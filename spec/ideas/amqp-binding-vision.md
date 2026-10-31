# AMQP for HEART: how it would work and look

A vision, written 2026-10-17, below the DSL: the documents a client holds and the messages that
carry them. A companion to `amqp-binding.md`, which also records the prior art. Nothing here is built. Every example document is
the garden's real HAL Schema Forms output (read over HTTP on 2026-10-17), with only the
addresses changed; the broker and host names (`broker.example`, `greenhouse.local`) are made up.

## The rules it stands on

SPEC §6.0 already says what any binding must do, and the AMQP one only fills it in:

1. Every link, form target, profile, relation and integrity-carrying reference is a URI, of any
   scheme.
2. The client interacts with a URI through the binding registered for its scheme.
3. A relative reference takes its base's scheme.
4. A URI with no binding is refused before anything is sent, and never rewritten.
5. A form's `method` is a word in the vocabulary of the binding its target selects. HAL Schema
   Forms writes HTTP's method names; another binding maps them, or refuses one it can't carry.

So nothing in HAL Schema Forms changes. A document doesn't know which binding carried it, and
a form's target decides how its act travels.

## The binding: AMQP 1.0, `amqp` and `amqps`

AMQP is messaging, not request and response, so the binding builds request and response from
AMQP's own reply pattern, and puts everything HTTP keeps in headers into the message's
properties. AMQP 1.0 gives each message a fixed `properties` section (`message-id`, `to`,
`subject`, `reply-to`, `correlation-id`, `content-type`, `user-id`, `absolute-expiry-time`)
and a free `application-properties` map. Both are used.

| Core concept (SPEC §6.0) | AMQP binding |
|---|---|
| Where a request goes | The URI's authority is the broker (`amqps` port 5671, `amqp` 5672). The message is sent to the service's request node, and its `to` property is the full URI of the resource. |
| Read | `subject` = `GET` |
| Act | `subject` = the form's `method` (`POST`), the input as the body |
| Request and response | The client names a reply node of its own in `reply-to`; the answer carries the request's `message-id` as its `correlation-id`. `absolute-expiry-time` bounds how long the request is worth answering. |
| Answer, by meaning | The answer's `subject` is HTTP's status number (200, 201, 304, 409, 412, 422, 428), as the binding's own words. This is the convention Apache Qpid Dispatch Router already uses for HTTP over AMQP (method in the request's `subject`, status in the response's, DISPATCH-2212), so existing AMQP tooling reads HEART's messages. CoAP chose its own codes; AMQP has none. The client only reports the number, as it does today. |
| Requested rendering | `accept`, `accept-profile`, `prefer` in `application-properties`, with HTTP's values |
| Applied rendering | `content-type` property; `profile` and `preference-applied` in `application-properties` |
| Validator | `etag` in `application-properties`: the HTTP binding's ETag value, whole. AMQP has no size limit, so unlike CoAP's 8-byte form, the validator is the same string over HTTP and AMQP. |
| Unchanged | `if-none-match` on a read → `subject` 304, no body |
| Act, conditional | `if-match` with the validator held |
| Act done | 200; `content-location` names the resource |
| New resource | 201; `location` names it, as an absolute URI of any scheme; `content-location` says what the body represents |
| Refusals | 428 precondition required, 412 stale, 409 not offered, 422 invalid input, each with an RFC 9457 problem body |
| Never fresh, immutable | No shared caches sit between client and service, so only the client's own cache applies: Layer 1 is always read again, conditionally; immutable layers are kept by URI. |
| Integrity | The link's `integrity` value, as in every binding |
| Who is asking | Credentials per connection: SASL, `PLAIN` with a token or `EXTERNAL` with a client certificate, over TLS for `amqps`. The broker can check `user-id` against the connection's identity, so the service can trust it. |
| Trace | `traceparent` (W3C Trace Context; W3C has a draft for carrying it in AMQP: follow it) and `user-agent` |
| Large bodies | Nothing to do: AMQP carries large messages natively |

## A: everything over AMQP

The garden served through a broker. The client's entry is `amqps://broker.example/garden/`.
The garden's documents use relative references, so every link and form resolves against the
document's own `amqps` base. The bytes of the documents are exactly what HTTP serves today.

### The bookmark

```json
{
  "title": "Garden beds",
  "_links": {
    "self": { "href": "/garden/" },
    "service-desc": { "href": "/garden/openapi.json", "type": "application/vnd.oai.openapi+json;version=3.1" },
    "curies": [ { "name": "garden", "href": "/garden/profiles/garden#{rel}", "templated": true } ]
  },
  "_forms": {
    "garden:get-bed": {
      "_links": { "target": { "href": "/garden/beds/{id}", "templated": true } },
      "method": "GET",
      "title": "Get a bed by its id",
      "schema": { "type": "object", "properties": { "id": { "type": "integer" } }, "required": ["id"] }
    },
    "garden:build-bed": {
      "_links": { "target": { "href": "/garden/beds" } },
      "method": "POST",
      "title": "Build a bed",
      "schema": { "$id": "/garden/schemas/…", "…": "…" }
    }
  }
}
```

Every `href` here resolves to `amqps://broker.example/garden/…`. The profile, the schemas and
the OpenAPI description are read over AMQP too. Nothing in the document says AMQP.

### Reading a bed: the messages

The client follows `garden:get-bed` with `id` 9. Its binding sends:

```
to:              amqps://broker.example/garden/beds/9
subject:         GET
message-id:      7f3a…01
reply-to:        /queues/artie-replies-5c1e        (the client's own reply node)
application-properties:
  accept:          application/hal+json
  accept-profile:  <https://w3id.org/heart-rise/hal-schema-forms>
  traceparent:     00-4bf9…-a1b2…-01
  user-agent:      artie/0.1
```

The garden's AMQP front door answers on the client's reply node:

```
subject:         200
correlation-id:  7f3a…01
content-type:    application/hal+json
application-properties:
  etag:     "seq-1.hsf.L0.ea3c9ce48a4d50b2"
  profile:  <https://w3id.org/heart-rise/hal-schema-forms>
body:
  { "id": 9, "label": "Bed over coaps", "status": "PLANTED", "cropName": "basil", …,
    "_links": { "self": { "href": "/garden/beds/9" }, … },
    "_forms": {
      "garden:water-cell": {
        "_links": { "target": { "href": "/garden/beds/9/actions/water-cell" } },
        "method": "POST",
        "schema": { "properties": { "milliliters": { "type": "integer", "minimum": 1, "maximum": 1000 } },
                    "required": ["milliliters"] }
      }
    } }
```

The `etag` is the same string HTTP sends for the same state.

### Acting: water the bed

```
to:              amqps://broker.example/garden/beds/9/actions/water-cell
subject:         POST
message-id:      7f3a…02
reply-to:        /queues/artie-replies-5c1e
content-type:    application/json
application-properties:
  if-match:  "seq-1.hsf.L0.ea3c9ce48a4d50b2"
body:            { "milliliters": 250 }
```

Answered with `subject` 200, the bed's new representation, its new `etag`, and
`content-location: amqps://broker.example/garden/beds/9`. Had someone watered it a moment
before, the answer would be `subject` 412 with a problem body, and the script above would see
"stale, refused by the server", exactly as it does over HTTP and CoAP.

### A new resource

`garden:build-bed` answers `subject` 201 with `location: amqps://broker.example/garden/beds/10`.
The client holds bed 10 next, as it does over every binding.

### What AMQP adds

- **Change notices.** A bed could link a node that publishes its new representation whenever it
  changes: `"hr:changes": { "href": "/garden/beds/9/changes" }`. A client attaches a receiver
  and gets each new state with its `etag`, as CoAP's Observe does. A script could someday wait
  on a notice instead of reading again with `until`. That would be a new relation in the HEART
  profile, offered by any binding that can carry it.
- **Late answers.** An answer may arrive long after the request, as long as the request hasn't
  expired. Acts that take minutes, like a Jenkins build, fit without polling.

## B: mixed protocols in one document

Now the same bed, read over `https`, from a garden that sends each act where it is best served.
Reads stay on HTTPS, where caches and CDNs help. Watering goes straight to the bed's own sensor
controller over `coaps`. Building, which only queues work, goes to a durable queue over
`amqps`. Change notices come from the broker.

```json
{
  "id": 9,
  "label": "Bed over coaps",
  "status": "PLANTED",
  "cropName": "basil",
  "_links": {
    "self": { "href": "https://garden.example/beds/9" },
    "curies": [ { "name": "garden", "href": "https://garden.example/profiles/garden#{rel}", "templated": true } ],
    "hr:changes": { "href": "amqps://broker.example/garden/beds/9/changes" },
    "hr:unavailable": { "href": "https://garden.example/unavailable/1648f3dc5307f159",
                        "type": "application/json", "integrity": "sha256-Fkjz3FMH8VnKolwCT8ID7fvf1jvy2LKP83Ge1Xk4UOA=" }
  },
  "_forms": {
    "garden:water-cell": {
      "title": "Water the bed",
      "_links": { "target": { "href": "coaps://bed-9.greenhouse.local/actions/water-cell" } },
      "method": "POST",
      "contentType": "application/json",
      "schema": { "$id": "https://garden.example/schemas/7f232c04c52671dd", "…": "…" }
    },
    "garden:harvest": {
      "title": "Harvest the bed",
      "_links": { "target": { "href": "amqps://broker.example/garden/beds/9/actions/harvest" } },
      "method": "POST"
    }
  }
}
```

What the client does, form by form:

- **Watering** goes over coaps: a DTLS handshake with the controller's pre-shared key (a
  credential for the origin `coaps://bed-9.greenhouse.local`), `POST` with `If-Match`. The
  validator the client holds is the HTTP `ETag`; the CoAP binding sends its 8-byte form, the first
  8 bytes of SHA-256 over that ETag (SPEC §6.0.1). The controller must compute the same, so it
  must know the garden's ETag for the bed's state: the hard part of this example, and the
  reason mixing needs a rule in SPEC.
- **Harvesting** goes over amqps, to `broker.example` with that origin's SASL credential. The
  validator is the HTTP ETag unchanged, because the AMQP binding carries it whole. HTTPS and AMQP
  mix with no translation at all.
- **Changes** come over amqps, from the broker, for as long as the client listens.
- **The profile, schemas and reasons** stay on HTTPS, where the immutable ones are cached by
  anyone on the path. Their `integrity` values hold whichever binding fetched them.

### Rules mixing needs

- **Validators cross bindings.** SPEC should say the HTTP ETag is the validator's canonical
  form, and that each binding carries it whole or in a defined derivation (CoAP: 8 bytes of
  SHA-256; AMQP: whole). Then a client can act over any binding on a state it read over another.
- **No downgrade.** A form that arrived over a secure scheme (`https`, `coaps`, `amqps`) and
  points at a plain one (`http`, `coap`, `amqp`) is refused before anything is sent, unless the
  client is configured to allow it. Browsers call this mixed content.
- **Credentials are per origin.** Each scheme, host and port is its own origin with its own
  credential: a Bearer token for `https://garden.example`, a pre-shared key for
  `coaps://bed-9.greenhouse.local`, a SASL identity for `amqps://broker.example`. ARTIE's
  credential sources already work this way.
- **Answers name their own resources.** A new resource's `location` may be any scheme. A
  harvest queued over AMQP may answer with a harvest record at an `https` address.

## What the DSL sees

Nothing new. The same `garden-bed.kts` runs with `entry=amqps://broker.example/garden/`, and
against a mixed garden its trace simply shows each step's address in whatever scheme the
document chose. The script never says AMQP, CoAP or HTTP, before or after.

## Open questions

- **Request nodes.** How does a URI name the node a request is sent to? Proposal above: the
  service's request node, found from the URI's first path segment or from the service
  description; `to` carries the full resource URI. Brokers address nodes differently (RabbitMQ
  4's `/queues/…` and `/exchanges/…`), so this needs a decision and a test on a real broker.
- **Status in the subject.** Decided in this vision: the response's `subject` carries HTTP's
  status number, following Qpid Dispatch Router, rather than words of HEART's own (`done`,
  `stale`, …). Words would be more native to AMQP; the numbers keep the problem types, the
  existing client code, and compatibility with the one HTTP-over-AMQP convention in production.
- **Lost answers.** If no answer arrives before the request expires, the client can't know
  whether an act happened. Reads are safe to repeat. Acts need either a read before retrying,
  which the validator makes safe (a repeat is refused as stale if the first one happened), or
  an idempotency key.
- **Which AMQP.** 1.0 (OASIS) throughout; 0-9-1 brokers aren't covered.
