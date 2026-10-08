import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class FrameHeader {
    private final int payloadLength;
    private final byte type;
    private final byte flags;
    private final int streamId;

    public FrameHeader(int payloadLength, byte type, byte flags, int streamId) {
        if (payloadLength < 0 || payloadLength > Protocol.MAX_FRAME_PAYLOAD) {
            throw new IllegalArgumentException("Payload length out of range: " + payloadLength);
        }
        if (streamId < 0 || (streamId & 0x80000000) != 0) {
            throw new IllegalArgumentException("Invalid stream ID: " + streamId);
        }
        this.payloadLength = payloadLength;
        this.type = type;
        this.flags = flags;
        this.streamId = streamId;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public byte getType() {
        return type;
    }

    public byte getFlags() {
        return flags;
    }

    public int getStreamId() {
        return streamId;
    }

    public boolean hasFlag(byte flag) {
        return (flags & flag) != 0;
    }

    public void write(OutputStream out) throws IOException {
        byte[] headerBytes = new byte[9];
        headerBytes[0] = (byte) ((payloadLength >> 16) & 0xFF);
        headerBytes[1] = (byte) ((payloadLength >> 8) & 0xFF);
        headerBytes[2] = (byte) (payloadLength & 0xFF);
        headerBytes[3] = type;
        headerBytes[4] = flags;
        headerBytes[5] = (byte) ((streamId >> 24) & 0x7F);
        headerBytes[6] = (byte) ((streamId >> 16) & 0xFF);
        headerBytes[7] = (byte) ((streamId >> 8) & 0xFF);
        headerBytes[8] = (byte) (streamId & 0xFF);
        out.write(headerBytes);
    }

    public static FrameHeader read(InputStream in) throws IOException {
        byte[] headerBytes = new byte[9];
        int read = 0;
        while (read < 9) {
            int n = in.read(headerBytes, read, 9 - read);
            if (n == -1) {
                if (read == 0) {
                    return null;
                }
                throw new IOException("Incomplete frame header");
            }
            read += n;
        }

        int payloadLength = ((headerBytes[0] & 0xFF) << 16) | ((headerBytes[1] & 0xFF) << 8) | (headerBytes[2] & 0xFF);
        byte type = headerBytes[3];
        byte flags = headerBytes[4];

        long rawStreamId = ((headerBytes[5] & 0xFFL) << 24) |
                           ((headerBytes[6] & 0xFFL) << 16) |
                           ((headerBytes[7] & 0xFFL) << 8) |
                           (headerBytes[8] & 0xFFL);

        if ((rawStreamId & 0x80000000L) != 0) {
            throw new IOException("Reserved stream ID bit must be 0");
        }

        int streamId = (int) rawStreamId;

        if (payloadLength > Protocol.MAX_FRAME_PAYLOAD) {
            throw new IOException("Payload length exceeds maximum allowed payload");
        }

        return new FrameHeader(payloadLength, type, flags, streamId);
    }
}
