public class FrameType {
    public static final byte REQUEST = 0x01;
    public static final byte RESPONSE = 0x02;
    public static final byte DATA = 0x03;

    public static boolean isKnown(byte type) {
        return type == REQUEST || type == RESPONSE || type == DATA;
    }
}
