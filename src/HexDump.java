public class HexDump {

    public static String dump(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int length = data.length;

        for (int i = 0; i < length; i += 16) {
            sb.append(String.format("%04x  ", i));

            for (int j = 0; j < 16; j++) {
                if (i + j < length) {
                    sb.append(String.format("%02x ", data[i + j]));
                } else {
                    sb.append("   ");
                }
                if (j == 7) {
                    sb.append(" ");
                }
            }

            sb.append(" |");
            for (int j = 0; j < 16 && (i + j) < length; j++) {
                byte b = data[i + j];
                if (b >= 32 && b <= 126) {
                    sb.append((char) b);
                } else {
                    sb.append('.');
                }
            }
            sb.append("|\n");
        }

        return sb.toString();
    }

    public static byte[] frameToBytes(Frame frame) {
        FrameHeader h = frame.getHeader();
        byte[] payload = frame.getPayload();
        byte[] bytes = new byte[9 + payload.length];

        bytes[0] = (byte) ((h.getPayloadLength() >> 16) & 0xFF);
        bytes[1] = (byte) ((h.getPayloadLength() >> 8) & 0xFF);
        bytes[2] = (byte) (h.getPayloadLength() & 0xFF);
        bytes[3] = h.getType();
        bytes[4] = h.getFlags();
        bytes[5] = (byte) ((h.getStreamId() >> 24) & 0x7F);
        bytes[6] = (byte) ((h.getStreamId() >> 16) & 0xFF);
        bytes[7] = (byte) ((h.getStreamId() >> 8) & 0xFF);
        bytes[8] = (byte) (h.getStreamId() & 0xFF);

        System.arraycopy(payload, 0, bytes, 9, payload.length);
        return bytes;
    }
}
