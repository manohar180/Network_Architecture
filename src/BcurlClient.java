import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BcurlClient {

    public static void main(String[] args) {
        boolean verbose = false;
        List<String> targets = new ArrayList<>();

        for (String arg : args) {
            if ("-v".equals(arg)) {
                verbose = true;
            } else {
                targets.add(arg);
            }
        }

        if (targets.isEmpty()) {
            System.err.println("Usage: java BcurlClient [-v] <host>:<port>/<path> [<path2> ...]");
            System.exit(1);
        }

        String firstUrl = targets.get(0);
        if (firstUrl.startsWith("bhttp://")) {
            firstUrl = firstUrl.substring(8);
        } else if (firstUrl.startsWith("http://")) {
            firstUrl = firstUrl.substring(7);
        }

        String hostPort;
        String firstPath;
        int slashIdx = firstUrl.indexOf('/');
        if (slashIdx >= 0) {
            hostPort = firstUrl.substring(0, slashIdx);
            firstPath = firstUrl.substring(slashIdx);
        } else {
            hostPort = firstUrl;
            firstPath = "/";
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

        List<String> paths = new ArrayList<>();
        paths.add(firstPath);
        for (int i = 1; i < targets.size(); i++) {
            String t = targets.get(i);
            if (t.startsWith("bhttp://")) {
                t = t.substring(8);
            } else if (t.startsWith("http://")) {
                t = t.substring(7);
            }
            if (t.length() >= 3 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':' && (t.charAt(2) == '/' || t.charAt(2) == '\\')) {
                int lastSlash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
                paths.add("/" + t.substring(lastSlash + 1));
            } else {
                int sIdx = t.indexOf('/');
                if (sIdx >= 0) {
                    paths.add(t.substring(sIdx));
                } else {
                    paths.add(t.startsWith("/") ? t : "/" + t);
                }
            }
        }

        boolean hasError = false;
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            for (String path : paths) {
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
                    hasError = true;
                }
            }

            if (hasError) {
                System.exit(1);
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            System.exit(1);
        }
    }
}
