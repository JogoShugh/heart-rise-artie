# Idea: RISE, a deep dive before the prototype

**Status:** an exploration, 2026-11-07, for the author to prioritize. Nothing here is built, and SPEC §7 is unchanged.
It reviews §7, `rise-awakening.md` and the fictional "Rest, then RISE" talk, then works through four questions the
author raised: clients sending hypermedia, self-registering subscribers, the DDD context-map patterns that come with
publish and subscribe, and whether RISE needs HAL-FORMS `_templates`. It ends with the smallest working prototype,
over a dynamic webhook and over AMQP.

## Where things stand

- **SPEC §7** is on hold since 2026-10-09. It has a flat five-field envelope (`reply-to`, `callback-mode`,
  `template-rel`, `template-schema`, `template-fields`), push and poll modes, a fallback agent directory, and two
  `[0.5]` proposals: RISE requires L2 at the server, and the envelope travels as a `_rise` body member.
- **`spec/ideas/rise-awakening.md`** (2026-11-05) replaces the five fields with one link to a reply resource, makes
  one exchange two HEART resources facing each other, adds "read before you send" and the stamp, and sketches many
  consumers as thin events plus reads.
- **The talk** (artifact "Rest, then RISE", fictional) has Doca build that, with three channels: HTTPS, AMQP and
  Server-Sent Events.
- **What is built that RISE can stand on:**
  - acts that bring a resource into being (201 with `Location`);
  - offers by state, with reasons for absent acts;
  - `If-Match` with 409, 412 and 422;
  - `hr:options`, choices drawn from state or from a link;
  - the CoAP binding, which shows how ARTIE picks a transport by a link's scheme;
  - the gateway's hub (`Prefer: wait=N`), a read that comes back early when a webhook names its resource
    (PLAN-LISTENERS, slice 6);
  - the simulators (library, deploy) served over http4k.
- **What is not built:** any RISE code, any AMQP code, any inbox or subscription resource.
- **On this machine:** Docker 27.4 with the images `rabbitmq:3.13-management` and `rabbitmq:3-management-alpine`.

## 1. Review of SPEC §7 as drafted

| § | Says | Problem | Keep, change, drop |
|---|---|---|---|
| 7.1 | Client and server are roles, not identities | None. It is the whole idea | Keep; make it the headline |
| 7.2 | Envelope of five flat strings | The recipient must build a reply with no form to follow: the shape is described in strings, not offered as a control. RISE breaks HEART's own rule on its way back | Change: one link to a reply resource (rise-awakening) |
| 7.2 | `callback-mode` | Follows from whether there is a reply link | Drop |
| 7.2 | `template-rel`, `template-schema`, `template-fields` | The template is the reply form's schema. "Template" also collides with HAL-FORMS `_templates`, and invites the question in §4 below | Drop the words; the form carries it |
| 7.2 | Requires L2 at the server | Still right for content-addressed form sets; L0 recipients can still read a reply resource | Keep, scoped to the reply's server |
| 7.2 | `_rise` body member | Fine, and the act's own schema can declare it, which makes "this act takes an envelope" discoverable | Keep |
| 7.4 | 202, echo `reply-to` in the body | Echoing the address adds nothing. A `202` needs new support in heart-core, while a 201 bringing a task resource into being is already HEART | Change: 201 with the task at `Location` first; 202 later if wanted |
| 7.4 | Poll mode: "post the result to the polling resource" | The recipient doesn't post to its own resource: the task's state becomes done, and it then offers `hr:result` | Change the wording |
| 7.4 | Never expose the internal model | Right; the reply form is the whole contract | Keep |
| 7.6 | Fallback agent directory | A different problem: choosing who does the work. It is the first step toward an orchestration engine | Drop from RISE; maybe an idea of its own |
| 7.7 | Push and poll | Both are one idea: the answer ends up in a resource, owned by the originator (push), the recipient (poll), or a gateway | Keep; add the gateway |
| — | Security | Missing: an address handed over by a caller is an SSRF and spam hazard | Add: read before you send, the stamp, scheme allowlist, expiry |
| — | Expiry, cancelling, progress, duplicates | Missing | Add, as the reply resource's states |
| — | Clients sending hypermedia | Implied, never said | Add, as the principle of §2 below |

**Verdict.** §7.1 and the push and poll idea hold up. Everything in §7.2 is the part to replace, and rise-awakening
already says with what. The spec rewrite is small once the prototype has shown it works.

## 2. Clients sending hypermedia

Raised by the author during this review: RISE is the place where the **client sends hypermedia to the server**, not
only the other way round. §7 never says so plainly. It should be RISE's second principle, after "roles, not
identities".

### What it means

In HEART the server sends hypermedia and the client sends data: field values, filled in from a form the server
offered. In RISE the originator also sends controls: a link, or a whole representation with forms, that the
recipient will follow later, as a client. The originator is a server of its own inbound side, and the hypermedia it
sends is how it tells the recipient what it will accept.

That is the reciprocity in the name. Each party is driven by hypermedia the other one authored.

### By reference and by value

| | By reference | By value |
|---|---|---|
| What travels | One link: `"hr:reply-to": { "href": "…/replies/7f3a" }` | The reply resource's representation, its state and `_forms`, embedded in the envelope (HAL `_embedded`) |
| What the recipient does | Reads the link, gets state, forms and a validator, then acts | Uses the embedded forms at once; reads only if an act comes back 412 |
| Fresh? | Always: read at wake-up | As fresh as the envelope; the validator catches the rest |
| Cost | One read per reply | None on the happy path |
| Trust | The recipient trusts the address's answer | It trusts the requester's copy, so the embedded document must carry its `self` link, and its forms linked by digest (L2) with integrity |

By reference is the one to build first. By value is an optimization, and the `self` link plus the validator keep it
honest.

### Prior art: the web already does this

- OAuth's `redirect_uri`, and the `return_url` and `cancel_url` of payment flows: a client hands the server a link
  for later.
- WebSub's `hub.callback`, Webmention's `source` and `target`, ActivityPub's inbox.
- Email's `Reply-To`, AMQP's `reply-to`, MQTT 5's Response Topic (the table in rise-awakening).

None of them sends forms. RISE's addition is that the link leads to a resource that offers forms, with state and a
validator.

### What makes it safe and in band

1. **The act says it takes an envelope.** Its schema has a `_rise` property referring to one shared, content-addressed
   envelope schema. ARTIE sees from the form that the act can answer later; nothing out of band.
2. **Relations, not keys.** The envelope's links are named by relation (`hr:reply-to`, `hr:error-reply`,
   `hr:progress`), defined in the HEART profile.
3. **Untrusted input.** The recipient treats handed hypermedia as a browser treats a page:
   - schemes it delivers to are listed (as `hr:options` on the envelope field), anything else is a 422 at once;
   - no private address ranges unless configured;
   - size limits;
   - an expiry on every envelope.
4. **Read before you send.** It delivers only if the link answers as a reply awaiting *this* request.
5. **No configuration on the receiving side.** Everything the recipient needs to answer arrived in the request, and
   is kept as the task's state.

The last point is the bridge to self-registration: configuration is knowledge baked in before the system runs;
state is knowledge handed over while it runs. Client-sent hypermedia is how subscriber knowledge moves from the
first to the second.

## 3. One publisher, many subscribers, no configuration

The question: one server, many subscribers. Subscribers register themselves; the server's configuration knows none of
them. How does that look in HEART?

### The HEART answer

- **The topic is a resource.** Anything worth subscribing to links its events: a collection or a single resource
  (`hr:events`), found from the bookmark like any link.
- **Subscribing is an act the topic offers.** Not a create on a `/subscriptions` collection: the topic offers
  `subscribe`, its form asks for the kinds of event (an `hr:options` choice from the profile's event relations) and
  an envelope: the subscriber's inbox link. The answer is 201, the subscription at `Location`.
- **The subscription is a resource the subscriber controls:**

  | State | Offers | Why the rest is absent |
  |---|---|---|
  | pending | cancel | "Waiting to check the inbox answers" |
  | active | pause, change kinds, renew, cancel | — |
  | paused | resume, cancel | "Paused at 10:42" |
  | expired | renew | "The lease ended on 1 December" |
  | cancelled | nothing | "Cancelled at 11:03" |

- **The publisher checks before the first delivery.** It reads the inbox link and activates only if it answers as an
  inbox awaiting deliveries (WebSub's check of intent, and "read before you send").
- **Leases.** A subscription expires unless renewed, so a vanished subscriber stops costing anything. The server's
  configuration holds only policy: allowed schemes, how many subscriptions per caller, rates.
- **Thin events.** A delivery carries a link to the resource that changed, its version after the change, and the kind
  of change as a relation URI (CloudEvents fields: `subject` or `dataref`, `type`, an extension for the version).
  The subscriber reads the resource for the truth and for what it may do next. A late or repeated event leads to a
  read; a version already held gets a 304.
- **The inbox is the subscriber's resource.** It offers `deliver` while active and nothing while paused, so a paused
  inbox is backpressure: the publisher's next delivery gets a 409 and backs off.
- **One detail that matters at volume.** If every delivery changed the inbox's validator, parallel deliveries would
  412 each other. The inbox's validator covers its control state (active or paused) only; the deliveries go into a
  list under it. Then `If-Match` guards "is this inbox still taking deliveries", not "has anything arrived since".

### The variations

Who holds the list of subscribers decides most of it.

| | Who holds the list | How a subscriber registers | Delivery | The publisher knows | Fits |
|---|---|---|---|---|---|
| **A. Subscriptions as resources** (above) | The publisher, as state | The `subscribe` act, with its inbox link | Push: the publisher acts on each inbox | Each subscription, as state it was handed | Tens to hundreds of subscribers; webhooks' territory |
| **B. A broker's topology** | The broker | The subscriber binds its own queue to the publisher's exchange, found by link: `"hr:events": { "href": "amqp://broker/exchanges/library.loans" }` | The broker fans out | Nothing at all | Many subscribers, already on a broker |
| **C. Publisher-made queues** | The publisher, as state; the broker as the pipe | The `subscribe` act; the publisher makes and binds a queue for the subscriber and answers its address in the subscription's state | The subscriber consumes its queue | Each subscription; it holds the broker credentials | A broker, without giving subscribers rights on it |
| **D. A feed** | Nobody | None: read the feed | Pull: the head page with `If-None-Match`, or `Prefer: wait` as the gateway's hub does now | Nothing | Any number; caches absorb the reads |
| **E. A hub** | A hub between them (the gateway) | `subscribe` at the hub | The hub pushes, or holds reads | Only the hub | Many publishers that shouldn't each run subscriptions |

These combine. The likely shape is D always (the truth, readable by anyone), with A or B for those who want to be
told sooner. Whatever is pushed is a hint; the feed and the resources are the truth.

### Through a DDD context map

Each variation puts the two sides in a different relationship. The patterns are Evans's and Vernon's.

| Pattern | What it means | Where it appears |
|---|---|---|
| **Open Host Service** | The upstream offers one open protocol for everyone, instead of an integration per consumer | The publisher's topic resource, its `subscribe` act and its feed. HEART's resources are open host services by construction |
| **Published Language** | A documented, shared language for the exchange | The profile's relations (ALPS), the event kinds as relation URIs, CloudEvents as the envelope of an event. In band, because each relation is a URI with its meaning behind it |
| **Conformist** | The downstream takes the upstream's model as it is | A subscriber that reads the publisher's resources and uses its relations. Thin events make this the default, and cheap |
| **Anticorruption Layer** | The downstream translates the upstream's model into its own | A subscriber that maps what it reads into its own model; ARTIE's "decide once, keep it" is an ACL that a model writes once |
| **Customer/Supplier** | The downstream's needs shape what the upstream provides | RISE's request and reply: the originator's reply form decides the reply's shape, and the recipient maps its result onto it. The reverse of OpenAPI callbacks, where the supplier dictates |
| **Shared Kernel** | Two teams share part of a model | What a schema registry quietly becomes. HEART's form sets by digest are published language, not a shared kernel: nobody edits them together, each side reads what the other serves |
| **Partnership** | Two teams evolve together | Avoided: nothing is negotiated beyond the relations |
| **Separate Ways** | No integration | Always available: stop subscribing |

The useful conclusion is that publishing and replying want different patterns:

- **Broadcast (one to many): Open Host Service with a Published Language.** The publisher's events are thin and in
  its own language; each subscriber conforms or keeps its own ACL. If each subscriber dictated its own delivery shape,
  the publisher would become a conformist to N languages: affordable for a few important customers, ruinous for many.
- **Request and reply (RISE): Customer/Supplier, with the customer in charge of the reply.** The originator asked;
  its reply form is its own language; the recipient maps once per form digest.

### Where AMQP fits

- **As the carrier of hints (B, C).** The broker fans out thin events; the resources stay the truth. Topic names stop
  living in wikis because the exchange is a link in the topic's representation.
- **As a binding of HEART itself (`amqp-binding-vision.md`).** Any request, read or act, travels as a message: the
  method and the status in the message, `reply-to` and `correlation-id` for the answer. Then a reply resource can live
  behind a queue, and a RISE delivery over AMQP is just an act whose target has an `amqp` scheme.

These are different jobs. The prototype needs the second for a reply link with an `amqp` scheme; publish and
subscribe needs only the first.

## 4. HAL-FORMS `_templates` in RISE?

**Not needed as a RISE feature.** A reply resource is an ordinary HEART resource, so it already speaks both dialects
by content negotiation, as every HEART server does. A recipient that only reads HAL-FORMS asks for HAL-FORMS when it
reads the reply link, and gets `_templates`.

Where it would matter:

- **By value.** An embedded reply representation is written in one dialect, before the originator knows which the
  recipient reads. Embed HAL Schema Forms, the reference dialect, and keep the `self` link, so a HAL-FORMS recipient
  reads it again in its own dialect.
- **A recipient built on a HAL-FORMS library** (Spring HATEOAS speaks HAL-FORMS) could answer RISE envelopes with no
  HEART library. That is a real argument for keeping both dialects on the reply resource, and it
  costs nothing because the server library already serves both.

Separately, the word "template" leaves RISE: §7.2's template fields are just the reply form.

## 5. Review of the fictional talk

What holds up, and is worth building as told:

- one link instead of five fields;
- two resources facing each other, the task and the reply;
- the reply brought into being by an act the originator's inbox offers ("open a reply"), its `Location` the bookmark
  that goes in the envelope;
- read before you send;
- exactly once from at least once: a second delivery reaches an answered reply and gets 409;
- the AMQP field mapping (method in `subject`, the reply in `to`, `message-id` back as `correlation-id`, expiry as
  `absolute-expiry-time`);
- backpressure as a state, for many consumers.

What the talk blurs, to fix before building:

- **Delivery channels and notification channels.** The demo's envelope had three reply links: HTTPS to the
  assistant, AMQP to the back office, and an event stream to Ana's phone. The first two are ways the recipient
  *delivers*. The stream is how the originator's own devices *hear* that the reply was answered: it hangs off the
  reply resource, and the recipient never touches it. One reply resource is the truth; delivery reaches it over any
  binding; notifications flow from it to whoever watches.
- **"Reads each reply link first, all three say awaiting."** There is nothing to read on an event stream, and over
  AMQP the read needs the HEART binding over AMQP, not just a queue.
- **AMQP needs a front door.** For `amqp://broker/…/replies/7f3a` to behave like a HEART resource (states, 409s,
  validators), something on the originator's side must consume the queue and answer as HEART does. The talk skips
  this; the prototype must build it.
- **Three channels in one demo** is the talk's showmanship. One reply, with its link in one scheme, is the honest
  first step.

## 6. The prototype: a dynamic webhook and AMQP, as simply as possible

### The cast

- **The library**, the recipient, in `heart-sims`. A loan with a hold on it offers `tell-me-when-renewable`. Its form
  takes `_rise`. Acting brings a **watch** into being (201, at `Location`). The watch is the task: waiting, delivered,
  failed, cancelled. A simulator control clears the hold, and the library wakes up.
- **The inbox**, the originator's side: a small HEART service. It offers `open-reply`, which brings a **reply** into
  being. The reply is awaiting, then answered or cancelled or expired. While awaiting it offers `deliver` (its schema:
  `loan` and `standing`), `progress` and `fail`.
- **ARTIE** plays the originator: opens a reply, then acts on the loan with the reply's link in `_rise`.
- **The library's wake-up** is a HEART client too: ARTIE's library, reading the reply link and acting on it.

The library needs a HEART client, which lives in the client build, and `heart-sims` can't depend on it. So the
prototype lives in a new client module, `client/artie-rise`, beside `artie-minecraft`. It uses `heart-sims` as test
fixtures, and gives the library a wake-up hook that the module fills with ARTIE.

### Slice 1: the dynamic webhook

1. The inbox and the reply resource, in heart-core terms (states, offers, reasons). Feature first.
2. The library's act with `_rise`, the watch, and the hold-clearing control.
3. The wake-up: read the reply link, act `deliver` with `If-Match`, mark the watch delivered.
4. A scenario, in Gherkin tables:
   - ARTIE opens a reply and asks the library to tell it when renewable;
   - the hold clears;
   - the reply reads `answered`, with `loan` and `standing`;
   - delivering again is refused, 409, "answered at …";
   - the watch reads `delivered`.
5. Live: the library and the inbox on two ports, ARTIE in a terminal, curl for the second delivery.

Everything is HTTP on localhost, and nothing is registered anywhere: the address arrives with the request.

### Slice 2: the same reply over AMQP

- **The binding, both ways, as a thin adapter.** An http4k `HttpHandler` behind a queue: a message becomes a request
  (method, URI, `If-Match`, body), and the response goes back to the message's `reply-to` with the same
  `correlation-id`. On the client side, a transport for the `amqp` scheme that does the reverse and waits for the
  correlated answer. Nothing above the transport learns about the broker.
- **The inbox listens on a queue too**, so the reply's link can be `amqp://localhost/inbox/replies/7f3a`. Read before
  you send works unchanged, because the read travels as a message as well.
- **The broker:** RabbitMQ 3.13, already on this machine, through Testcontainers in the features and Docker live.
  RabbitMQ 3.13 speaks AMQP 0-9-1 natively, where 0-9-1 has no `to` or `subject`, so the method, the URI and the
  status go in message headers, with `reply_to` and `correlation_id` as AMQP has them. AMQP 1.0, with
  `amqp-binding-vision.md`'s mapping, is the step after, on RabbitMQ 4.
- **The scenario** runs as an outline over the reply link's scheme: `http` and `amqp`, with the same rows.

### Slice 3, later: many subscribers

Variation A on the library's loans (a `subscribe` act, subscriptions as resources, thin events to inboxes), and
variation B as its broker twin. Not tonight.

### What tonight leaves out on purpose

The stamp beyond an unguessable reply address; signatures; by-value envelopes; progress; the 202 path; AMQP 1.0;
SSE; publish and subscribe; any spec text.

## 7. Decisions for the author

Each has a recommendation.

1. **The envelope:** one link by reference first, by value later. *Recommended.*
2. **Where it travels:** the `_rise` body member, declared in the act's schema so the form says the act takes one.
   *Recommended.*
3. **201 with a watch, instead of 202,** for now: no new heart-core status handling. *Recommended.*
4. **Read before you send:** a SHALL. *Recommended.*
5. **AMQP tonight:** 0-9-1 on the local RabbitMQ 3.13, headers for method and status. *Recommended*; 1.0 next.
6. **Where the prototype lives:** `client/artie-rise`. *Recommended.* The gates need no change: the client build is
   already in them.
7. **Many subscribers:** D (a feed) as the truth, A (subscriptions as resources) for push, B (a broker) as a hint
   carrier. *Recommended order: D is half built (the hub), then A, then B.*
8. **HAL-FORMS:** nothing RISE-specific; the reply resource negotiates as any resource does. *Recommended.*
9. **SPEC §7.6, the fallback directory:** drop it from RISE. *Recommended.*
10. **Client-sent hypermedia:** RISE's second principle in §7.1. *Recommended.*
