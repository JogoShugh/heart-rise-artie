# Idea: Buster over CoAP

**Status:** idea only, 2026-10-12. Nothing here is decided or built. Raised
by the second Roy Bott review: all 125 mutations of the 12 October campaign
were on HTTP, and no CoAP scenario appears in Buster's matrix. The CoAP
binding is tested by ARTIE's client suite against a scripted server and by
the live scenarios, but no adversary has attacked it.

## Why it is mostly there already

Buster's core does not depend on HTTP. The engine, the plugins, the verifier
and campaigns work on a `BusterRequest` and a `BusterResponse`: a method, a
path, headers and a body. Only `BusterWebFilter`, the Spring WebFlux filter,
is HTTP. And since 25dcf48 a CoAP request carries `traceparent` (option
65020), so Buster can tie a CoAP request to ARTIE's events as it does over
HTTP.

## What it would take

- A CoAP adapter in Buster: a Californium `MessageInterceptor` on the server's
  CoAP endpoint, or a wrapper around its message deliverer, found by a Spring
  bean post-processor so Buster stays independent of heart-coap.
- A mapping each way between CoAP options and the header names the plugins
  read and write, following SPEC §6.0.1:
  - Content-Format and `Content-Type`;
  - the 8-byte ETag option and `ETag` (hex, quoted);
  - 65008 Profile and `Link: rel="profile"`;
  - 65012 Preference-Applied and `Preference-Applied`;
  - Max-Age and `Cache-Control` (for `relax-layer1-cache`);
  - 65020 traceparent and `traceparent`;
  - the response code and an HTTP status (2.05 and 200, 4.12 and 412, and so on).
- A client name for the report: CoAP has no `User-Agent`, so the trace id
  would have to stand in, or ARTIE would send a client-name option.
- Campaign plumbing: the live CoAP scenarios join the dry run and the rounds.

## Open questions

- Should a stale ETag planted over CoAP be 8 bytes, so it still looks valid?
- Do all 8 plugins make sense over CoAP? `alias-forms-href` and
  `strip-forms-integrity` act on the body and carry over as they are;
  `relax-layer1-cache` must also remove the No-Cache answer path.
