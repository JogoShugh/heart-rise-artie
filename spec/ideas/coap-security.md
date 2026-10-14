# Idea: credentials over CoAP

Status: idea, nothing built. Written 14 October 2026, when the services began requiring credentials over HTTP.

## Why it's needed

Over HTTP, every HEART service now admits only callers with a Basic or Bearer credential. CoAP has no Authorization header, so heart-coap can't check either. Until it can, each CoAP endpoint listens on the loopback address only (`CoapFrontDoor`). Only the same machine can reach it.

That's fine for a demo on one laptop. It isn't fine for the seed-tray idea (`seed-tray-polyglot.md`), where devices on a shelf talk to a hub over the network.

## The two standard routes

- **OSCORE** (RFC 8613): protects the CoAP message itself, end to end, even through proxies. The keys come from a shared secret or from an authorization server, through **ACE** (RFC 9200, OAuth for constrained devices). This is the route that matches the later Okta or Google plan: the authorization server issues a token, and the device proves it holds it.
- **DTLS** (`coaps://`, RFC 9147): protects the transport, like TLS for UDP. Californium supports DTLS with pre-shared keys (PSK), raw public keys and certificates. PSK is the easiest to set up, with one key per device.

## What ARTIE would need

- A `coaps` binding, or OSCORE in `CoapBinding`, chosen by URI scheme as bindings are now.
- A credential source for CoAP keys, keyed by origin like the HTTP ones (`coaps://tray-1.local:5684`). The default ports are already in `Origin`.
- The same rule as for HTTP: no model ever sees a key.

## Order

1. DTLS with PSK, between ARTIE and one heart-coap service on the network. This proves the binding and the key source.
2. OSCORE with a static context.
3. ACE with an authorization server, beside the HTTP OIDC work.
