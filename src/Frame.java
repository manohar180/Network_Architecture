import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class Frame {
    private final FrameHeader header;
    private final byte[] payload;

    public Frame(FrameHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload != null ? payload : new byte[0];
    }

    public FrameHeader getHeader() {
        return header;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void write(OutputStream out) throws IOException {
        header.write(out);
        if (payload.length > 0) {
            out.write(payload);
        }
    }

    public static Frame read(InputStream in) throws IOException {
        FrameHeader header = FrameHeader.read(in);
        if (header == null) {
            return null;
        }

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

    public static void skipPayload(InputStream in, int length) throws IOException {
        int remaining = length;
        byte[] buffer = new byte[Math.min(remaining, 8192)];
        while (remaining > 0) {
            int toRead = Math.min(remaining, buffer.length);
            int n = in.read(buffer, 0, toRead);
            if (n == -1) {
                throw new IOException("Unexpected EOF while skipping frame payload");
            }
            remaining -= n;
        }
    }
}
