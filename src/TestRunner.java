import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestRunner {
    private static int port = 9000;

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        boolean startedInternalServer = false;
        ServerSocket testServerSocket = null;
        if (!isServerListening("localhost", port)) {
            try {
                testServerSocket = new ServerSocket(0);
                port = testServerSocket.getLocalPort();
                testServerSocket.close();
                ObserveServer server = new ObserveServer(new File("./www"), port);
                Thread serverThread = new Thread(() -> {
                    try {
                        server.start();
                    } catch (IOException ignored) {
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();
                startedInternalServer = true;
                Thread.sleep(300);
            } catch (Exception e) {
                System.err.println("Could not start test server: " + e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("Running BHTTP/1 Test Suite on port " + port);
        int passed = 0;
        int failed = 0;

        try {
            testNormal200();
            System.out.println("  [PASS] 1. Normal 200 OK Response");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 1. Normal 200 OK Response: " + t.getMessage());
            failed++;
        }

        try {
            testNotFound404();
            System.out.println("  [PASS] 2. 404 Not Found Response");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 2. 404 Not Found Response: " + t.getMessage());
            failed++;
        }

        try {
            testMalformedTruncatedFrame();
            System.out.println("  [PASS] 3. Malformed/Truncated Frame Handling");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 3. Malformed/Truncated Frame Handling: " + t.getMessage());
            failed++;
        }

        try {
            testInvalidReservedStreamIdBit();
            System.out.println("  [PASS] 4. Reserved Stream-ID Bit Validation");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 4. Reserved Stream-ID Bit Validation: " + t.getMessage());
            failed++;
        }

        try {
            testInvalidUtf8();
            System.out.println("  [PASS] 5. Invalid UTF-8 Rejection");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 5. Invalid UTF-8 Rejection: " + t.getMessage());
            failed++;
        }

        try {
            testInvalidRequestPath();
            System.out.println("  [PASS] 6. Invalid Request Path Format");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 6. Invalid Request Path Format: " + t.getMessage());
            failed++;
        }

        try {
            testPathTraversal();
            System.out.println("  [PASS] 7. Path Traversal Prevention");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 7. Path Traversal Prevention: " + t.getMessage());
            failed++;
        }

        try {
            testContentLengthMismatch();
            System.out.println("  [PASS] 8. Content-Length Mismatch Validation");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 8. Content-Length Mismatch Validation: " + t.getMessage());
            failed++;
        }

        try {
            testUnknownFrameFollowedByValidFrame();
            System.out.println("  [PASS] 9. Unknown Frame Followed by Valid Frame");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 9. Unknown Frame Followed by Valid Frame: " + t.getMessage());
            failed++;
        }

        try {
            testLargeResponseMultipleDataFrames();
            System.out.println("  [PASS] 10. Large Response Multiple DATA Frames");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 10. Large Response Multiple DATA Frames: " + t.getMessage());
            failed++;
        }

        try {
            testPersistentConnectionSequential();
            System.out.println("  [PASS] 11. Persistent Connection Sequential Requests");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 11. Persistent Connection Sequential Requests: " + t.getMessage());
            failed++;
        }

        try {
            testMultipleSimultaneousClients();
            System.out.println("  [PASS] 12. Multiple Simultaneous Clients");
            passed++;
        } catch (Throwable t) {
            System.out.println("  [FAIL] 12. Multiple Simultaneous Clients: " + t.getMessage());
            failed++;
        }

        System.out.println("Test Results: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean isServerListening(String host, int p) {
        try (Socket s = new Socket(host, p)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void testNormal200() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            Request req = new Request("GET", "/hello.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 200) {
                throw new RuntimeException("Expected 200, got: " + resp.getStatusCode());
            }
            String body = new String(resp.getBody(), StandardCharsets.UTF_8);
            if (!body.contains("Hello, BHTTP/1")) {
                throw new RuntimeException("Body content mismatch");
            }
        }
    }

    private static void testNotFound404() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            Request req = new Request("GET", "/nonexistent_file_404.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 404) {
                throw new RuntimeException("Expected 404, got: " + resp.getStatusCode());
            }
        }
    }

    private static void testMalformedTruncatedFrame() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] truncatedHeader = new byte[]{0x00, 0x00, 0x0A, 0x01};
            out.write(truncatedHeader);
            out.flush();
        }

        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            Request req = new Request("GET", "/hello.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 200) {
                throw new RuntimeException("Server failed to recover from truncated frame on next connection");
            }
        }
    }

    private static void testInvalidReservedStreamIdBit() {
        try {
            new FrameHeader(10, FrameType.REQUEST, (byte) 0, 0x80000001);
            throw new RuntimeException("Constructor accepted reserved stream ID MSB");
        } catch (IllegalArgumentException expected) {
        }

        byte[] rawBytes = new byte[]{
            0x00, 0x00, 0x00,
            0x01,
            0x00,
            (byte) 0x80, 0x00, 0x00, 0x01
        };
        try {
            FrameHeader.read(new ByteArrayInputStream(rawBytes));
            throw new RuntimeException("FrameHeader.read accepted reserved stream ID MSB");
        } catch (IOException expected) {
        }
    }

    private static void testInvalidUtf8() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] method = "GET".getBytes(StandardCharsets.UTF_8);
            byte[] badPath = new byte[]{'/', (byte) 0xC0, (byte) 0xAF};
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(Protocol.VERSION);
            baos.write(method.length);
            baos.write(method);
            baos.write((badPath.length >> 8) & 0xFF);
            baos.write(badPath.length & 0xFF);
            baos.write(badPath);
            baos.write(0);

            byte[] payload = baos.toByteArray();
            FrameHeader header = new FrameHeader(payload.length, FrameType.REQUEST, Protocol.FLAG_END_STREAM, 1);
            Frame badFrame = new Frame(header, payload);
            badFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 400) {
                throw new RuntimeException("Expected 400 for invalid UTF-8, got: " + resp.getStatusCode());
            }
        }
    }

    private static void testInvalidRequestPath() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] method = "GET".getBytes(StandardCharsets.UTF_8);
            byte[] noSlashPath = "hello.txt".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(Protocol.VERSION);
            baos.write(method.length);
            baos.write(method);
            baos.write((noSlashPath.length >> 8) & 0xFF);
            baos.write(noSlashPath.length & 0xFF);
            baos.write(noSlashPath);
            baos.write(0);

            byte[] payload = baos.toByteArray();
            FrameHeader header = new FrameHeader(payload.length, FrameType.REQUEST, Protocol.FLAG_END_STREAM, 1);
            Frame badFrame = new Frame(header, payload);
            badFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 400) {
                throw new RuntimeException("Expected 400 for path without slash, got: " + resp.getStatusCode());
            }
        }
    }

    private static void testPathTraversal() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            Request req = new Request("GET", "/../secret.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 400 && resp.getStatusCode() != 404) {
                throw new RuntimeException("Path traversal not blocked, got: " + resp.getStatusCode());
            }
        }
    }

    private static void testContentLengthMismatch() throws Exception {
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("content-length", "500"));
        Response resp = new Response(200, headers, new byte[200]);
        List<Frame> frames = ResponseEncoder.encode(resp, 1);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Frame f : frames) {
            f.write(baos);
        }

        try {
            ResponseDecoder.decode(new ByteArrayInputStream(baos.toByteArray()));
            throw new RuntimeException("Decoder failed to detect Content-Length mismatch");
        } catch (IOException expected) {
        }
    }

    private static void testUnknownFrameFollowedByValidFrame() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] unknownPayload = "FutureProtocolExtensionFrameBytes".getBytes(StandardCharsets.UTF_8);
            FrameHeader unknownHeader = new FrameHeader(unknownPayload.length, (byte) 0x99, (byte) 0x00, 1);
            Frame unknownFrame = new Frame(unknownHeader, unknownPayload);
            unknownFrame.write(out);

            Request req = new Request("GET", "/hello.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            Response resp = ResponseDecoder.decode(in);
            if (resp.getStatusCode() != 200) {
                throw new RuntimeException("Expected 200 after unknown frame, got: " + resp.getStatusCode());
            }
        }
    }

    private static void testLargeResponseMultipleDataFrames() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            Request req = new Request("GET", "/large.txt", null);
            Frame reqFrame = RequestEncoder.encode(req, 1);
            reqFrame.write(out);
            out.flush();

            AtomicInteger dataFrameCount = new AtomicInteger(0);
            Response resp = ResponseDecoder.decode(in, frame -> {
                if (frame.getHeader().getType() == FrameType.DATA) {
                    dataFrameCount.incrementAndGet();
                }
            });

            if (resp.getStatusCode() != 200) {
                throw new RuntimeException("Expected 200 for large.txt, got: " + resp.getStatusCode());
            }
            if (dataFrameCount.get() < 2) {
                throw new RuntimeException("Expected at least 2 DATA frames for large.txt, got: " + dataFrameCount.get());
            }
            if (resp.getBody().length != new File("./www/large.txt").length()) {
                throw new RuntimeException("Large body length mismatch");
            }
        }
    }

    private static void testPersistentConnectionSequential() throws Exception {
        try (Socket socket = new Socket("localhost", port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            String[] testPaths = new String[]{"/index.html", "/hello.txt", "/test.html"};
            for (String path : testPaths) {
                Request req = new Request("GET", path, null);
                Frame reqFrame = RequestEncoder.encode(req, 1);
                reqFrame.write(out);
                out.flush();

                Response resp = ResponseDecoder.decode(in);
                if (resp.getStatusCode() != 200) {
                    throw new RuntimeException("Sequential request for " + path + " failed: " + resp.getStatusCode());
                }
            }
        }
    }

    private static void testMultipleSimultaneousClients() throws Exception {
        int clientCount = 6;
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            new Thread(() -> {
                try (Socket socket = new Socket("localhost", port);
                     OutputStream out = socket.getOutputStream();
                     InputStream in = socket.getInputStream()) {

                    String path = (id % 2 == 0) ? "/hello.txt" : "/index.html";
                    Request req = new Request("GET", path, null);
                    Frame reqFrame = RequestEncoder.encode(req, 1);
                    reqFrame.write(out);
                    out.flush();

                    Response resp = ResponseDecoder.decode(in);
                    if (resp.getStatusCode() != 200) {
                        hasError.set(true);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        if (hasError.get()) {
            throw new RuntimeException("Concurrent client test failed");
        }
    }
}
