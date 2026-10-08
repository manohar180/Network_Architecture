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
            Frame frame = Frame.read(in);
            if (frame == null) {
                throw new IOException("Connection closed before RESPONSE frame");
            }

            if (frameListener != null) {
                frameListener.accept(frame);
            }

            if (frame.getHeader().getType() == FrameType.RESPONSE) {
                responseFrame = frame;
                break;
            } else if (!FrameType.isKnown(frame.getHeader().getType())) {
                continue;
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
                Frame frame = Frame.read(in);
                if (frame == null) {
                    throw new IOException("Connection closed before stream ended");
                }

                if (frameListener != null) {
                    frameListener.accept(frame);
                }

                byte type = frame.getHeader().getType();
                if (!FrameType.isKnown(type)) {
                    continue;
                }

                if (type == FrameType.DATA) {
                    bodyStream.write(frame.getPayload());
                    if (frame.getHeader().hasFlag(Protocol.FLAG_END_STREAM)) {
                        break;
                    }
                } else {
                    throw new IOException("Unexpected frame type during body transfer: " + type);
                }
            }
        }

        return new Response(statusCode, headers, bodyStream.toByteArray());
    }
}
