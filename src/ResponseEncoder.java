import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResponseEncoder {
    private static final int MAX_DATA_CHUNK = 16384;

    public static List<Frame> encode(Response response, int streamId) throws IOException {
        List<Frame> frames = new ArrayList<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(Protocol.VERSION);
        int status = response.getStatusCode();
        baos.write((status >> 8) & 0xFF);
        baos.write(status & 0xFF);

        byte[] encodedHeaders = HeaderCodec.encode(response.getHeaders());
        baos.write(encodedHeaders);

        byte[] payload = baos.toByteArray();
        byte[] body = response.getBody();
        boolean hasBody = body != null && body.length > 0;

        byte responseFlags = hasBody ? 0x00 : Protocol.FLAG_END_STREAM;
        FrameHeader responseHeader = new FrameHeader(
            payload.length,
            FrameType.RESPONSE,
            responseFlags,
            streamId
        );
        frames.add(new Frame(responseHeader, payload));

        if (hasBody) {
            int offset = 0;
            while (offset < body.length) {
                int chunkSize = Math.min(MAX_DATA_CHUNK, body.length - offset);
                byte[] chunk = Arrays.copyOfRange(body, offset, offset + chunkSize);
                offset += chunkSize;

                boolean isLast = (offset >= body.length);
                byte dataFlags = isLast ? Protocol.FLAG_END_STREAM : 0x00;
                FrameHeader dataHeader = new FrameHeader(
                    chunk.length,
                    FrameType.DATA,
                    dataFlags,
                    streamId
                );
                frames.add(new Frame(dataHeader, chunk));
            }
        }

        return frames;
    }
}
