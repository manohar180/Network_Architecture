import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileServer {
    private final File rootDir;

    public FileServer(File rootDir) {
        this.rootDir = rootDir;
    }

    public Response handleRequest(Request request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return errorResponse(400, "400 Bad Request: Unsupported Method\n");
        }

        File target = PathResolver.resolve(rootDir, request.getPath());
        if (target == null) {
            return errorResponse(400, "400 Bad Request: Invalid Path\n");
        }

        if (target.isDirectory()) {
            target = new File(target, "index.html");
        }

        if (!target.exists() || !target.isFile() || !target.canRead()) {
            return errorResponse(404, "404 Not Found\n");
        }

        try {
            byte[] body = Files.readAllBytes(target.toPath());
            String contentType = getMimeType(target.getName());

            List<Header> headers = new ArrayList<>();
            headers.add(new Header("status", "200 OK"));
            headers.add(new Header("content-type", contentType));
            headers.add(new Header("content-length", String.valueOf(body.length)));
            headers.add(new Header("connection", "keep-alive"));
            headers.add(new Header("server", "ObserveServer/1.0"));

            return new Response(200, headers, body);
        } catch (IOException e) {
            return errorResponse(500, "500 Internal Server Error\n");
        }
    }

    public static Response errorResponse(int statusCode, String message) {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String statusText;
        switch (statusCode) {
            case 400:
                statusText = "400 Bad Request";
                break;
            case 404:
                statusText = "404 Not Found";
                break;
            case 500:
            default:
                statusText = "500 Internal Server Error";
                break;
        }

        List<Header> headers = new ArrayList<>();
        headers.add(new Header("status", statusText));
        headers.add(new Header("content-type", "text/plain"));
        headers.add(new Header("content-length", String.valueOf(body.length)));
        headers.add(new Header("connection", "keep-alive"));
        headers.add(new Header("server", "ObserveServer/1.0"));

        return new Response(statusCode, headers, body);
    }

    public static String getMimeType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        } else if (lower.endsWith(".txt")) {
            return "text/plain";
        } else if (lower.endsWith(".css")) {
            return "text/css";
        } else if (lower.endsWith(".js")) {
            return "application/javascript";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }
}
