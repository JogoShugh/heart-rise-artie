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
