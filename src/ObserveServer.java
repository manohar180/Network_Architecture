import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class ObserveServer {
    private final File rootDir;
    private final int port;
    private final FileServer fileServer;

    public ObserveServer(File rootDir, int port) {
        this.rootDir = rootDir;
        this.port = port;
        this.fileServer = new FileServer(rootDir);
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("ObserveServer listening on port " + port + " (root: " + rootDir.getPath() + ")");

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleConnection(clientSocket)).start();
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    break;
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            while (!socket.isClosed()) {
                Frame frame;
                try {
                    frame = Frame.read(in);
                } catch (IOException e) {
                    try {
                        Response err = FileServer.errorResponse(400, "400 Bad Request: Malformed Frame\n");
                        List<Frame> errFrames = ResponseEncoder.encode(err, Protocol.DEFAULT_STREAM_ID);
                        for (Frame f : errFrames) {
                            f.write(out);
                        }
                        out.flush();
                    } catch (IOException ignored) {
                    }
                    break;
                }

                if (frame == null) {
                    break;
                }

                byte type = frame.getHeader().getType();
                if (!FrameType.isKnown(type)) {
                    continue;
                }

                if (type != FrameType.REQUEST) {
                    Response err = FileServer.errorResponse(400, "400 Bad Request: Expected REQUEST Frame\n");
                    List<Frame> errFrames = ResponseEncoder.encode(err, frame.getHeader().getStreamId());
                    for (Frame f : errFrames) {
                        f.write(out);
                    }
                    out.flush();
                    continue;
                }

                Request request;
                try {
                    request = RequestDecoder.decode(frame);
                } catch (IOException e) {
                    Response err = FileServer.errorResponse(400, "400 Bad Request: " + e.getMessage() + "\n");
                    List<Frame> errFrames = ResponseEncoder.encode(err, frame.getHeader().getStreamId());
                    for (Frame f : errFrames) {
                        f.write(out);
                    }
                    out.flush();
                    continue;
                }

                Response response;
                try {
                    response = fileServer.handleRequest(request);
                } catch (Exception e) {
                    response = FileServer.errorResponse(500, "500 Internal Server Error\n");
                }

                List<Frame> respFrames = ResponseEncoder.encode(response, frame.getHeader().getStreamId());
                for (Frame f : respFrames) {
                    f.write(out);
                }
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ObserveServer <document-root> <port>");
            System.exit(1);
        }

        File rootDir = new File(args[0]);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("Document root does not exist or is not a directory: " + args[0]);
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[1]);
            System.exit(1);
            return;
        }

        try {
            ObserveServer server = new ObserveServer(rootDir, port);
            server.start();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            System.exit(1);
        }
    }
}
