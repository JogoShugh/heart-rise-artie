# heart-coap

HEART over CoAP (RFC 7252, SPEC §6.0.1) on Eclipse Californium. A Spring Boot
auto-configuration (`HeartCoapAutoConfiguration`) starts a CoAP server on UDP
that serves the same `HeartResource` beans as `heart-spring`, at the same
paths, in HAL Schema Forms only.

## How HTTP maps to CoAP

| HTTP | CoAP |
|---|---|
| `Accept-Profile`, `Prefer`, `Profile`, `Preference-Applied` | Options 65000, 65004, 65008, 65012 |
| `traceparent` | Option 65020 (elective, NoCacheKey); the server reads it no more than it reads the HTTP header |
| `Cache-Control: no-cache` on a read | Option 65003 No-Cache (critical); responses Max-Age 0 |
| ETag | First 8 bytes of SHA-256 over the HTTP ETag value |
| 200 / 304 | 2.05 Content / 2.03 Valid |
| Act success | 2.04 Changed, with the new Layer 1 |
| An act on the bookmark (`/actions/{path}`): 201, `Location` | 2.01 Created, with `Location-Path` and the new resource's Layer 1 |
| 428 / 412 / 409 / 422 | 4.00 (problem type tells it apart) / 4.12 / 4.09 / 4.22 |
| `hal+json`, `schema+json`, `problem+json`, `json-home`, `alps+json` | Content-Formats 65000–65004; JSON is 50 |

Large bodies use block-wise transfer. `CoapOptions.registry` holds the custom
options; a client must use it too.

## Configuration

`heart.coap.port`: 5683 by default, `0` for a free port, `-1` to turn it off.

## Tests

`src/test/resources/features/coap.feature`: the lamp over real UDP with a
Californium client.
