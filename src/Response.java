import java.util.ArrayList;
import java.util.List;

public class Response {
    private final int statusCode;
    private final List<Header> headers;
    private final byte[] body;

    public Response(int statusCode, List<Header> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers != null ? headers : new ArrayList<>();
        this.body = body != null ? body : new byte[0];
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public String getHeaderValue(String name) {
        if (name == null) {
            return null;
        }
        for (Header h : headers) {
            if (h.getName().equalsIgnoreCase(name)) {
                return h.getValue();
            }
        }
        return null;
    }
}
