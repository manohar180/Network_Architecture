import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class ResponseDecoder {

    public static Response decode(InputStream in) throws IOException {
        return decode(in, null);
    }

    public static Response decode(InputStream in, Consumer<Frame> frameListener) throws IOException {
        Frame responseFrame = null;

        while (true) {
            FrameHeader header = FrameHeader.read(in);
            if (header == null) {
                throw new IOException("Connection closed before RESPONSE frame");
            }

            if (!FrameType.isKnown(header.getType())) {
                Frame.skipPayload(in, header.getPayloadLength());
                continue;
            }

            Frame frame = readPayload(in, header);
            if (frameListener != null) {
                frameListener.accept(frame);
            }

            if (frame.getHeader().getType() == FrameType.RESPONSE) {
                if (frame.getHeader().getStreamId() <= 0) {
                    throw new IOException("Invalid stream ID in RESPONSE frame: " + frame.getHeader().getStreamId());
                }
                responseFrame = frame;
                break;
            } else {
                throw new IOException("Unexpected known frame type before RESPONSE: " + frame.getHeader().getType());
            }
        }

        byte[] payload = responseFrame.getPayload();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);

        int version = bais.read();
        if (version != Protocol.VERSION) {
            throw new IOException("Unsupported protocol version: " + version);
        }

        int s1 = bais.read();
        int s2 = bais.read();
        if (s1 == -1 || s2 == -1) {
            throw new IOException("Missing status code in RESPONSE frame");
        }
        int statusCode = ((s1 & 0xFF) << 8) | (s2 & 0xFF);

        List<Header> headers = HeaderCodec.decode(bais);

        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        if (!responseFrame.getHeader().hasFlag(Protocol.FLAG_END_STREAM)) {
            while (true) {
                FrameHeader header = FrameHeader.read(in);
                if (header == null) {
                    throw new IOException("Connection closed before stream ended");
                }

                if (!FrameType.isKnown(header.getType())) {
                    Frame.skipPayload(in, header.getPayloadLength());
                    continue;
                }

                Frame frame = readPayload(in, header);
                if (frameListener != null) {
                    frameListener.accept(frame);
                }

                byte type = frame.getHeader().getType();
                if (type == FrameType.DATA) {
                    if (frame.getHeader().getStreamId() != responseFrame.getHeader().getStreamId()) {
                        throw new IOException("Stream ID mismatch in DATA frame");
                    }
                    bodyStream.write(frame.getPayload());
                    if (frame.getHeader().hasFlag(Protocol.FLAG_END_STREAM)) {
                        break;
                    }
                } else {
                    throw new IOException("Unexpected frame type during body transfer: " + type);
                }
            }
        }

        byte[] body = bodyStream.toByteArray();
        Response response = new Response(statusCode, headers, body);

        String clValue = response.getHeaderValue("content-length");
        if (clValue != null) {
            try {
                long expectedLength = Long.parseLong(clValue.trim());
                if (body.length != expectedLength) {
                    throw new IOException("Content-Length mismatch: expected " + expectedLength + " bytes, received " + body.length);
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Content-Length header value: " + clValue);
            }
        }

        return response;
    }

    private static Frame readPayload(InputStream in, FrameHeader header) throws IOException {
        byte[] payload = new byte[header.getPayloadLength()];
        int read = 0;
        while (read < header.getPayloadLength()) {
            int n = in.read(payload, read, header.getPayloadLength() - read);
            if (n == -1) {
                throw new IOException("Incomplete frame payload");
            }
            read += n;
        }
        return new Frame(header, payload);
    }
}
