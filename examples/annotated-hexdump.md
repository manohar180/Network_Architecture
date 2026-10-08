# Annotated Hexdump: BHTTP/1 Protocol Exchange

This document provides a byte-by-byte annotated hexdump of a complete live request and response captured from the `ObserveServer` and `BcurlClient` implementation over a TCP connection.

The captured exchange demonstrates a client requesting `/index.html` from the server running on `localhost:9000`.

---

## 1. Client REQUEST Frame

### Raw Frame Hexdump (51 bytes total: 9 bytes header + 42 bytes payload)

```text
0000  00 00 2a 01 01 00 00 00  01 01 03 47 45 54 00 0b  |..*........GET..|
0010  2f 69 6e 64 65 78 2e 68  74 6d 6c 02 0a 00 0e 6c  |/index.html....l|
0020  6f 63 61 6c 68 6f 73 74  3a 39 30 30 30 09 00 03  |ocalhost:9000...|
0030  2a 2f 2a                                          |*/*|
```

### Byte-by-Byte Breakdown

#### A. Fixed Frame Header (Bytes 0000 - 0008)
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0000 - 0002` | `00 00 2a` | 24-bit unsigned payload length (big-endian) | 42 bytes |
| `0003` | `01` | Frame Type | `0x01` (`REQUEST`) |
| `0004` | `01` | Flags | `0x01` (`END_STREAM` set) |
| `0005 - 0008` | `00 00 00 01` | 31-bit stream identifier (MSB = 0) | Stream `1` |

#### B. Request Payload (Bytes 0009 - 0032)
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0009` | `01` | Protocol Version (1 byte) | BHTTP/1 (`0x01`) |
| `000a` | `03` | Method length (1 byte) | 3 bytes |
| `000b - 000d` | `47 45 54` | Method UTF-8 string | `"GET"` |
| `000e - 000f` | `00 0b` | Path length (16-bit unsigned big-endian) | 11 bytes |
| `0010 - 001a` | `2f 69 6e 64 65 78 2e 68 74 6d 6c` | Path UTF-8 string | `"/index.html"` |
| `001b` | `02` | Header count (1 byte) | 2 headers |

#### C. Encoded Headers
##### Header 1: `host: localhost:9000`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `001c` | `0a` | Header name table index (1 byte) | Index 10 (`host`) |
| `001d - 001e` | `00 0e` | Header value length (16-bit big-endian) | 14 bytes |
| `001f - 002c` | `6c 6f 63 61 6c 68 6f 73 74 3a 39 30 30 30` | Header value UTF-8 string | `"localhost:9000"` |

##### Header 2: `accept: */*`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `002d` | `09` | Header name table index (1 byte) | Index 9 (`accept`) |
| `002e - 002f` | `00 03` | Header value length (16-bit big-endian) | 3 bytes |
| `0030 - 0032` | `2a 2f 2a` | Header value UTF-8 string | `"*/*"` |

---

## 2. Server RESPONSE Frame

### Raw Frame Hexdump (73 bytes total: 9 bytes header + 64 bytes payload)

```text
0000  00 00 40 02 00 00 00 00  01 01 00 c8 05 05 00 06  |..@.............|
0010  32 30 30 20 4f 4b 01 00  09 74 65 78 74 2f 68 74  |200 OK...text/ht|
0020  6d 6c 02 00 03 32 34 31  03 00 0a 6b 65 65 70 2d  |ml...241...keep-|
0030  61 6c 69 76 65 04 00 11  4f 62 73 65 72 76 65 53  |alive...ObserveS|
0040  65 72 76 65 72 2f 31 2e  30                       |erver/1.0|
```

### Byte-by-Byte Breakdown

#### A. Fixed Frame Header (Bytes 0000 - 0008)
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0000 - 0002` | `00 00 40` | 24-bit unsigned payload length (big-endian) | 64 bytes |
| `0003` | `02` | Frame Type | `0x02` (`RESPONSE`) |
| `0004` | `00` | Flags | `0x00` (`END_STREAM` is 0; body follows in DATA) |
| `0005 - 0008` | `00 00 00 01` | 31-bit stream identifier (MSB = 0) | Stream `1` |

#### B. Response Payload (Bytes 0009 - 0048)
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0009` | `01` | Protocol Version (1 byte) | BHTTP/1 (`0x01`) |
| `000a - 000b` | `00 c8` | Status code (16-bit unsigned big-endian) | `200` |
| `000c` | `05` | Header count (1 byte) | 5 headers |

#### C. Encoded Headers
##### Header 1: `status: 200 OK`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `000d` | `05` | Header name table index | Index 5 (`status`) |
| `000e - 000f` | `00 06` | Header value length (16-bit big-endian) | 6 bytes |
| `0010 - 0015` | `32 30 30 20 4f 4b` | Value UTF-8 string | `"200 OK"` |

##### Header 2: `content-type: text/html`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0016` | `01` | Header name table index | Index 1 (`content-type`) |
| `0017 - 0018` | `00 09` | Header value length (16-bit big-endian) | 9 bytes |
| `0019 - 0021` | `74 65 78 74 2f 68 74 6d 6c` | Value UTF-8 string | `"text/html"` |

##### Header 3: `content-length: 241`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0022` | `02` | Header name table index | Index 2 (`content-length`) |
| `0023 - 0024` | `00 03` | Header value length (16-bit big-endian) | 3 bytes |
| `0025 - 0027` | `32 34 31` | Value UTF-8 string | `"241"` |

##### Header 4: `connection: keep-alive`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0028` | `03` | Header name table index | Index 3 (`connection`) |
| `0029 - 002a` | `00 0a` | Header value length (16-bit big-endian) | 10 bytes |
| `002b - 0034` | `6b 65 65 70 2d 61 6c 69 76 65` | Value UTF-8 string | `"keep-alive"` |

##### Header 5: `server: ObserveServer/1.0`
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0035` | `04` | Header name table index | Index 4 (`server`) |
| `0036 - 0037` | `00 11` | Header value length (16-bit big-endian) | 17 bytes |
| `0038 - 0048` | `4f 62 73 65 72 76 65 53 65 72 76 65 72 2f 31 2e 30` | Value UTF-8 string | `"ObserveServer/1.0"` |

---

## 3. Server DATA Frame

### Raw Frame Hexdump (250 bytes total: 9 bytes header + 241 bytes payload)

```text
0000  00 00 f1 03 01 00 00 00  01 3c 21 44 4f 43 54 59  |.........<!DOCTY|
0010  50 45 20 68 74 6d 6c 3e  0a 3c 68 74 6d 6c 20 6c  |PE html>.<html l|
0020  61 6e 67 3d 22 65 6e 22  3e 0a 3c 68 65 61 64 3e  |ang="en">.<head>|
0030  0a 20 20 20 20 3c 6d 65  74 61 20 63 68 61 72 73  |.    <meta chars|
0040  65 74 3d 22 55 54 46 2d  38 22 3e 0a 20 20 20 20  |et="UTF-8">.    |
0050  3c 74 69 74 6c 65 3e 42  48 54 54 50 2f 31 20 54  |<title>BHTTP/1 T|
0060  65 73 74 20 53 65 72 76  65 72 3c 2f 74 69 74 6c  |est Server</titl|
0070  65 3e 0a 3c 2f 68 65 61  64 3e 0a 3c 62 6f 64 79  |e>.</head>.<body|
0080  3e 0a 20 20 20 20 3c 68  31 3e 57 65 6c 63 6f 6d  |>.    <h1>Welcom|
0090  65 20 74 6f 20 4f 62 73  65 72 76 65 53 65 72 76  |e to ObserveServ|
00a0  65 72 3c 2f 68 31 3e 0a  20 20 20 20 3c 70 3e 54  |er</h1>.    <p>T|
00b0  68 69 73 20 70 61 67 65  20 77 61 73 20 73 65 72  |his page was ser|
00c0  76 65 64 20 6f 76 65 72  20 74 68 65 20 42 48 54  |ved over the BHT|
00d0  54 50 2f 31 20 62 69 6e  61 72 79 20 70 72 6f 74  |TP/1 binary prot|
00e0  6f 63 6f 6c 2e 3c 2f 70  3e 0a 3c 2f 62 6f 64 79  |ocol.</p>.</body|
00f0  3e 0a 3c 2f 68 74 6d 6c  3e 0a                    |>.</html>.|
```

### Byte-by-Byte Breakdown

#### A. Fixed Frame Header (Bytes 0000 - 0008)
| Byte Range | Hex Value | Description | Semantic Value |
|---|---|---|---|
| `0000 - 0002` | `00 00 f1` | 24-bit unsigned payload length (big-endian) | 241 bytes |
| `0003` | `03` | Frame Type | `0x03` (`DATA`) |
| `0004` | `01` | Flags | `0x01` (`END_STREAM` set; final chunk) |
| `0005 - 0008` | `00 00 00 01` | 31-bit stream identifier (MSB = 0) | Stream `1` |

#### B. Data Payload (Bytes 0009 - 00f9)
The 241 payload bytes represent the verbatim UTF-8 encoded text of the requested `./www/index.html` file.
The flag byte `0x01` signifies the end of the stream for request stream 1.
