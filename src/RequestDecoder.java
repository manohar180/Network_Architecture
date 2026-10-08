import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RequestDecoder {

    public static Request decode(Frame frame) throws IOException {
        if (frame.getHeader().getType() != FrameType.REQUEST) {
            throw new IOException("Expected REQUEST frame type");
        }
        if (!frame.getHeader().hasFlag(Protocol.FLAG_END_STREAM)) {
            throw new IOException("REQUEST frame must have END_STREAM flag set");
        }

        byte[] payload = frame.getPayload();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);

        int version = bais.read();
        if (version != Protocol.VERSION) {
            throw new IOException("Unsupported protocol version: " + version);
        }

        int methodLen = bais.read();
        if (methodLen == -1) {
            throw new IOException("Missing method length");
        }
        byte[] methodBytes = readExact(bais, methodLen);
        String method = new String(methodBytes, StandardCharsets.UTF_8);

        if (!"GET".equalsIgnoreCase(method)) {
            throw new IOException("Unsupported HTTP method: " + method);
        }

        int p1 = bais.read();
        int p2 = bais.read();
        if (p1 == -1 || p2 == -1) {
            throw new IOException("Missing path length");
        }
        int pathLen = ((p1 & 0xFF) << 8) | (p2 & 0xFF);
        if (pathLen > Protocol.MAX_PATH_LENGTH) {
            throw new IOException("Path length exceeds maximum limit");
        }

        byte[] pathBytes = readExact(bais, pathLen);
        String path = new String(pathBytes, StandardCharsets.UTF_8);

        if (!path.startsWith("/")) {
            throw new IOException("Path must begin with '/'");
        }

        List<Header> headers = HeaderCodec.decode(bais);

        return new Request(method, path, headers);
    }

    private static byte[] readExact(ByteArrayInputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int read = in.read(buf);
        if (read != length) {
            throw new IOException("Unexpected EOF reading request field");
        }
        return buf;
    }
}
