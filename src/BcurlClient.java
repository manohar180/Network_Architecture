import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BcurlClient {

    public static void main(String[] args) {
        boolean verbose = false;
        String target = null;

        for (String arg : args) {
            if ("-v".equals(arg)) {
                verbose = true;
            } else if (target == null) {
                target = arg;
            }
        }

        if (target == null) {
            System.err.println("Usage: java BcurlClient [-v] <host>:<port>/<path>");
            System.exit(1);
        }

        String url = target;
        if (url.startsWith("bhttp://")) {
            url = url.substring(8);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        }

        String hostPort;
        String path;
        int slashIdx = url.indexOf('/');
        if (slashIdx >= 0) {
            hostPort = url.substring(0, slashIdx);
            path = url.substring(slashIdx);
        } else {
            hostPort = url;
            path = "/";
        }

        String host = "localhost";
        int port = 9000;
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx >= 0) {
            host = hostPort.substring(0, colonIdx);
            try {
                port = Integer.parseInt(hostPort.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + hostPort.substring(colonIdx + 1));
                System.exit(1);
            }
        } else if (!hostPort.isEmpty()) {
            host = hostPort;
        }

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            List<Header> headers = new ArrayList<>();
            headers.add(new Header("host", host + ":" + port));
            headers.add(new Header("accept", "*/*"));

            Request request = new Request("GET", path, headers);
            Frame reqFrame = RequestEncoder.encode(request, Protocol.DEFAULT_STREAM_ID);

            if (verbose) {
                System.err.println("> Sending REQUEST frame (stream " + reqFrame.getHeader().getStreamId() + "):");
                System.err.print(HexDump.dump(HexDump.frameToBytes(reqFrame)));
            }

            reqFrame.write(out);
            out.flush();

            final boolean isVerbose = verbose;
            Response response = ResponseDecoder.decode(in, frame -> {
                if (isVerbose) {
                    String typeName;
                    switch (frame.getHeader().getType()) {
                        case FrameType.RESPONSE:
                            typeName = "RESPONSE";
                            break;
                        case FrameType.DATA:
                            typeName = "DATA";
                            break;
                        default:
                            typeName = "UNKNOWN (0x" + String.format("%02x", frame.getHeader().getType()) + ")";
                            break;
                    }
                    System.err.println("< Received " + typeName + " frame (stream " + frame.getHeader().getStreamId() + "):");
                    System.err.print(HexDump.dump(HexDump.frameToBytes(frame)));
                }
            });

            if (verbose) {
                System.err.println("< Status: " + response.getStatusCode());
                for (Header h : response.getHeaders()) {
                    System.err.println("< " + h.getName() + ": " + h.getValue());
                }
                System.err.println();
            }

            System.out.write(response.getBody());
            System.out.flush();

            if (response.getStatusCode() >= 400) {
                System.exit(1);
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            System.exit(1);
        }
    }
}
