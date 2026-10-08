import java.util.ArrayList;
import java.util.List;

public class Request {
    private final String method;
    private final String path;
    private final List<Header> headers;

    public Request(String method, String path, List<Header> headers) {
        this.method = method != null ? method : "";
        this.path = path != null ? path : "";
        this.headers = headers != null ? headers : new ArrayList<>();
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public List<Header> getHeaders() {
        return headers;
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
