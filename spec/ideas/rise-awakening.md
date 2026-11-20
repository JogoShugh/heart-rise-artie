# Idea: RISE, the awakening

**Status:** an exploration, 2026-11-05. Nothing here is built, and nothing here changes SPEC §7 yet. It is for
thinking with, before the work is prioritized.

## The picture

A server at REST waits. HEART gives it a heartbeat: every answer carries its state, the acts that state allows,
and a version that guards each act, so the body keeps one consistent rhythm. RISE is the moment the server wakes
up and reaches out. The server stops waiting and becomes a client of someone else, very often of whoever asked it
for the work in the first place.

What makes the awakening safe and simple is the oldest device in business: the self-addressed stamped envelope.
Whoever asks for work hands over, with the request, everything needed to send the answer back:

- **where** it goes;
- **what shape** it must take;
- **the postage**: proof that the sender may use that address.

The recipient doesn't keep a list of its callers' addresses, queue names or reply formats. It keeps the envelope.

SPEC §7.1 already says it: client and server are roles, not identities. This note follows that idea all the way
through.

## The envelope is very old

| World | The envelope | What it teaches |
|---|---|---|
| Post | The self-addressed stamped envelope; business reply mail, its postage prepaid by permit | The asker pays and addresses; the answerer only fills in and drops it in a box |
| Email | `Reply-To` (RFC 5322); `Disposition-Notification-To`, read receipts (RFC 8098) | The return address travels in the message, apart from the sender's identity |
| EDI, AS2 (RFC 4130) | `Receipt-Delivery-Option`: the URL to send the signed receipt (MDN) to, later | Asynchronous replies between businesses, routine since the early 2000s |
| JMS | `JMSReplyTo`, `JMSCorrelationID` | The reply destination and the thread tying it to the request |
| AMQP 1.0 | `reply-to`, `message-id`, `correlation-id`, `absolute-expiry-time` | The same, built into every message; the request says how long it is worth answering |
| MQTT 5 | Response Topic, Correlation Data | Even the smallest devices carry a return address |
| NATS | A reply subject (an "inbox") on every request | An address made for one exchange is also its correlation |
| Enterprise Integration Patterns | Return Address; Correlation Identifier | Named in 2003, as patterns to keep services from knowing each other |
| SOAP, WS-Addressing | `wsa:ReplyTo`, `wsa:FaultTo`, `wsa:MessageID`, `wsa:RelatesTo` | The right idea, buried under weight; a lesson in what not to make mandatory |
| HTTP | `Prefer: respond-async` (RFC 7240), `202 Accepted` with a `Location` to watch | Waiting for an answer that comes later is already part of HTTP |
| Webhooks; OpenAPI callbacks | A callback URL in the request, its shape described in the *recipient's* document | Here the recipient defines the reply's shape, the opposite of the envelope |
| WebSub (W3C) | `hub.callback`, with a check that the callback really wants the messages | Never deliver to an address you were handed without checking it first |
| AsyncAPI 3 | A reply's address read from the message at run time (`$message.header#/replyTo`) | The return address belongs to the message, not to the description |
| ActivityPub | Every actor has an inbox; servers deliver to it, signed | An inbox is a resource like any other |

RISE invents none of this. The one thing it adds is that the envelope is hypermedia.

## What RISE adds: the envelope is a link to a resource

In every system above, the return address is a string, and the reply's shape is agreed beforehand, out of band.
In RISE, the return address is a **link to a resource that answers like any HEART resource**. The reply's shape
is the **form** that resource offers.

- **The smallest envelope is a single link.** The recipient wakes up holding one URI, the bookmark of the answer.
  Fielding's rule for entering a REST API applies to the recipient too: "no prior knowledge beyond the initial
  URI (bookmark)".
- **Reading it tells the recipient everything.** It reads the reply resource and finds its state (still
  awaiting?), the forms it offers now (deliver, report progress, report failure), each with its schema, and a
  version to send back with `If-Match`.
- **Two optional additions save work:**
  - the reply resource's representation, embedded in the envelope, which saves the read, as HAL's `_embedded`
    does;
  - an L2 link to its form set, with integrity, which a recipient answering thousands of requests fetches once
    and caches.
- **The template fields go away.** The template of §7.2 (`template-rel`, `template-schema`, `template-fields`) is
  simply the schema of the reply form. The originator is the server of its own inbound side, so it stays in
  charge of what it accepts.

The library's next scene shows it. Ana asks her assistant to have *The Hobbit* renewed as soon as the hold on it
clears. The assistant can't keep watching, so it hands the library an envelope:

```
POST /loans/3310/actions/tell-me-when-renewable
If-Match: "seq-1.hsf.L2.…"
Content-Type: application/json

{ "_rise": { "_links": {
    "heart:reply-to":    { "href": "https://assistant.example/replies/7f3a" },
    "heart:error-reply": { "href": "https://assistant.example/replies/7f3a" } },
  "expires": "2026-12-31T00:00:00Z" } }

202 Accepted
Location: /loans/3310/watches/41
```

Three weeks later, the hold clears, and the library wakes up as a client:

```
GET https://assistant.example/replies/7f3a          (verify, and read what it offers now)

200  { "state": "awaiting", "asked": "/loans/3310/actions/tell-me-when-renewable",
       "_forms": { "heart:reply-to": { "method": "POST", "target": …,
                   "schema": { "required": ["loan", "standing"], … } } } }

POST https://assistant.example/replies/7f3a/actions/deliver    If-Match: "…"
{ "loan": "/loans/3310", "standing": "renewable" }

200  { "state": "answered", … }       (and from now on, nothing is offered)
```

In 1997 terms, this is the library mailing a postcard that says "your book can be renewed now", written on the
card Ana left at the desk.

## Two resources, facing each other

One RISE exchange is two HEART resources, each owned by one party and each offering what its state allows:

| | Owned by | Created when | What it offers, by state |
|---|---|---|---|
| **The task** | the recipient | it answers `202`, at `Location` | working: cancel, if still possible · done: `heart:result` · cancelled, failed: nothing, with the reason |
| **The reply** | the originator | before sending the request | awaiting: deliver, progress, fail · answered: nothing ("It was answered at 10:42") · cancelled: nothing ("No longer needed") · expired: nothing |

What follows from that is the useful part:

- **Exactly once, from at least once.** The recipient may retry delivery as often as the network demands.
  - A second delivery reaches a reply that has already been answered. It is refused (409, with the reason), or the
    recipient's read shows there is nothing left to do.
  - No idempotency keys are needed: the state is the key, as the 412 and the 409 already are.
- **Progress is just an act.** While the reply is awaiting, `progress` stays offered.
- **Cancelling works from either side.**
  - The originator cancels its reply, and the recipient sees it on its next read.
  - The recipient's task offers cancel for as long as cancelling is possible, and the form disappears once it
    isn't.
- **Late answers explain themselves.** An expired reply offers nothing and says why, and the recipient logs it,
  rather than parsing a 404.

## Channels: any URI with a binding

SPEC §6.0 already says how a link chooses its transport: by its scheme, through the binding registered for it,
and a URI with no binding is refused before anything is sent. The envelope's links follow the same rule.

| The reply's link | How the answer travels |
|---|---|
| `https://assistant.example/replies/7f3a` | HTTP, as above |
| `amqps://broker.example/assistant/replies/7f3a` | AMQP 1.0, as in `amqp-binding-vision.md`: the method in `subject`, the reply resource in `to`, the request's `message-id` as `correlation-id`, the envelope's `expires` as `absolute-expiry-time` |
| `coap://greenhouse.local/replies/7f3a` | CoAP, for a device that can't host HTTP |
| `https://inbound.acme.example/inbox/7f3a` | An inbound gateway the originator owns, in front of a service behind a firewall or serverless; the gateway holds the reply resource |
| no link at all | Poll mode (§7.7): the answer stays at the recipient, in the task, and the originator reads it there |

- **Push, poll and gateway are one idea.** In every case the answer ends up in a resource. The only difference is
  who owns that resource: the originator (push), the recipient (poll), or a gateway acting for the originator.
- **Mixed channels are allowed.** An envelope may send progress over AMQP and the final answer over HTTPS, since
  every link carries its own scheme.
- **An unknown scheme is refused at once.** A recipient that can't deliver to a scheme says so synchronously:
  422, at `/_rise/_links/heart:reply-to`, "This service can't deliver to amqps." It may also list the schemes it
  delivers to, as `hr:options` on the envelope field of its own form.

## Security, compliance and controls

A server that sends to addresses handed to it by callers is an SSRF hazard and a spam cannon, unless the
envelope comes with controls. Most of them come straight from HEART.

- **Read before you send.** The recipient never posts to a handed address cold. It reads it first, and it
  delivers only if the address answers as a HEART resource awaiting a reply to *this* request. This is WebSub's
  check of intent, and it costs nothing extra, because a HEART client reads before it acts anyway.
- **The stamp.** The postage is a reply capability: something that lets the recipient write to this one reply
  resource and to nothing else. Three forms, from simplest to strongest:
  - an address no one can guess, which acts as the capability;
  - a token in the envelope, valid only on that reply and only until it expires;
  - signed requests (HTTP Message Signatures, RFC 9421), so the originator knows *who* answered.
- **Controls of your own, in front.** An originator's inbound gateway applies its own policy: which recipients
  may answer, size limits, and schema validation (422). This is the "own controls" frame turned around, applied
  to the way back in.
- **Data minimization.** The reply form's schema is the whole contract. The recipient sends what the form asks
  for and never its internal model (§7.4).
- **The record.** The reply resource is the audit trail: who answered, when, under which envelope, with which
  `traceparent`.

## Evolvability: why neither side bakes anything in

| Today | With RISE |
|---|---|
| Each partner registers a webhook URL, stored in the recipient's configuration | The address arrives with each request; nothing is registered |
| Queue names and exchanges agreed per integration and baked into both sides | A link of any scheme, per request |
| The callback's shape defined by the recipient (OpenAPI callbacks), or by a document both must track | The shape is the originator's reply form, read when replying, content-addressed and cached |
| Moving the inbox means a coordinated change | The originator moves it; the next envelope says where it is |
| A late or duplicate callback is the receiver's problem to detect | The reply resource's state refuses it, with a reason |

The recipient's code knows exactly one thing: read the link it was handed, and act on what that link offers. It
is the same loop ARTIE runs, and the same one a person runs in a browser.

## What this changes in SPEC §7

| §7 today | This exploration |
|---|---|
| 7.1 Roles, not identities | Keep, and make it the headline |
| 7.2 Flat envelope fields: `reply-to`, `callback-mode`, `template-rel`, `template-schema`, `template-fields` | Replace them with links: `heart:reply-to`, `heart:error-reply`, `heart:progress`, optionally an embedded representation and an `hr:forms` link. The template is the reply form's schema. The callback mode follows from whether there is a reply link |
| 7.2 The binding: a body member, `_rise` | Still open. A body member is validated by the act's own schema; a request `Link` header (RFC 8288) works with any body, and maps directly onto AMQP's `reply-to` and MQTT's Response Topic |
| 7.3, 7.4: 202, then do the work | Keep, and add "read before you send" and the stamp |
| 7.5 The relation vocabulary | Keep `heart:reply-to`, `heart:error-reply`, `heart:progress`, `heart:result` |
| 7.6 Fallback, the agent directory | Defer: it is a separate problem, choosing who does the work |
| 7.7 Push and poll | Keep, and add the gateway as a third owner of the reply |
| 7.8 Shared caching | Keep; the reply form sets are content-addressed like any other |

## Where ARTIE, the library and the deck come in

- **The library** gets one act that takes an envelope, such as "tell me when this loan can be renewed", and a
  simulator control that clears a hold, so that it wakes up and delivers.
- **ARTIE as the originator.** The scripts plan already names `act(…) awaiting { }`.
  - Poll mode works with today's ARTIE: it reads the task until `heart:result` is offered.
  - Push mode needs somewhere to receive the answer: the ARTIE daemon idea, or a small inbox behind the gateway.
- **ARTIE as the recipient.** A kept script that answers envelopes is just a script whose bookmark arrives in
  the request.
- **Listeners** (`exhibits/listeners-live`) poll GitHub today. The gateway could turn GitHub's webhooks into RISE
  deliveries, so that a watch wakes up instead of looking.
- **The deck:** a scene seven, "The library calls back", with its 1997 counterpart: a postcard, addressed in
  Ana's own hand.

## Second pass: merit, caching, location, templates

Written after the author asked whether this is SOAP again.

### Merit, and the death star to avoid

SOAP and WS-* grew into a death star for four reasons:

- an envelope around every message, with headers stacked on it (Addressing, ReliableMessaging, Security,
  Coordination);
- every operation tunnelled through POST to one endpoint, so HTTP's methods, status codes and caching did no work;
- code generated from WSDL on both sides, which welded them together;
- an orchestration engine in the middle.

RISE, kept as it is here, adds one link, uses HTTP as written (GET, POST, ETag, 202, 409, 412, 422, caching), and
makes the reply an ordinary HEART resource. It is choreography, not orchestration: each party acts on what it was
handed. It scales the way webhooks already scale at GitHub and Stripe, with reply shapes that are content-addressed
and shared.

The death star returns only if the spec mandates any of these:

- reliable-messaging machinery;
- distributed transactions or sagas;
- §7.6's fallback agent directory;
- a fixed security-token format;
- several callback modes as protocol.

The rule that keeps it out: **anything that can be a resource with forms does not become a keyword.**

### Caching at wake-up: confirmed

A reply form set at L2 is immutable and named by its own digest.

- **On receipt.** The recipient fetches the set and checks its integrity when the request arrives, not when the
  work is done. If it can't produce what the schema asks for, it refuses then and there (422), not three weeks
  later.
- **At wake-up.** The set is a local cache hit. An egress caching proxy can share it across the recipient's whole
  fleet, as a reverse proxy shares sets among clients.
- **On the way out.** The only trip across the network is the POST to the originator.
- **The version tag.** A recipient that doesn't read the reply first has no version to send with `If-Match`. The
  envelope can carry the reply resource's version. If the originator has cancelled since, the POST gets a 412, and
  only then does the recipient read. The happy path stays a single trip.

### A changed location

Any change to a set's bytes gives it a new digest automatically. Nothing needs invalidating: the old entry is
simply no longer referenced.

The per-request reply address therefore stays **out** of the set:

- **A templated target.** The set's target is a template such as `{reply}/actions/deliver`, filled from the
  envelope, as `hr:templatePointers` fills `{id}` from a loan today.
- **Moving the inbox is data.** It is a new envelope with the same digest, and the cache stays warm.
- **Changing the shape is a material change.** A different path pattern, method or schema is a new digest and one
  cache miss.

This is the same split as the leniency already built: ARTIE pins what a form does, not where it points.

### Templates are forms

Once forms are sent, the template already exists: it is the form's schema, with types, required fields, titles and
validation. A text blob with placeholders is a weaker form, with untyped slots whose meaning lives out of band, so
it is better left out.

The hard part is the mapping: turning the recipient's own result into the originator's fields. There are two
in-band answers:

1. **A shared vocabulary.** Fields are named by profile relations. The recipient maps its data to the vocabulary
   once, and every originator using those relations works. This is the shared-vocabulary work parked in
   FIELDING-REVIEW.
2. **A model once, kept by digest.** The first time a recipient sees a set's digest, a model maps its result onto
   that schema, and the mapping is kept under the digest.
   - Same digest means same schema, so the kept mapping is valid by construction and no model is called again.
   - A new digest means one repair.
   - This is ARTIE's "find the way once, keep it", with the content address as the key: GenAI at the edge.

Start with the second, which needs no prior agreement between parties. The first makes it cheaper as relations
come to be shared.

## Many consumers of one event

The question: in event- or message-based integration, one service publishes and N services consume. Does that
work in a HEART and RISE world, or is out-of-band coupling built into it?

### Where pub/sub hides its coupling

Pub/sub decouples *who*: the publisher doesn't know its consumers. It doesn't decouple *what*. Every consumer
knows these things out of band:

| What the consumer must know | How it usually knows |
|---|---|
| Where to listen: the broker, the topic or stream name | Configuration, a wiki page |
| What the events mean: "LoanRenewed" | A schema registry, naming conventions |
| The payload's shape, and how it may evolve | Avro or JSON Schema in a registry, compatibility rules |
| What it may do in response | Another API, known separately |
| Order and duplicates | Assumptions, partition keys, idempotency code in every consumer |

A fat event is the Great Divorce again: information with no controls. A consumer that wants to act has to know,
out of band, which API to call and what is allowed now.

### The HEART answer: the event is a hint, the resource is the truth

PLAN-LISTENERS already settled it: **events are hints, never truth.** A hint says that a resource may have changed.
The consumer reads the resource and acts only on what it offers now, with its validator. Applied to N consumers,
that removes most of the table:

- **A thin event.** The event carries a link to the resource, the resource's version after the change, and the
  kind of change as a relation URI. It doesn't carry the state, and it doesn't need a schema registry.
- **The read.** Each consumer reads the resource and gets HEART's state and the controls that state allows, with
  shared form sets by digest. What it can do in response is in that answer, not in another API.
- **Order and duplicates stop mattering.** A late or repeated hint leads to a read of the *current* state. A
  consumer that already holds that version gets a 304. Idempotency comes from the version, not from code in every
  consumer.
- **N reads, one fetch from the origin.** N consumers read the same version of the same resource. A shared cache,
  with request collapsing, turns that into one fetch from the publisher. The version in the event lets each reader
  ask conditionally.
- **The fat-event option stays, as an optimization.** A publisher may embed the representation, as HAL's
  `_embedded` does. Its forms stay linked by digest, so even a fat event carries no copied contract.

### What can't be removed, and how small it gets

Some coupling is inherent, and Fielding accepts it: the media type and the meaning of relations. The aim is that
nothing else is shared out of band.

| Coupling | In a HEART and RISE world |
|---|---|
| Where to listen | A link. A collection or resource links its event stream (an `hr:events`-style relation); the consumer finds it from the bookmark, like any link |
| How to subscribe | A form. "Subscribe" is an act the stream offers, its kinds of event an `hr:options` choice from a list resource, the same mechanism as the problem kinds |
| What an event means | A relation URI in the publisher's profile (ALPS): `library:renewed`, `library:hold-cleared`. Dereferenceable, so in band; shared vocabulary is the irreducible part |
| The event envelope | An existing standard, not a new one: CloudEvents. `type` is the relation URI, `subject` or `dataref` the resource's link, an extension carries the version |
| What the consumer may do next | The forms on the resource it reads |
| Order and duplicates | The version, plus conditional reads |

### Two delivery shapes, both HEART

1. **Push to subscribers, through a standing envelope.** Subscribing hands the publisher an envelope, as in RISE,
   but a standing one: the consumer's inbox is a resource that accepts many deliveries.
   - **States act as backpressure.** The inbox's states are active, paused and cancelled. A paused inbox offers
     no deliver form, so the publisher's next delivery gets a 409 and stops. The consumer controls the rate by
     changing its own state.
   - **Each subscriber can ask for its own shape.** The publisher's mapping is kept by the digest of each
     subscriber's form set, so a thousand subscribers on the same set cost one mapping.
   - **The cost** is N deliveries per event. That is fine for tens or hundreds of subscribers, which is webhooks'
     territory.
2. **A feed, read by many.** The stream is itself a paged HEART resource, as Atom feeds were for event-driven
   REST (*REST in Practice*).
   - **Archived pages never change.** They are named by content and cached for good.
   - **Only the head page is Layer 1.** It is read with `If-None-Match`, mostly getting 304s.
   - **Fan-out is the cache's job.** A thousand consumers polling cost the publisher close to one, because caches
     absorb them.
   - **Latency.** A push or broker nudge can carry just "the head moved", for consumers that want it sooner. The
     nudge is a hint; the feed is the truth.
   - **A broker** fits here as a carrier of hints, never as the system of record for the meaning of events.

### Verdict

It is realistic, and it is less coupled than pub/sub as usually practised. What remains shared:

- the bookmark;
- the media type (HAL Schema Forms, CloudEvents);
- the publisher's relation vocabulary, which is in band because each relation is a URI with its meaning behind it.

Topic names, payload schemas, the API for responding, and the ordering rules all come from links, forms and
versions read at run time. The price is one read per consumer per change, and caching and request collapsing make
that close to free at the origin.

## Open questions

1. **What the envelope must hold.** Is it a link only, with everything else optional (as proposed here)? Or
   must it embed the reply resource's representation, to save the read?
2. **Where the envelope travels.** A body member (`_rise`), a request `Link` header, or either one?
3. **One reply resource per request, or a shared inbox with a correlation id?** One per request is simpler to
   reason about and is its own correlation; a shared inbox is cheaper for very high volumes.
4. **The stamp.** Which of the three is required, and which allowed?
5. **Must the recipient read first?** Is "read before you send" a SHALL, or a SHOULD with a capability URL?
6. **MCP Tasks and AsyncAPI.** Is RISE's poll mode a profile over MCP Tasks? Can an AsyncAPI document describe a
   RISE exchange with no new keywords?
7. **Retries and timing.** Backoff, `Retry-After`, and how long a recipient keeps trying before it gives up and
   reports the failure to `heart:error-reply`.

## Thin slices, in an order to choose from

1. **Spec only.** Rewrite §7.2 to §7.4 around "the envelope is a bookmark" and the reply resource's states. No
   code.
2. **heart-core: a reply resource.** A helper to declare an inbox type, with the states awaiting, answered,
   cancelled and expired and the forms each offers. Feature first.
3. **The library wakes up.** In heart-sims, "tell me when renewable" takes an envelope, and a simulator control
   clears the hold. HTTPS only.
4. **ARTIE, poll mode.** `act(…) awaiting { }` against the task's `heart:result`. No inbox needed.
5. **ARTIE, push mode.** A small inbox, in the daemon or behind the gateway.
6. **AMQP.** A reply link with an `amqps` scheme, on a real broker.
7. **Controls.** Read before you send, the reply token, and signatures.
