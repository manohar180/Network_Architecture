# BHTTP/1: Binary Application Protocol Specification
**Protocol Name:** BHTTP/1 (Binary Hypertext Transfer Protocol Version 1)  
**Status:** Educational Standard Specification  
**Transport:** Transmission Control Protocol (TCP)  
**Encoding:** Network Byte Order (Big-Endian)

---

## 1. Protocol Purpose
BHTTP/1 is a compact, binary-framed application-layer client-server protocol designed for transferring resources over TCP. While inspired by modern framing concepts introduced in HTTP/2, BHTTP/1 eliminates unnecessary complexity, text-parsing overhead, and delimiters (such as `\r\n` boundaries), replacing them with an unambiguous fixed-width binary frame header, typed frames, and a lightweight header table compression mechanism.

---

## 2. Transport & Byte Order
- **Underlying Transport:** Standard reliable, ordered byte stream via TCP.
- **Port:** Default server port is `9000`.
- **Byte Order:** All multi-byte numerical fields (e.g., lengths, status codes, stream IDs) are serialized strictly in **network byte order (big-endian)**.
- **String Encoding:** All strings (methods, paths, header names, and header values) are serialized in **UTF-8**.

---

## 3. Fixed Frame Header (9 Bytes)

Every protocol frame transmitted across the TCP connection begins with an exact 9-byte binary header followed by an optional payload.

### Frame Header Wire Format
```
 +-----------------------------------------------+
 | Length (24 bits)                              |
 +---------------+---------------+---------------+
 | Type (8 bits) | Flags (8 bits)|
 +---------------+---------------+---------------+---------------+
 |R| Stream Identifier (31 bits)                                 |
 +---------------------------------------------------------------+
```

### Detailed Field Breakdown
1. **Payload Length (Bytes 0–2, 24 bits):**
   - Unsigned 24-bit integer specifying the exact number of bytes in the frame payload that immediately follow this 9-byte header.
   - Maximum payload size is $2^{24} - 1 = 16,777,215$ bytes (~16 MB).
   - The 9-byte header itself is **not** included in this length.
2. **Type (Byte 3, 8 bits):**
   - Identifies the semantic purpose and structure of the frame payload.
3. **Flags (Byte 4, 8 bits):**
   - Frame-specific boolean flags.
4. **Stream Identifier (Bytes 5–8, 32 bits total):**
   - Bit 31 (Most Significant Bit, `R`): Reserved bit. **MUST be 0**. Receivers must reject frames with bit 31 set with a protocol error.
   - Bits 0–30 (31 bits): Unsigned 31-bit stream identifier. Standard synchronous client-server request/response flows use Stream ID `1`.

---

## 4. Architectural Rationale for Frame Header Dimensions

- **Why a 9-byte Fixed Header?**  
  A fixed-size header allows receivers to parse the complete header in a single, predictable `read(9)` operation without speculative buffering or parsing variable-length delimiters.
- **Why 24-bit Payload Length?**  
  A 16-bit field would limit individual frames to 64 KB, requiring excessive frame fragmentation for web assets. A 32-bit field (4 GB) would encourage unbounded single-frame memory allocations and denial-of-service vulnerabilities. 24 bits allows single frames up to 16 MB—ideal for typical web payloads—while preserving compact header space.
- **Why 8-bit Frame Type?**  
  One byte supports up to 256 distinct frame types, giving ample room for future protocol evolution while keeping byte alignment clean.
- **Why 8-bit Flags?**  
  One byte provides 8 independent binary flags (such as `END_STREAM`), aligned on a byte boundary.
- **Why a 31-bit Stream Identifier inside a 32-bit Field?**  
  31 bits accommodates up to $2,147,483,647$ streams over long-lived persistent connections. Reserving the highest bit leaves room for future extensions (e.g., flow-control, priority flags, or directional routing).

---

## 5. Frame Types

| Type ID | Name | Direction | Description |
|---|---|---|---|
| `0x01` | `REQUEST` | Client $\to$ Server | Initiates a request for a resource. |
| `0x02` | `RESPONSE` | Server $\to$ Client | Returns metadata, status code, and response headers. |
| `0x03` | `DATA` | Server $\to$ Client | Carries the binary payload body. |
| `0x04`–`0xFF`| *Reserved* | Either | Reserved for future protocol revisions. |

---

## 6. Flags

| Flag Bit | Hex Mask | Applicable Frames | Description |
|---|---|---|---|
| `END_STREAM` | `0x01` | `REQUEST`, `RESPONSE`, `DATA` | Signals that this frame is the final frame sent on the given stream. |

### Semantic Rules for `END_STREAM`:
- On `REQUEST`: **MUST always be set (`0x01`)** because client request payloads do not transmit a request body in Version 1.
- On `RESPONSE`:
  - Set (`0x01`) if the response has **no body** (e.g., empty file or header-only response).
  - Clear (`0x00`) if the response body follows in subsequent `DATA` frames.
- On `DATA`:
  - Cleared (`0x00`) on intermediate body chunks.
  - Set (`0x01`) on the final `DATA` frame of the response stream.

---

## 7. Unknown Frame Handling & Forward Compatibility

A critical requirement of BHTTP/1 is graceful forward compatibility for version 2:
1. When a receiver encounters a frame with an unrecognized `Type` (e.g., `0x04`–`0xFF`), it **MUST NOT crash**.
2. It **MUST NOT treat the unknown frame as an error or drop the TCP connection**.
3. It **MUST read the 24-bit payload length** from the 9-byte header, consume/skip exactly that many payload bytes from the stream, and continue parsing the next frame.

This ensures newer clients or servers can introduce informational or control frames without breaking existing endpoints.

---

## 8. Request Frame Payload Format (`0x01`)

The payload of a `REQUEST` frame has the following binary layout:

```
 +---------------------------------------------------------------+
 | Version (1 byte: 0x01)                                        |
 +---------------------------------------------------------------+
 | Method Length (1 byte)                                        |
 +---------------------------------------------------------------+
 | Method (N bytes, UTF-8)                                       |
 +---------------------------------------------------------------+
 | Path Length (2 bytes, 16-bit big-endian)                      |
 +---------------------------------------------------------------+
 | Path (M bytes, UTF-8)                                         |
 +---------------------------------------------------------------+
 | Header Count (1 byte)                                         |
 +---------------------------------------------------------------+
 | Encoded Headers (variable length)                             |
 +---------------------------------------------------------------+
```

### Constraints:
- `Version`: Must equal `0x01`.
- `Method`: For BHTTP/1, the only supported method is `"GET"`.
- `Path`: Must begin with `'/'`. Must not exceed 4,096 bytes.

---

## 9. Response Frame Payload Format (`0x02`)

The payload of a `RESPONSE` frame contains response metadata and headers. **The body bytes are never embedded in the `RESPONSE` frame.**

```
 +---------------------------------------------------------------+
 | Version (1 byte: 0x01)                                        |
 +---------------------------------------------------------------+
 | Status Code (2 bytes, 16-bit unsigned big-endian)             |
 +---------------------------------------------------------------+
 | Header Count (1 byte)                                         |
 +---------------------------------------------------------------+
 | Encoded Headers (variable length)                             |
 +---------------------------------------------------------------+
```

### Standard Status Codes:
- `200`: Success. Requested resource found and served.
- `400`: Bad Request. Malformed frame, unsupported method, or illegal path.
- `404`: Not Found. Resource does not exist within the document root.
- `500`: Internal Server Error. Unrecoverable I/O or server processing error.

---

## 10. DATA Frame Payload Format (`0x03`)

The payload of a `DATA` frame consists entirely of raw content body bytes:
```
 +---------------------------------------------------------------+
 | Body Bytes (0 to 16,777,215 bytes)                            |
 +---------------------------------------------------------------+
```
The client reassembles the body by concatenating payloads of consecutive `DATA` frames on the stream until receiving a frame with `END_STREAM` (`0x01`).

---

## 11. Header Encoding (HPACK-Lite)

BHTTP/1 uses an indexed and length-prefixed header codec:

### Static Header Table (Indices 1–10)
| Index | Header Name |
|:---:|---|
| `1` (`0x01`) | `content-type` |
| `2` (`0x02`) | `content-length` |
| `3` (`0x03`) | `connection` |
| `4` (`0x04`) | `server` |
| `5` (`0x05`) | `status` |
| `6` (`0x06`) | `cache-control` |
| `7` (`0x07`) | `date` |
| `8` (`0x08`) | `content-encoding` |
| `9` (`0x09`) | `accept` |
| `10` (`0x0A`)| `host` |

### A. Indexed Header Field Wire Format
Used when the header name exists in the static table:
```
 +---------------+-------------------------------+-----------------------+
 | Index (1 byte)| Value Length (2 bytes, uint16)| Value (UTF-8 bytes)   |
 +---------------+-------------------------------+-----------------------+
```

### B. Literal Header Field Wire Format
Used when the header name is not in the static table. The marker byte is `0x00`:
```
 +---------------+-------------------------------+-----------------------+
 | Marker (0x00) | Name Length (2 bytes, uint16) | Name (UTF-8 bytes)    |
 +---------------+-------------------------------+-----------------------+
 | Value Length (2 bytes, uint16)                | Value (UTF-8 bytes)   |
 +-----------------------------------------------+-----------------------+
```

---

## 12. Security & Path Traversal Handling
- The server maps the requested path relative to the document root directory (`./www`).
- Paths must be normalized (removing redundant separators, `.`, and `..` segments).
- The canonical path of the resolved file **must reside strictly within the canonical path of the document root**.
- Requests attempting directory escape (such as `/../secret.txt` or `/../../etc/passwd`) are rejected with `400 Bad Request` or `404 Not Found`. BHTTP/1 implementations reject path traversal with `400 Bad Request`.

---

## 13. Persistent Connection Lifecycle

1. **Connection Establishment:** The client opens a single TCP socket to the server.
2. **Sequential Exchanges:**
   - The client sends a `REQUEST` frame with `END_STREAM` set.
   - The server replies with a `RESPONSE` frame.
   - If a body exists, the server transmits one or more `DATA` frames, setting `END_STREAM` on the final chunk.
   - The server **keeps the TCP socket open** and returns to reading frames.
   - The client may issue subsequent requests on the **same TCP connection**.
3. **Termination:** The connection remains open until either endpoint initiates a TCP close (clean EOF) or an unrecoverable protocol framing error occurs.

---

## 14. Protocol Limits

- `MAX_FRAME_PAYLOAD`: 16,777,215 bytes ($2^{24}-1$)
- `MAX_PATH_LENGTH`: 4,096 bytes
- `MAX_HEADER_COUNT`: 255 headers
- `MAX_HEADER_FIELD_LENGTH`: 65,535 bytes ($2^{16}-1$)
