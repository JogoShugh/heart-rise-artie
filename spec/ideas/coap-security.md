# Idea: credentials over CoAP

Status: DTLS with pre-shared keys is built (15 October 2026). OSCORE is still an idea.

## What's built

CoAP has no Authorization header, so HTTP's Basic and Bearer credentials can't travel over it.

- **Plain `coap`** carries no credential, so it listens on the loopback address only.
- **`coaps`** is DTLS 1.2 with one pre-shared key per client (RFC 7252 §9.1.3.1), using Californium Scandium and `TLS_PSK_WITH_AES_128_GCM_SHA256`.
  - It listens on every address, on its own port: garden 5784, basketball 5785, tickets 5786.
  - It admits only the identities it holds a key for. A client without the right key never completes the handshake.
  - The server is configured with `HEART_COAPS_PORT` and `HEART_COAPS_PSK` (`identity:base64-key,...`). One without the other stops it at the start.
- **ARTIE** reads the key like any credential: `ARTIE_PSK_IDENTITY_…` and `ARTIE_PSK_KEY_…`, a `[coaps://host:port]` section with `psk_identity` and `psk_key`, or Vault. Only the DTLS connector holds the key, and no model is shown it.
- `scripts/dev-credentials.sh` makes one key per service.

One difference from Bearer tokens: the server holds each key itself, not a hash, because DTLS needs it.

## What's next

- **OSCORE** (RFC 8613) protects each message end to end, even through proxies. It's the better fit for the seed-tray idea (`seed-tray-polyglot.md`), where a hub may forward between devices.
- **ACE** (RFC 9200, OAuth for constrained devices) issues OSCORE keys from an authorization server. It's the CoAP side of the Okta or Google plan.
- **DTLS with raw public keys or certificates** would remove shared secrets from the server.
