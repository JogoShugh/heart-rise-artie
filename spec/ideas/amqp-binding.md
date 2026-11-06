# An AMQP binding for HEART: amqp and amqps

An idea, filed 2026-10-17, updated the same day. Not started.

## The idea

The same heart-script runs against the garden over CoAP and over HTTP, unchanged, with only its
`entry` input differing (`docs/reports/heart-script-garden/both.html`). Add a third binding,
AMQP 1.0, and run it a third way: `entry=amqp://localhost/garden/`. If the eight steps come out
the same again, through a message broker this time, that is a much stronger demonstration of
Fielding's first rule ("A REST API should not be dependent on any single communication
protocol …") than two request/response protocols.

## What stays the same

The script, the DSL, ARTIE's core, and the garden's declarations. Nothing above a binding
learns that a broker is involved.

## What gets built

- **The mapping, in SPEC** (a new §6.0.x beside the CoAP binding's §6.0.1). CoAP was easy
  because RFC 7252 already defines methods, codes, ETag and Location-Path. AMQP is messaging,
  with no standard REST mapping, so HEART defines one:
  - request/response by AMQP's reply pattern: `reply-to` and `correlation-id`;
  - the resource's URI in the message's `to` (and `subject`), so the URI still identifies it;
  - the method in the request's `subject` and the status in the answer's `subject`, as Qpid
    Dispatch Router does for HTTP over AMQP;
  - the validator (`if-match`), the answer's validator, the new resource (`location`) and the
    represented resource as application properties; refusals by meaning as statuses (409 not
    offered, 412 stale, 422 invalid input, 428 precondition required) with problem bodies;
  - the body as today: HAL Schema Forms JSON, with its content type.
- **ARTIE**: an `AmqpBinding`, chosen by the `amqp` and `amqps` schemes, filling the same
  protocol-free `Outcome`. A Java AMQP 1.0 client such as Qpid ProtonJ2.
- **heart**: a `heart-amqp` front door beside heart-spring and heart-coap, consuming requests
  from the broker and answering from the same declarations.
- **A broker** in Docker for tests and live runs: RabbitMQ 4 (AMQP 1.0 native) or ActiveMQ
  Artemis.
- Features on both sides, red then green; then the garden script three ways, and the
  side-by-side page grows a third column.

## amqp and amqps

`amqps` is AMQP over TLS, as https is HTTP over TLS, and it is in wide use: port 5671 is the
IANA-assigned port for the "amqps" service (plain amqp is 5672); RabbitMQ's URI specification
defines `amqp://` and `amqps://`; OPC UA's pub/sub mapping (Part 14) uses `amqps://`; client
libraries in many languages take both. HEART would take the same stance as for coap and coaps:

- `amqps` for anything real, with credentials per connection (SASL over TLS);
- plain `amqp` for loopback and tests only, as plain CoAP listens on loopback only today.

Two things to settle before the spec section is written:

- **Which AMQP.** RabbitMQ's URI spec was written for AMQP 0-9-1. This idea uses AMQP 1.0
  (OASIS), which has no single official URI specification, though the same URI forms are used
  in practice. The spec section must say exactly which `amqp://` and `amqps://` URIs a HEART
  client accepts and how they name a resource.
- **Registration.** The port and service name "amqps" are IANA-registered. Whether `amqp` and
  `amqps` are in IANA's URI scheme registry is unconfirmed: check before citing it.

## Mixing schemes in one document

SPEC §6.0 already allows it: "Every link, form target, profile, relation and integrity-carrying
reference is a URI. Any URI scheme MAY appear in any of them." ARTIE chooses a binding per
request, from that request's own URI, so a HAL Schema Forms document read over HTTP may carry a
form whose target is `coap://` or `amqps://`, and the act travels that way. Uses: a building's
HTTP API whose device acts go to the devices over CoAP; a Jenkins start over HTTP whose queue
item is watched over AMQP. Three issues to settle, each with a scenario:

- **Validators differ by binding.** The CoAP validator is the first 8 bytes of SHA-256 over the
  HTTP ETag (SPEC §6.0.1). Read over HTTP, act over CoAP: the If-Match must be the CoAP form. The
  derivation is defined, so a client can compute it; ARTIE likely doesn't across bindings yet.
  An AMQP binding needs its own rule, and the spec a sentence on crossing bindings.
- **Downgrade.** A form that arrived over `https` or `coaps` and points at plain `coap://` or
  `amqp://` hands the act to a weaker channel. Browsers block this as mixed content. HEART should
  say a client refuses to act through a less secure scheme than the one the form came on,
  unless configured to.
- **Credentials are per origin.** `https://garden` and `coaps://garden` are different origins,
  each with its own credential: a Bearer token for one, a pre-shared key for the other.

## Where it shines

- Change notices: a resource's changes published to subscribers, as CoAP's Observe does. A
  script could wait on a notice instead of polling with `until`.
- Long-running acts: an answer that arrives when the work is done, such as a Jenkins queue
  item becoming a build (`spec/ideas/jenkins-gateway.md`).

## Prior art (researched 2026-10-17)

Nobody we could find has done full REST over AMQP: resources identified by URI in every
message, a uniform interface, and hypermedia driving state, with forms whose targets are
`amqp(s)://` URIs. That is what web searches found, not proof that nobody ever has. Pieces
exist, of four kinds.

**HTTP tunnelled over AMQP: in production, but a tunnel.**
- Apache Qpid Dispatch Router, and Skupper on top of it (Red Hat Service Interconnect), turn
  HTTP/1 and HTTP/2 traffic into AMQP messages, routed as request and response. Since Dispatch
  1.17 a request's AMQP `subject` is the HTTP method and a response's `subject` is its status
  (DISPATCH-2212). HEART's binding adopts that convention (see `amqp-binding-vision.md`).
- RabbitMQ 4: its AMQP 1.0 clients manage queues, exchanges and bindings through what RabbitMQ
  calls an "HTTP-over-AMQP management endpoint" (for example `GET /bindings`, named in
  CVE-2026-61837). Fixed paths documented out of band: no forms, no state-dependent affordances.

**A standard attempted, apparently not finished.** In December 2018 Clemens Vasters proposed
"HTTP Semantics and Content over AMQP" at OASIS (AMQP Bindings and Mappings TC), mapping HTTP
semantics (RFC 7231) onto AMQP 1.0; Alan Conway reported a prototype. We found no published
draft or specification after that.

**The opposite of REST, standardised.** OASIS AMQP Management (a working draft, used by Azure
Service Bus and Qpid) does create, read, update and delete on typed entities through a
`$management` node: the "typed resources significant to the client" Fielding's rules forbid.

**REST-like work on other messaging, and hypermedia across protocols.**
- XEP-0332, HTTP over XMPP: deferred, not recommended for production.
- Zest, REST over ZeroMQ (Cambridge and Nottingham, 2019), inspired by CoAP: RESTful in style,
  with nothing found on hypermedia.
- The QEST broker, bridging MQTT and REST.
- Rohit Khare's dissertation (UC Irvine, 2003), "ARRESTED": REST extended with asynchronous
  events and publish/subscribe. Academic precedent for change notices.
- W3C Web of Things Thing Descriptions: the closest thing to mixing protocols in one document.
  Hypermedia forms whose `href` may be any scheme (http, coap, mqtt, modbus), with a vocabulary
  per protocol binding. No AMQP binding found, and none of HEART's core: acts offered by state
  with reasons for the absent ones, conditional acts with validators, refusals by meaning.

**What HEART would add.** A native binding, not a tunnel, of a full hypermedia core onto AMQP:
the resource's URI in every message, forms targeting `amqps://` URIs, conditional acts with
validators, refusals by meaning, documents mixing https, coaps and amqps, and the same script
running unchanged over all of them.

**Is it worth doing?** Yes, as a bounded experiment once plan steps 2 and 3 are done.
- For: the first concrete evidence of Fielding's first rule on a messaging protocol, beside a
  request/response one (CoAP) we already have, demonstrable and publishable; practical value,
  since enterprises already run brokers, and durable acts, late answers and change notices are
  real needs; and prior art lowers the cost, since Dispatch's subject convention can be copied.
- Against: narrow demand; a broker to run; no shared caches; request and reply through a broker
  add latency. Some will say "just tunnel HTTP". The answer: a tunnel carries HTTP, a binding
  carries HEART; only a binding gives a whole validator, mixed schemes per form, and notices as
  a native feature.

**Sources**
- OASIS, "HTTP Semantics and Content over AMQP" starter document request: https://issues.oasis-open.org/browse/TCADMIN-3282
- OASIS AMQP mailing list: https://lists.oasis-open.org/archives/amqp/201903/msg00000.html
- OASIS AMQP Bindings and Mappings TC charter: https://www.oasis-open.org/committees/amqp-bindmap/charter.php
- Qpid Dispatch 1.17.0 release notes (DISPATCH-2212): https://qpid.apache.org/releases/qpid-dispatch-1.17.0/release-notes.html
- Qpid Dispatch 1.16.0 release notes: https://qpid.apache.org/releases/qpid-dispatch-1.16.0/release-notes.html
- Red Hat Service Interconnect, supported protocols: https://docs.redhat.com/en/documentation/red_hat_service_interconnect/1.8/html/using_service_interconnect/protocols
- alanconway/envoy-amqp: https://github.com/alanconway/envoy-amqp
- CVE-2026-61837, RabbitMQ HTTP-over-AMQP management: https://www.tenable.com/cve/CVE-2026-61837
- RabbitMQ AMQP 1.0: https://www.rabbitmq.com/docs/amqp
- Azure Service Bus, AMQP request/response operations: https://learn.microsoft.com/azure/service-bus-messaging/service-bus-amqp-request-response
- Qpid Dispatch qdmanage (AMQP Management): https://qpid.apache.org/releases/qpid-dispatch-1.17.0/man/qdmanage.html
- XEP-0332, HTTP over XMPP transport: https://xmpp.org/extensions/xep-0332.html
- Zest, REST over ZeroMQ: https://arxiv.org/abs/1902.07009
- QEST broker: https://www.researchgate.net/publication/261348765_Introducing_the_QEST_broker_scaling_the_IoT_by_bridging_MQTT_and_REST
- Rohit Khare, Extending REST for Decentralized Systems: https://isr.uci.edu/node/494.html
- W3C WoT Binding Templates: https://w3c.github.io/wot-binding-templates/publication/note3/Overview.html
- W3C WoT Binding Registry (draft): https://www.w3.org/TR/2025/DRY-wot-binding-registry-20251104
- RabbitMQ URI specification: https://www.rabbitmq.com/docs/uri-spec
- Fielding, "REST APIs must be hypertext-driven": https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven

## What it gives up, and questions

- No shared caches between client and server, as HTTP has; only the client's own cache, kept
  fresh with validators.
- Credentials: per-connection (SASL) or per-message? How does the front door know the caller?
- Addressing: one request queue per service, or per resource? How does `amqp://host/path`
  name a resource when brokers address nodes, not paths?
- Timeouts and lost replies: what a client concludes when no reply arrives, and whether an
  unsafe act may be retried (an idempotency key, as some HTTP APIs use).
