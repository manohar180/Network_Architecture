public class Protocol {
    public static final byte VERSION = 0x01;
    public static final byte FLAG_END_STREAM = 0x01;
    public static final int HEADER_SIZE = 9;
    public static final int MAX_FRAME_PAYLOAD = 16777215;
    public static final int MAX_PATH_LENGTH = 4096;
    public static final int MAX_HEADER_COUNT = 255;
    public static final int MAX_HEADER_FIELD_LENGTH = 65535;
    public static final int DEFAULT_STREAM_ID = 1;
}
