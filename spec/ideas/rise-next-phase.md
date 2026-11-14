# Idea: RISE next phase, asynchronous reads with QUERY

**Status:** an exploration, 2026-11-08. Nothing built, and SPEC is unchanged. It comes after the minimal slice
(`docs/reports/rise-minimal-e2e/`) and builds on `rise-core-and-modules.md`, `rise-deep-dive.md` §7 and §8, and
`amqp-binding-vision.md`.

## The idea

A read can take time: a large search, a report, filtered history. Today a read answers 200 or fails. In this phase a
read may answer 202 now and deliver its answer later, by pull or by callback. A form works sync or async, as an act
does: sync when the caller sends no `_rise`, async when it does. SPEC §7.2 already says this for acts. This applies it
to reads.

## Shape

- **Method:** `QUERY` on a search or history form. The body holds the query fields, plus `_rise` when the caller wants a
  callback.
- **Body type:** `application/hal+json` under the HAL Schema Forms profile (decision 1 in the minimal slice).
- **Accept-Query:** the target advertises
  `Accept-Query: "application/hal+json";profile="https://w3id.org/heart-rise/hal-schema-forms"`. RFC 10008 §3 allows
  parameters, mapped to Structured Field parameters. Use RISE's composite profile URI, the one clients negotiate (SPEC
  §6.1), not the upstream URI.
- **Sync:** no `_rise`. The target answers 200 with the result.
- **Async:** `_rise` present. The target answers 202 with `Location` at a result resource in state awaiting, and
  `Cache-Control: no-store`.
- **Result, by pull:** the caller GETs the result resource, which is a resource like any other (R2).
- **Result, by push:** the target sends the outcome to `hr:reply-to` as a signed act, as in the minimal slice.
- **Size:** large results are held, the outcome carrying a link to the result (H1). Small results go inline (S1–S3).
- **Retry:** a repeat query on the same reply gets 409, as a repeat act does.

## Standards this stands on

- **RFC 10008, QUERY** (June 2026, Proposed Standard):
  - Safe and idempotent (§2). Retries are free.
  - The target resource must not change. Creating a result resource for later GET is allowed (§2.3–2.4).
  - Responses are cacheable, and the cache key includes the body (§2.7). So the 202 says `no-store`, and results carry
    validators.
  - The body needs a `Content-Type`. Missing or wrong gets 4xx. Unsupported gets 415. No sniffing (§2.1).
- **RFC 7240, Prefer:** `respond-async` gives poll mode with no envelope, as deep dive §8 says. Push needs the `_rise`
  address.
- **Upstream HAL Schema Forms** (jbadeau) lists methods GET, DELETE, PATCH, POST, PUT, and says "Producers MAY use
  values outside this set." QUERY is allowed there, but not defined.

## The safety question

RFC 10008 says a QUERY must not change the target resource. This design sends a callback, an outbound effect the RFC
does not cover. Two readings:

- **Strict:** a read with `_rise` is not safe. Then it is a POST act, and the RFC does not apply.
- **Practical:** the callback belongs to the result resource, which the RFC allows. The spec states the callback as
  that resource's own behaviour.

Recommendation: QUERY for reads that take time but change nothing beyond their result resource. POST for work with
effects beyond that. The author decides.

## What changes for clients

- Accept 202 on a read, not only on an act.
- Parse `Accept-Query` and its profile parameter.
- Send QUERY, with a GET twin where the form needs one. Proxies and some caches may reject an unknown method, so every
  QUERY form has a GET template the server also serves. That GET template is the floor.

## Where it fits

- Large search and reports.
- Filtered history, as `hr:history` with a body instead of a long query string.
- Held results. A QUERY's `Location` can point at a GET-able result (RFC 10008 §2.4). That is the held pattern, H1.

## Dependencies

- **Spring:** the 7.1 line has `RequestMethod.QUERY`. Boot 4.1.0 pins Framework 7.0.8, so the build needs an explicit
  override. This is priority 2 on the list.
- **http4k:** QUERY from 6.22.0.0, per the changelog (earlier evidence).
- **CoAP:** FETCH (RFC 8132) is the same idea. SPEC's CoAP table would gain a row. Check the RFC before relying on it.

## Open

1. QUERY or POST for async reads (see the safety question).
2. Whether the callback counts as an effect under a safe method.
3. Where a result's shape is declared. Proposed: on the job or result form, so the caller knows what it gets.
4. Encryption. The caller's key, unused in the minimal slice, becomes live here if results are encrypted (JWE, RFC 7516).
5. Envelope placement, from the minimal slice: `_rise` wrapper (deep dive §7) or `_links` at the top of the body.
6. Relation names: `hr:reply-to`, `hr:keys`, `hr:progress`, `hr:outcome`, `hr:history`, `hr:error-reply`. Check against the
   IANA registry before any claim of registration.
7. Whether to propose QUERY to the upstream draft, or keep it as a HEART profile addition. Nothing is submitted; this is
   the author's call.
