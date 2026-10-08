# Network Architecture Course Project: BHTTP/1 Protocol

## Overview
This project implements **BHTTP/1** (Binary HTTP Version 1), a binary application protocol running directly over TCP using raw Java sockets (`ServerSocket` and `Socket`). It includes both a multi-client concurrent server (`ObserveServer`) and a command-line client (`BcurlClient`).

Communication occurs using custom binary frames over a single persistent TCP connection. No HTTP libraries, frameworks, or external dependencies are used.

---

## Architecture Diagram & Flow

```text
[ BcurlClient ]                                       [ ObserveServer ]
       |                                                      |
       |  ========= 1. TCP Connection (Port 9000) =========   |
       |----------------------------------------------------->| (Worker Thread)
       |                                                      |
       |  -- 2. REQUEST Frame (Stream 1, END_STREAM=1) -----> |
       |     [9-byte Header | Version, Method, Path, Headers] |
       |                                                      | Path Validation &
       |                                                      | FileServer Lookup
       |  <-- 3. RESPONSE Frame (Stream 1, END_STREAM=0) ---- |
       |     [9-byte Header | Version, Status, Headers]       |
       |                                                      |
       |  <-- 4. DATA Frame Chunk 1 (16 KB, END_STREAM=0) --- |
       |  <-- 5. DATA Frame Chunk 2 (Final, END_STREAM=1) --- |
       |                                                      |
       |  ==== 6. Connection Kept Alive for Next Request ==== |
       |  -- 7. Sequential REQUEST on Same Socket ----------> |
       v                                                      v
```

---

## Architecture & Design
- **Transport:** Raw TCP sockets (`java.net.ServerSocket` and `java.net.Socket`).
- **Framing:** Fixed 9-byte header (`24-bit length | 8-bit type | 8-bit flags | 31-bit stream ID`).
- **Header Compression:** HPACK-lite hybrid encoding (10 indexed names and literal name fallback).
- **Concurrency:** Thread-per-connection concurrency model on the server.
- **Connection Management:** Persistent TCP connection; the server keeps the socket open and serves sequential requests without reconnecting.
- **Path Security:** Normalizes paths and ensures requests remain strictly inside the document root (`./www`), returning `400 Bad Request` on path traversal attempts.
- **DATA Chunking:** Large bodies exceeding `16,384` bytes are chunked into consecutive `DATA` frames. The final frame asserts `END_STREAM = 0x01`.

---

## Protocol Summary
- **Frame Types:**
  - `0x01` (`REQUEST`): Contains method ("GET"), path, and request headers. Flags include `END_STREAM`.
  - `0x02` (`RESPONSE`): Contains status code and response headers.
  - `0x03` (`DATA`): Contains raw payload body bytes. The last chunk has `END_STREAM` set.
- **Unknown Frame Tolerance:** Any receiver encountering an unknown frame type skips exactly the payload length without crashing or dropping the connection, ensuring forward compatibility.

---

## Folder Structure
```text
NetworkArchitectureProject/
|-- README.md
|-- SPEC.md
|-- TESTING.md
|-- .gitignore
|-- observe
|-- bcurl
|-- src/
|   |-- ObserveServer.java
|   |-- BcurlClient.java
|   |-- Protocol.java
|   |-- Frame.java
|   |-- FrameHeader.java
|   |-- FrameType.java
|   |-- Header.java
|   |-- HeaderTable.java
|   |-- HeaderCodec.java
|   |-- Request.java
|   |-- Response.java
|   |-- RequestEncoder.java
|   |-- RequestDecoder.java
|   |-- ResponseEncoder.java
|   |-- ResponseDecoder.java
|   |-- FileServer.java
|   |-- PathResolver.java
|   |-- HexDump.java
|   |-- TestRunner.java
|-- www/
|   |-- index.html
|   |-- hello.txt
|   |-- test.html
|   |-- large.txt
|-- examples/
|   |-- annotated-hexdump.md
|-- scripts/
    |-- observe
    |-- bcurl
```

---

## Compilation

To compile all Java source files:
```bash
javac -d out src/*.java
```

---

## Running the Server

Using raw Java:
```bash
java -cp out ObserveServer ./www 9000
```

Or using the executable wrapper script:
```bash
./observe ./www 9000
```

---

## Running the Client

Using raw Java:
```bash
java -cp out BcurlClient -v localhost:9000/index.html
```

Or using the executable wrapper script:
```bash
./bcurl -v localhost:9000/index.html
```

---

## Example Usage

### 1. Successful Request (200 OK)
```bash
./bcurl localhost:9000/hello.txt
```
**Output:**
```text
Hello, BHTTP/1 binary world!
This is a plain text file served over our custom TCP binary application protocol.
```
Exit code: `0`.

### 2. Missing Resource (404 Not Found)
```bash
./bcurl localhost:9000/missing.txt
```
**Output:**
```text
404 Not Found
```
Exit code: `1`.

### 3. Persistent Connection Demo (Multiple Sequential Requests)
`BcurlClient` can send multiple sequential requests across the same socket without reconnecting:
```bash
./bcurl localhost:9000/hello.txt /test.html
```

### 4. Large Response (DATA Frame Chunking)
```bash
./bcurl -v localhost:9000/large.txt
```
Demonstrates chunked `DATA` frame delivery where the final chunk sets `END_STREAM`.

### 5. Verbose Frame Hexdump Mode
```bash
./bcurl -v localhost:9000/index.html
```
Displays full hexadecimal frame inspection of the `REQUEST`, `RESPONSE`, and `DATA` frames.

---

## Security & Path Traversal Handling
All incoming paths are normalized and resolved to canonical paths relative to the document root (`./www`). Requests attempting to escape via `..` or null bytes (`\0`) are intercepted by `PathResolver` and rejected with `400 Bad Request`.

---

## Automated Test Suite
Run the built-in, standalone test suite:
```bash
java -cp out TestRunner
```
Covers all 12 core test scenarios: normal 200, 404, truncated frames, reserved bit checks, invalid UTF-8, path traversal, Content-Length checks, unknown frames, multi-chunk DATA, persistent sequential connections, and simultaneous concurrent clients.

---

## Design Decisions & Limitations
- **Synchronous Streams:** Stream ID 1 is used for synchronous request/response pairs on a connection. Multiplexing across concurrent stream IDs is reserved for future revisions.
- **Strict Parsing:** Protocol fields (method, path, headers) enforce strict UTF-8 decoding (`CodingErrorAction.REPORT`) to prevent character substitution vulnerabilities.
- **Direct Unknown Frame Skipping:** Unknown frame types have their headers parsed and payloads consumed directly from the socket stream without heap allocation.
- **Clean Standard Library:** Built exclusively on plain Java SE standard library sockets and streams without third-party frameworks.

---

## Annotated Hexdump Location
A byte-level annotated hexdump of live captured network frames is available in:
[`examples/annotated-hexdump.md`](examples/annotated-hexdump.md)
