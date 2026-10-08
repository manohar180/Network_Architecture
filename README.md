# Network Architecture Course Project: BHTTP/1 Protocol

## Overview
This project implements **BHTTP/1** (Binary HTTP Version 1), a binary application protocol running directly over TCP using raw Java sockets (`ServerSocket` and `Socket`). It includes both a multi-client concurrent server (`ObserveServer`) and a command-line client (`BcurlClient`).

Communication occurs using custom binary frames over a single persistent TCP connection. No HTTP libraries, frameworks, or external dependencies are used.

---

## Architecture & Design
- **Transport:** Raw TCP sockets (`java.net.ServerSocket` and `java.net.Socket`).
- **Framing:** Fixed 9-byte header (`24-bit length | 8-bit type | 8-bit flags | 31-bit stream ID`).
- **Header Compression:** HPACK-lite hybrid encoding (10 indexed names and literal name fallback).
- **Concurrency:** Thread-per-connection concurrency model on the server.
- **Connection Management:** Persistent TCP connection; the server keeps the socket open and serves sequential requests without reconnecting.
- **Path Security:** Normalizes paths and ensures requests remain strictly inside the document root (`./www`), returning `400 Bad Request` on path traversal attempts.

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
|-- www/
|   |-- index.html
|   |-- hello.txt
|   |-- test.html
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

### 3. Verbose Frame Hexdump Mode
```bash
./bcurl -v localhost:9000/index.html
```
Displays full hexadecimal frame inspection of the `REQUEST`, `RESPONSE`, and `DATA` frames.

---

## Verification & Testing
The implementation was verified against the following tests:
1. Java compilation with standard `javac`.
2. Clean request/response serving of HTML and plain text files.
3. Proper 404 response and non-zero exit codes for nonexistent files.
4. Path traversal prevention (rejecting `/../secret.txt`).
5. Tolerance and skipping of unknown frame types without crashing.
6. Persistent TCP connections handling multiple requests sequentially.
7. Hexadecimal frame dumping and verification against the protocol specification.

---

## Annotated Hexdump Location
A byte-level annotated hexdump of live captured network frames is available in:
[`examples/annotated-hexdump.md`](examples/annotated-hexdump.md)
