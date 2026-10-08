import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HeaderCodec {

    public static byte[] encode(List<Header> headers) throws IOException {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        if (headers.size() > Protocol.MAX_HEADER_COUNT) {
            throw new IOException("Too many headers: " + headers.size());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(headers.size());

        for (Header h : headers) {
            String name = h.getName();
            String value = h.getValue();
            byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);

            if (valBytes.length > Protocol.MAX_HEADER_FIELD_LENGTH) {
                throw new IOException("Header value too long");
            }

            int index = HeaderTable.getIndex(name);
            if (index != -1) {
                baos.write(index);
                writeUInt16(baos, valBytes.length);
                baos.write(valBytes);
            } else {
                byte[] nameBytes = name.toLowerCase().getBytes(StandardCharsets.UTF_8);
                if (nameBytes.length > Protocol.MAX_HEADER_FIELD_LENGTH) {
                    throw new IOException("Header name too long");
                }
                baos.write(0);
                writeUInt16(baos, nameBytes.length);
                baos.write(nameBytes);
                writeUInt16(baos, valBytes.length);
                baos.write(valBytes);
            }
        }

        return baos.toByteArray();
    }

    public static List<Header> decode(InputStream in) throws IOException {
        int count = in.read();
        if (count == -1) {
            throw new IOException("Unexpected EOF reading header count");
        }
        int headerCount = count & 0xFF;
        List<Header> headers = new ArrayList<>();

        for (int i = 0; i < headerCount; i++) {
            int marker = in.read();
            if (marker == -1) {
                throw new IOException("Unexpected EOF reading header entry");
            }

            if (marker >= 1 && marker <= 10) {
                String name = HeaderTable.getName(marker);
                int valLen = readUInt16(in);
                byte[] valBytes = readExact(in, valLen);
                headers.add(new Header(name, decodeUtf8(valBytes)));
            } else if (marker == 0) {
                int nameLen = readUInt16(in);
                byte[] nameBytes = readExact(in, nameLen);
                int valLen = readUInt16(in);
                byte[] valBytes = readExact(in, valLen);
                String name = decodeUtf8(nameBytes);
                String val = decodeUtf8(valBytes);
                headers.add(new Header(name, val));
            } else {
                throw new IOException("Invalid header marker byte: " + marker);
            }
        }

        return headers;
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Malformed UTF-8 encoding in header: " + e.getMessage(), e);
        }
    }

    private static void writeUInt16(OutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readUInt16(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if (b1 == -1 || b2 == -1) {
            throw new IOException("Unexpected EOF reading 16-bit integer");
        }
        return ((b1 & 0xFF) << 8) | (b2 & 0xFF);
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        if (length > Protocol.MAX_HEADER_FIELD_LENGTH) {
            throw new IOException("Header field length exceeds limit: " + length);
        }
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(data, read, length - read);
            if (n == -1) {
                throw new IOException("Unexpected EOF reading header string bytes");
            }
            read += n;
        }
        return data;
    }
}
