import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestEncoder {

    public static Frame encode(Request request, int streamId) throws IOException {
        String method = request.getMethod();
        String path = request.getPath();

        byte[] methodBytes = method.getBytes(StandardCharsets.UTF_8);
        if (methodBytes.length > 255) {
            throw new IOException("Method too long");
        }

        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        if (pathBytes.length > Protocol.MAX_PATH_LENGTH) {
            throw new IOException("Path too long");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(Protocol.VERSION);
        baos.write(methodBytes.length);
        baos.write(methodBytes);

        baos.write((pathBytes.length >> 8) & 0xFF);
        baos.write(pathBytes.length & 0xFF);
        baos.write(pathBytes);

        byte[] encodedHeaders = HeaderCodec.encode(request.getHeaders());
        baos.write(encodedHeaders);

        byte[] payload = baos.toByteArray();
        FrameHeader header = new FrameHeader(
            payload.length,
            FrameType.REQUEST,
            Protocol.FLAG_END_STREAM,
            streamId
        );

        return new Frame(header, payload);
    }
}
