# Idea: the HEART tour, a narrative for onboarding developers

**Status:** idea only. Raised by the author on 2026-10-26. The text below was first given in chat, as a narrative to
listen to; it is kept here to become an artifact for onboarding other developers. It is a broad conceptual tour, from
the history of hypertext to what this repository builds. Facts about HEART are from spec/SPEC.md,
spec/HAL-SCHEMA-FORMS-HR.md and the module READMEs; historical dates are approximate. Before it becomes an artifact,
check it against the current spec and code, and against ONBOARDING.md.

---

## Part one: the Web's original bargain

Start with the browser, because everything here comes back to it.

In the early nineties the Web made an unusual bargain. A browser knows nothing about any particular site. It doesn't
know your bank, your library catalogue, or the airline. What it knows is a handful of media types: HTML, images, later
CSS and JavaScript. It's tightly coupled to those formats and to nothing else. Every site speaks those formats, so one
generic client can use all of them.

The key move was the form. HTML didn't just carry text and links. It carried controls. A resource told the browser
what it could do next: here's a link to follow, here's a form to fill in, here's the method and the address to send
it to. And it told it only what was possible right now. If your account was overdrawn, the transfer button simply
wasn't there. The browser didn't need a rule about overdrafts. The absence of the control *was* the rule. A person
read the representation, picked from the controls offered, and the server moved them to the next state.

That's hypertext as the engine of application state. The client doesn't carry a map of the application. The server
hands it the next part of the map with every answer.

## Part two: Fielding names it

Around 2000, Roy Fielding wrote his dissertation and gave the Web's architecture a name: REST. He described it as a
set of constraints:

- client and server are separate;
- the server keeps no session state;
- answers say whether they can be cached;
- servers can be layered;
- clients can optionally download code;
- the interface is uniform.

The uniform interface has four parts:

- resources are identified by URIs;
- they're manipulated through representations;
- messages describe themselves;
- hypermedia drives the application's state.

That last part is the one everyone forgot. It's also the one that makes a generic client possible.

## Part three: machines, and the tunnel over POST

Then the industry wanted machines to talk to machines, and it mostly ignored all of this.

First came XML-RPC, and then SOAP. The idea was remote procedure calls dressed up in XML. Everything went to one
endpoint, almost always as a POST. The operation's name sat inside the body. HTTP became a pipe that carried
envelopes. Caches couldn't see what was safe to reuse, because everything looked like the same POST. Status codes said
little, because errors lived inside the envelope. The contract lived outside the messages, in WSDL, a big description
of every operation the service could ever perform. You generated client stubs from it at build time. When the service
changed, you regenerated, recompiled and redeployed.

This is what tunneling means. The protocol becomes a transport, and the real application protocol hides inside it. You
lose everything the Web's architecture gave you for free: caching, visibility to intermediaries, and above all
hypermedia. A SOAP message never told you what you could do next. The WSDL told you everything you could ever do, in
principle, and you found out what you couldn't do right now by trying it and getting a fault back.

## Part four: "REST", as the industry heard it

By the mid-2000s there was a backlash. SOAP was heavy, and people wanted something simpler. They picked up the word
REST, but what they built was something different. It was nice URLs, HTTP verbs, and later JSON instead of XML.
Frameworks made it easy: a resource became a database table, and the four verbs became create, read, update and
delete.

That was progress on one axis. Caching came back, and status codes meant something again. But two important things
were lost.

**JSON has no controls.** HTML has links and forms built into the format. Plain JSON has neither. A JSON answer is
pure data. It tells you a pull request is a draft. It doesn't tell you that merge is unavailable because it's a draft,
and it doesn't offer you a merge form when merge becomes possible.

**The contract moved back outside the messages.** Without controls in the answer, the client has to know the API in
advance, so the documentation became the hypertext. Swagger, then OpenAPI, became the WSDL of the JSON world: a
catalogue of every endpoint and every parameter, read at build time or by a developer, and hard-coded into the client.
Every developer becomes a human compiler, translating documentation into rules in their own code.

The CRUD framing made it worse. When everything is create, read, update and delete on records, the business meaning
disappears into which fields you happen to patch. Merging a pull request isn't "update the pull request's merged field
to true". It's an act, with preconditions and consequences.

In 2008 Fielding complained publicly. The line the project's README opens with is his: if the engine of application
state, and hence the API, isn't being driven by hypertext, then it can't be RESTful and can't be a REST API. Period.
He listed his rules for what a REST API must do. Most of them come down to this: a client should need to know only the
media types and the entry point, and should learn everything else from the answers.

Leonard Richardson's maturity model described the same ladder from the industry's side:

- level zero is one endpoint, the SOAP tunnel;
- level one is resources;
- level two is HTTP verbs;
- level three is hypermedia controls.

Almost nobody reached level three.

## Part five: the people who kept the idea alive

A small group kept working on hypermedia for JSON:

- **Mike Kelly** made HAL, which adds links and embedded resources to JSON.
- **Mike Amundsen** made Collection+JSON and later HAL-FORMS, which adds form templates to HAL. He also worked on ALPS,
  a way to describe what an application's terms mean.
- **Siren** offered actions.
- **JSON Hyper-Schema** described links with templates whose variables come from the data.
- **Spring HATEOAS**, from Oliver Drotbohm and Greg Turnquist, put HAL and HAL-FORMS into one of the biggest server
  frameworks in Java.

So why didn't it take over? Because the generic client never showed up. Developers wrote bespoke clients anyway. They
read the docs, hard-coded the URLs and the rules, and ignored the links. Hypermedia's benefit, a client that adapts at
runtime, had no one to collect it. The browser collected it for HTML because a person sat behind it. For JSON APIs,
there was no person and no generic client.

## Part six: agents change the economics

Then language models arrived, and something interesting happened. For the first time there's a plausible generic
client for machine APIs: something that can read an answer it has never seen before and decide what to do with it.
That's the role a person used to play behind a browser.

But the industry wired agents up the old way, to catalogues:

- an OpenAPI document in the prompt;
- tool search, which retrieves tool definitions just in time;
- MCP, which lets an agent discover a server's tools.

All three are real improvements in their own ways. Tool search in particular is excellent at keeping context small.
But every one of them answers the question "what can this system do, in principle?" None of them answers "what can I
do to *this* resource, in *its* current state, right now?"

That gap is the whole project. A model choosing from a catalogue can pick something well-formed, sensible, and illegal
right now. It finds out by being refused. It invents field names from memory. It builds addresses by hand. When the
API changes, its knowledge drifts. And because a model is so good at coping, it papers over all of this, which hides
the gaps instead of closing them (spec/ideas/papering-over-gaps.md).

GitHub's own API gives a perfect small example. Ask it about a draft pull request and it tells you `mergeable: true`
and `mergeable_state: clean`. Yet you can't merge a draft. The rule lives somewhere else, in documentation and in
people's heads. Any client, human-written or model-driven, has to know that in advance.

## Part seven: HEART's claim

HEART stands for Hypermedia Enforced Agentic Reliable Transactions. Its claim is narrow on purpose. Tool catalogues are
scoped to the server or the tool library. HEART's affordance sets are scoped to the resource instance, its state, and
the moment. **Absence carries information.** If merge isn't offered on this pull request right now, the agent can't
choose it, because the server didn't put it in the candidate set. The precondition is enforced when the agent selects,
not discovered when it submits.

HEART isn't a new format built from scratch. It's a profile, a set of additions layered on formats that already exist:

- **Reference dialect:** HAL Schema Forms, Jeremy Badeau's project. It puts a `_forms` member into HAL, with full JSON
  Schema for each field.
- **Second dialect:** Amundsen's HAL-FORMS, which Spring HATEOAS already produces. It's the on-ramp.
- **Choosing a dialect:** ordinary content negotiation with the Accept header.
- **Choosing the HEART profile:** separately, using the W3C's Content Negotiation by Profile draft, with an
  Accept-Profile header going in and a Link header with relation "profile" coming back.
- **Identifiers:** they live under w3id.org, the permanent-identifier service, so they never depend on one company's
  domain.

## Part eight: three layers and a ladder

**The three layers** are the heart of it, if you'll forgive the pun.

**Layer one is state.** It's the resource's current representation: the pull request's title, its labels, whether it's
a draft. Layer one is never reused without checking. It's marked no-cache, and it carries a strong ETag that names
exactly this state.

**Layer two is the affordance set**, the forms that are legal right now. Here's the clever part. If the forms use URI
templates instead of concrete addresses, the affordance set contains nothing about which resource it belongs to. It's
purely a function of the state. Every draft pull request gets the same set of forms. So the server can hash those
bytes, give them an address of their own, and serve them as immutable, cacheable for a year and shared across every
resource in that state. Two unrelated drafts point at the same affordance-set address, and a cache fetches it once.

To make templates work, the form says where each variable comes from. That's `hr:templatePointers`, borrowed directly
from JSON Hyper-Schema: "fill `{number}` from this JSON Pointer into the state you already hold." The client fills it
in with deterministic code, never with the model.

**Layer three is the schemas**, the JSON Schema for each form's input. They're also content-addressed, immutable, and
shared even more widely.

**Trust between the layers** comes from the W3C's Subresource Integrity syntax, the same `integrity` attribute browsers
use for scripts. The link from layer one to layer two carries the full digest of the bytes it points at. The client
hashes what arrives and refuses it if it doesn't match. Each layer vouches for the next. Layer one is revalidated on
every visit, so the chain stays fresh at the top and permanent underneath.

One subtle rule follows Fielding's rule about not defining fixed names. The client never reads meaning from an
address. Even though the reference server puts part of a hash in its paths, the client doesn't parse the URL. It
trusts only the integrity value in the representation that linked to it.

**The conformance ladder** lets a server adopt this one step at a time:

- **L0** is everything in one answer: state, forms and schemas embedded, with no special headers. The spec says plainly
  that L0 buys correctness, not economy: absence already carries information, but nothing is shared.
- **L1** moves the affordance set out to its own cacheable address.
- **L2** moves the schemas out too. L2 is the reference rung, the one a server must reach to claim compliance.
- **L3** is the caching proof: two unrelated resources, one fetch.
- **L4** is concurrency and errors.
- **L5** is the same legality decision rendered in both dialects, which must agree.
- **L6** is reasoning: optional, never required, confined to one step.

## Part nine: acting, and refusing well

When a client acts, it sends the form's method to the form's target, with an If-Match header carrying the ETag of the
state it read. The server checks in a fixed order, and each answer means something different:

- **428:** you didn't say which state you read.
- **412:** the state changed since you read it. Your intent may have been fine, but the world moved, so re-read and
  decide again. Never blindly resend.
- **409:** that act isn't legal here at all.
- **422:** your input doesn't satisfy the form's schema.

Every refusal comes back as an RFC 9457 problem document, so the reason is machine-readable. When an act brings a new
resource into being, the answer is 201 with a Location header naming it.

There's no "create" operation in the CRUD sense. New things come from domain acts a resource offers: opening an issue,
opening a pull request, recording a foul. Acts are named by link relations, described by ALPS profiles, and the forms
describe themselves, so a reader, or a model, knows what each act means without prior knowledge.

Then there's **explaining absence**, `hr:unavailable`. Removing an act from the forms prevents a bad call, but leaves
an agent with nothing to say when someone asks why. And a model, given nothing, will invent a reason. So an affordance
set can link to a small shared resource listing the absent acts with a short reason for each: "A draft can't be
merged; mark it ready for review first." It's fetched only when someone asks. It's a reference, not a menu: a client
never acts on anything it finds there.

Newer rules in the profile:

- **Choices from state** (`hr:options`): instead of a free text field, a form can say "pick one of the players of this
  team who haven't fouled out", or "one of the agents on duty". The server enforces the same set it advertised. It's
  borrowed from HAL-FORMS' options and extended with JSON Pointers into state.
- **Embedded resources carry no forms.**
- **Each form has its own integrity.**
- **Fragments are JSON Pointers.**

Around all this sit:

- **The entry point.** A bookmark and a JSON Home document say what a service serves.
- **The credentials rule:** no model is ever shown a credential. The client adds the credential just above the wire,
  below everything the reasoner can see. The reasoner's input has no place for a header, so a credential isn't
  withheld from the model. It's impossible to express to it.

**Protocol neutrality** comes from Fielding's first rule: an API shouldn't depend on one protocol. HEART is a core with
bindings. HTTP is the main binding. CoAP, the lightweight protocol for constrained devices, is the second, with DTLS
security, and the same concepts carried in CoAP options.

## Part ten: what's actually built

**The server library** (`heart/`):

- You declare your resources, states and acts, and write each act's input as a plain class. JSON Schemas are generated
  from its annotations. You write no controllers.
- `heart-core` does the rendering at every rung, the content addressing, the act checks and the negotiation.
- There are bindings for Spring WebFlux, http4k and CoAP, plus optional event sourcing.
- A conformance kit, the TCK, is a set of features a server runs against itself.

**Three reference domains** prove it isn't tied to one business:

- a garden with beds, sowing, watering and harvesting;
- basketball scoring;
- support tickets.

**A TypeScript client** and a client conformance suite show it isn't tied to one language.

**The gateway.** This is what makes HEART useful against systems you don't control. You point it at an existing API's
OpenAPI description, here GitHub's own published description, and write a connector file. The connector says which
operations to read, which states and rules apply, and how links and embedded resources should be presented. The
gateway then answers in HEART, in front of GitHub, calling GitHub with each caller's own token. Suddenly GitHub has
state-scoped forms, explained absence, ETags and 412s. The pitch is "little control over GitHub? Automate it from your
laptop with your own gateway."

The gateway is also where gaps get closed for good. Two examples from the merge watch:

- The draft-that-says-mergeable became a missing merge form with a reason.
- The embedded repository that made every pull request look changed became a connector rule that keeps only the
  identifying fields.

Each was fixed once, in the system, instead of being worked around in every client run.

**ARTIE**, the reference client, is the generic client the industry never had. It's coupled to the HEART media type and
to no domain:

- It syncs: reads the state and follows the layers, verifying integrity and caching what's immutable.
- It can reason, but only in one confined step: intent and the legal forms go in, one selection comes out.
- It acts deterministically: ARTIE, not the model, fills templates, checks the input against the schema, sends
  If-Match, and treats a refusal as normal information.

**heart-mcp** lets any MCP host, such as Claude Code or Claude Desktop, work with any HEART service through six fixed
tools. The tool list never changes. What's legal now comes back in every result. The model chooses, and ARTIE checks
the choice and builds every request.

## Part eleven: goals, scripts, and reasoning once

This is the newest layer, and the one the merge watch showed off.

**A goal** is a Markdown file with a little YAML front matter: where to start, and the inputs with their questions and
defaults. The body is plain words, either free prose or a bulleted list of steps, with words for time and repetition:
whenever, once, until, or give up after three minutes. One line can be marked `reasoned`, meaning "a model decides
this every time."

**ARTIE plans the goal against the live service.** For each step, the model reads the words against the forms actually
offered and chooses a relation, a check or a field. That decision is recorded in a capture, with the model's stated
reason.

**The generator turns the capture into a heart-script.** A heart-script is a small Kotlin script, readable in a minute,
where each line points back to the goal line it came from. Alongside it come:

- **pins:** the integrity of each form the script was reviewed against, so that if the server changes a form, the
  script refuses rather than guesses;
- **derivations:** a source map from goal words to script lines.

After that, the script replays the kept decisions with no model, except on the one reasoned line.

That's the merge watch's result. An hour, two thousand seven hundred and forty steps, five model calls: six merged,
held ones left alone and noted once, authors told why, zero errors, stopped when its issue was closed. Reason once,
then let the other R's carry the load (spec/ideas/the-other-rs.md). And even that one reasoned line could be
deterministic here. It earns its keep only when it has to read free text
(spec/ideas/reasoning-that-earns-its-keep.md).

## Part twelve: keeping everyone honest

**Buster**, named after a terrier who chews remotes, is a chaos filter. It sits in front of a server and tampers with
answers on purpose:

- wrong bytes under an integrity value;
- stale states;
- missing headers.

Each plugin is both an attack and a test of what a well-behaved client must do.

**Standards work** is drafted but not submitted: an Internet-Draft for the HAL Schema Forms HEART profile, with its link
relations and problem types, and a request for the w3id identifiers. Nothing has gone out.

**RISE** is on hold until HEART is finished. It's the reciprocal half, where client and server swap roles for
asynchronous callbacks, like a self-addressed stamped envelope.

## The thread through all of it

The browser was the Web's generic client, coupled to media types and to no site, guided by controls the server offered
for this resource, now. Machine APIs threw that away: first by tunnelling procedures over POST, then by putting data in
JSON and the rules in documentation. Agents finally give us a generic client again. HEART's bet is that the right thing
to do with one is not to hand it a bigger catalogue, but to give it what the browser had:

- a media type with controls;
- state that tells the truth;
- absence that means something;
- refusals that explain themselves.

Then keep the model to the one place judgment lives, and let everything else be deterministic, checked, cached and
kept. Nearly every piece is borrowed:

- **HAL and HAL Schema Forms** for the format;
- **HAL-FORMS** for the on-ramp dialect and the idea of options;
- **JSON Schema and JSON Hyper-Schema** for inputs and templates;
- **JSON Pointer and URI Templates** for filling them in;
- **Subresource Integrity** for trust;
- **ETags and conditional requests** for concurrency;
- **problem details** for refusals;
- **ALPS** for meaning;
- **profile negotiation** for opting in;
- **CoAP** for the small devices.

What's new is how they dovetail, and the claim that ties them together: affordances scoped to the resource, its state,
and the moment.

---

## For the artifact, later

- Keep to the explainer rules used for the other pages: one theme (the browser as generic client), outline first, no model calls,
  traces or fix histories.
- Diagrams worth drawing: the tunnel over POST beside hypermedia; the three layers with integrity links; two drafts
  sharing one affordance set; the 428/412/409/422 order; goal to capture to script.
- Reuse the companion pages: Information and Controls (`docs/reports/information-and-controls/`) and One Goal, Decided
  Once (`docs/reports/merge-watch/`).
- Quote Fielding only verbatim, checked against his own pages.
