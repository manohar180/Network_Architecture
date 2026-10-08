# BHTTP/1 Testing Guide

This guide lists the exact commands to compile, start, and thoroughly test the BHTTP/1 server and client.

---

## 1. Compile All Sources
```bash
javac -d out src/*.java
```

---

## 2. Start Server
Run the server on port 9000 using `./www` as the document root:
```bash
java -cp out ObserveServer ./www 9000
```
Or with wrapper script:
```bash
./observe ./www 9000
```

---

## 3. Normal Request (200 OK)
Request an existing text file:
```bash
java -cp out BcurlClient localhost:9000/hello.txt
```
Or with wrapper script:
```bash
./bcurl localhost:9000/hello.txt
```
**Expected:** Status 200, prints text body, exits with code `0`.

---

## 4. Verbose Request (Hexdump Inspection)
Inspect raw frame headers, hexadecimal byte layouts, and decoded ASCII:
```bash
java -cp out BcurlClient -v localhost:9000/index.html
```
Or with wrapper script:
```bash
./bcurl -v localhost:9000/index.html
```
**Expected:** Full frame dump for `REQUEST`, `RESPONSE`, and `DATA` frames.

---

## 5. Non-Existent Resource (404 Not Found)
Request a missing resource:
```bash
java -cp out BcurlClient localhost:9000/missing.html
```
Or with wrapper script:
```bash
./bcurl localhost:9000/missing.html
```
**Expected:** Status 404, prints "404 Not Found", exits with non-zero code `1`.

---

## 6. Path Traversal Test
Attempt directory escape:
```bash
java -cp out BcurlClient localhost:9000/../secret.txt
```
Or with wrapper script:
```bash
./bcurl localhost:9000/../secret.txt
```
**Expected:** Status 400, rejected as an illegal path, exits with non-zero code `1`.

---

## 7. Persistent Connection Test (Multiple Sequential Requests)
Issue multiple requests across a single TCP socket:
```bash
java -cp out BcurlClient localhost:9000/hello.txt /test.html /index.html
```
Or with wrapper script:
```bash
./bcurl localhost:9000/hello.txt /test.html /index.html
```
**Expected:** Single TCP connection established; all three resources fetched sequentially.

---

## 8. Large / Chunked Response (Multiple DATA Frames)
Request a file larger than the 16 KB chunk limit:
```bash
java -cp out BcurlClient -v localhost:9000/large.txt
```
Or with wrapper script:
```bash
./bcurl -v localhost:9000/large.txt
```
**Expected:** Received in multiple sequential `DATA` frames, with `END_STREAM = 0x01` on the final frame chunk.

---

## 9. Automated Malformed-Input & Robustness Tests
Run the standalone test suite covering truncated frames, invalid reserved stream-ID bits, invalid UTF-8 byte sequences, Content-Length mismatches, and unknown frame types:
```bash
java -cp out TestRunner 9000
```
**Expected:** All 12 test assertions pass (`[PASS] 1..12`).

---

## 10. Concurrency Test (Simultaneous Clients)
The automated test runner executes simultaneous worker threads requesting endpoints concurrently:
```bash
java -cp out TestRunner 9000
```
Assertion 12 (`Multiple Simultaneous Clients`) verifies multi-client concurrency over thread-per-connection sockets.
