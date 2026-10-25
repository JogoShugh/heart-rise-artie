<!-- Converted from the published review (claude.ai artifact NiZdhyhpwHrhdpDgkMJgWr, v17). Keep the two in step. -->

*H.E.A.R.T. + R.I.S.E. · ARTIE · read against Roy T. Fielding, 20 Oct 2008*

# Fielding's Rules, Checked

> **Note.** Re-checked 11 October 2026 at commit `a70ecc2`, against the live garden server over HTTP and CoAP: the garden suite (15 scenarios), the HEART conformance suite run against the garden (14 pass; 4 test an act on the bookmark that creates a resource, which the garden does not offer), ARTIE's live suite (24 scenarios) and the last Buster campaign. No verdict changed. The server's HEART code now lives in the library at `heart/` (`heart/ARCHITECTURE.md`); the garden server keeps only its domain and its declaration. Paths below are current.
>
> Re-checked again 12 October 2026 at commit `a7831ce`, after fixing what the Roy Bott review noticed: a CoAP request now carries its W3C trace context; a refusal's `detail` names the act by its relation and gives the domain's reason; the garden's bookmark offers an act that builds a new bed (201, `Location`); and the garden refuses water a bed cannot hold (422 `rule-refused`). Evidence: the garden suite (17 scenarios), the HEART conformance suite against the garden (18 of 18, none skipped), ARTIE's live suite (26 scenarios) and a fresh Buster campaign (125 mutations, 0 FAIL). No verdict changed.

> Re-checked 15 October 2026, on the work since `a7831ce` (some of it not yet committed): `coaps` with pre-shared keys (Rule 1); HEART's identifiers moved to `https://w3id.org/heart-rise/`, each with a page; a Layer 1 `ETag` that changes whenever its representation's bytes do; an act's former paths; the OpenAPI twin's opt-in link to each state's affordance set; and **choices from state** (HAL-SCHEMA-FORMS-HR Rule 8): a property's choices taken from the resource itself or from another resource, enforced by the server and shown to a model as a closed set. Two verdicts moved. Rule 2 goes from ✅ to 🟡: on a closer reading, the way an act's `If-Match` is scoped stretches RFC 9110, and the section [Precondition scope, examined](#precondition-scope) says how far, what the alternatives cost, and the fix this review recommends. And the "interface in the form" reply is stronger: a form now says not only how to build the request but which values it may carry now.

Each heading below is Fielding's own wording, from ["REST APIs must be hypertext-driven"](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven) and his replies in its comments. Under each is a short account of what the spec and today's code do about it, with the evidence. Where the work falls short, this review says so. Revised 8–9 October 2026: Rule 4 resolved, the entry point added, Rule 6 re-read in full, the `If-Match` narrowing removed, and rung discovery added (a JSON Home index and `Preference-Applied`). Revised 10 October 2026: forms named by typed relations described in an ALPS profile, refusals as RFC 9457 problem details, absent acts explained by the server, Layer 1 always read with `Cache-Control: no-cache`, act responses naming their resource in `Content-Location`, and ARTIE checked by an adversarial proxy, Buster. The spec now has a protocol-neutral core with two bindings, HTTP and CoAP, and ARTIE works live over both (Rule 1).

> "if the engine of application state (and hence the API) is not being driven by hypertext, then it cannot be RESTful and cannot be a REST API. Period."
>
> — The post's thesis. H.E.A.R.T. takes it literally for machine clients: the manifest in each response is the only source of what the client may do next.

Key: ✅ Supports the work does this · 🟡 Partly mostly, with a stated gap · 🔴 Tension the work departs from it

## At a glance

> Re-checked 25 October 2026 at commit `e5c16e8`, on the work since 15 October: resources embedded the HAL way (`_embedded`, SPEC §6.1); search and list results revalidated by their own `ETag`; an OpenAPI-to-HEART gateway that serves GitHub as HEART; heart-script, its scripts, goals in plain words, and `artie generate`, which writes a run's decisions as a script with a lock file; listeners that look again only when what they read has changed, with the gateway holding a read until a webhook says it has; and choices from state in two more clients, heart-ts and heart-mcp, and live on two domains (`docs/reports/choices-live/`). One verdict changed, the same day: Rule 2 is back to ✅, because an act's target is now a resource with a representation of its own, read under its owner's ETag ([Precondition scope, examined](#precondition-scope)). One row is added, [Inference per run](#inference), because the project's goal is Fielding's rules with as little model inference per run as possible, and nothing here measured that half. It asks the question this review had left alone: a script that runs with no model names its relations, as the early-bound client of the OpenAPI review does, so how is it still a REST client? Rule 2 also gains one thing to watch: the gateway's held read.

Each row jumps to its full account below. Columns are the commits this review has been checked against, all re-assessed with Fielding’s full wording.

| Axis | `c16e61b` 4 Oct · first review | `c5db0e4` 8 Oct · integrity, template pointers | `91fb71a` 8 Oct · service index | `e2a773f` 9 Oct · strong If-Match | `9a39cfc` 9 Oct · discovery, no fixed paths | `360865f` 10 Oct · typed relations, Buster | `d10c048` 10 Oct · any protocol | `4c52390` 10 Oct · CoAP binding | `b7f4d00` 10 Oct · first real-model run | `a70ecc2` 11 Oct · re-checked live | `a7831ce` 12 Oct · trace over CoAP, bed from the bookmark | 15 Oct · coaps, ETag digest, choices from state | 25 Oct · scripts, gateway, listeners, act targets |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| [Any protocol](#r1) Rule 1 | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [No protocol changes](#r2) Rule 2 | 🟡 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ |
| [Media types first](#r3) Rule 3 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Server-taught URIs](#r4) Rule 4 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [No typed resources](#r5) Rule 5 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Bookmark only](#r6) Rule 6 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Hypertext = affordance](#hypertext) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Typed relations](#typed-relations) Reply | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Interface in the form](#interface) Reply | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Client decides](#client-decides) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Cool URIs](#cool-uris) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [What POST means](#post-meaning) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Concentrated prior knowledge](#prior-knowledge) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [No batch calls](#no-batch) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Follow your nose](#follow-nose) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Key off the content](#key-off-content) Reply | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [App vs resource state](#app-state) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Public identifiers](#public-ids) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [AI-driven clients](#ai-driven) Reply | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| [How well a model chooses](#model-quality) Project, §10 | – | – | – | – | – | – | – | – | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 |
| [Inference per run](#inference) Project goal | – | – | – | – | – | – | – | – | – | – | – | – | 🟡 |

## The six rules in the post

<a id="r1">

### "A REST API should not be dependent on any single communication protocol … any protocol element that uses a URI for identification must allow any URI scheme to be used"

*Rule 1*

**✅ Supports:** A protocol-neutral core with two real bindings, HTTP and CoAP, chosen by URI scheme. (10 Oct 2026.)

The full rule allows that a "successful mapping to a given protocol may be dependent on the availability of metadata, choice of methods, etc." So depending on a protocol's details is not the problem. The problem is mixing identification with interaction. The spec now separates them. §6.0 states the core in protocol-neutral terms: read, requested and applied rendering, validator, never fresh, act, done, stale, not offered, invalid, refusal detail. It then maps each one to a binding. Any URI scheme may appear in any link. A client picks a binding by the URI’s scheme, refuses a scheme it has no binding for before sending anything, and never rewrites a URI to another scheme.

There are two real bindings. §6.1–§6.12 are the HTTP binding. §6.0.1 is a CoAP binding (RFC 7252). CoAP has no header for a profile or a preference, so the binding carries them in experimental CoAP options, one for one with their HTTP headers. A Layer 1 read carries a critical, unsafe `No-Cache` option, which no proxy can answer from its cache. The validator is 8 bytes, CoAP’s limit, and the refusals are CoAP codes with the same problem types. The reference server answers CoAP natively on UDP beside HTTP, from the same services and the same act checks. ARTIE’s core is unchanged; `CoapBinding` serves `coap:` URIs. Live, over UDP against the real server, ARTIE takes a bed through sow, water and harvest at L0 and at L2, and goes from the `coap://…/` bookmark to an act. Nothing is sent over HTTP in any of them.

What remains is narrower than the rule. The CoAP option and format numbers are experimental until registered with IANA. There is no `coaps` (DTLS) yet. HAL-FORMS, the HTTP on-ramp, is not served over CoAP. A CoAP request carries no client identity. Since 12 October it does carry trace context: option 65020 holds the same value as HTTP's `traceparent` header, elective, safe to forward and no part of the cache key (RFC 7252 §5.4.6). ARTIE mints a new span for each CoAP request, so each event in its log names the request it is about, as over HTTP; in the live run every CoAP event has a span.

- **Code:** client `heart/Binding.kt`, `HttpBinding.kt`, `CoapBinding.kt`; library `heart-coap/…/CoapFrontDoor.kt`; garden `declaration/BedActs.kt`
- **Tests:** `any-protocol.feature`, `coap-binding.feature` (client, with traceparent), `coap.feature` (server), live "Over CoAP" scenarios, including "every event names the span of the request it is about"
- **Spec:** §6.0 "Core and bindings", §6.0.1 "The CoAP binding"

<a id="r2">

### "A REST API should not contain any changes to the communication protocols aside from filling-out or fixing the details of underspecified bits of standard protocols"

*Rule 2*

**✅ Supports:** Standard mechanisms only, each as its RFC reads. (Narrowing removed 9 Oct 2026; the act's precondition scope examined 15 Oct 2026 and resolved 25 Oct 2026, see [Precondition scope, examined](#precondition-scope).)

Every mechanism is an existing standard: `Prefer` tokens (RFC 7240, which allows unregistered tokens), conditional requests (RFC 9110, RFC 6585), `immutable` (RFC 8246), `Link: rel="profile"` and `Accept-Profile` (W3C content negotiation by profile). Spec 0.4 dropped its private use of the media-type `profile` parameter for exactly this reason. Conditional acts follow RFC 9110 §13.1.1 as written. HTTP is stateless, so an act repeats the `Accept`, `Accept-Profile` and `Prefer` its client synced with; the server selects the owning bed’s representation from them and compares `If-Match` with that representation’s `ETag` by strong comparison, the whole value. An earlier rule read only the state number inside the `ETag` and ignored the rest, a narrowing of RFC 9110; it was removed in `e2a773f`. Declaring what a server serves adds nothing new either: `Preference-Applied` (RFC 7240) says which rung each response is at, and the service index is also served as `application/json-home`, whose link hints `formats`, `accept-prefer` and `precondition-req` were written for exactly this. OPTIONS was considered and rejected, since its response has no defined body and is not cached. The 10 October work kept to the same line: a client reads Layer 1 with the request directive `Cache-Control: no-cache` (RFC 9111 §5.2.1.4), refusals are `application/problem+json` (RFC 9457) with JSON Pointers (RFC 6901), an act’s response names the bed it represents in `Content-Location` (RFC 9110 §8.7), and every request carries a W3C `traceparent`, over CoAP in an experimental option one for one with the header. Things that need watching: `Accept-Profile` is a W3C draft, and JSON Home and HTTP link hints are Internet-Drafts. Using a draft mechanism is not a change to the protocol, but it may move.

- **Spec:** §6.1 "Why not a media-type parameter", §6.3 "Precondition scope"
- **Tests:** `negotiation.feature` (heart-core), `conditional-requests.feature` (heart-spring), `index.feature` (heart-core: the home document), client `layer1-freshness.feature`
- **Resolved 25 Oct 2026:** the act's precondition scope ([below](#precondition-scope))
- **To watch (25 Oct 2026):** the gateway's held read. A listener's conditional GET may carry `Prefer: wait=N`, and the gateway then holds it while the answer would be 304, until a signed webhook says the resource changed or the time runs out. RFC 7240 §4.3 defines `wait` as how long the client expects the server to take to process the request; using it to wait for a change is a long poll under that name, not what the RFC describes. It is a gateway feature, not in SPEC, and a client that ignores it is answered at once. If SPEC takes it up, it should be named for what it does, as RFC 7240 lets anyone define a preference, rather than borrow `wait`.

<a id="precondition-scope">

### Precondition scope, examined

*Added 15 October 2026. Rule 2, read strictly.*

**What HEART does.** An act goes to its own target, `POST /beds/2/actions/water-cell`, but its `If-Match` carries the `ETag` of a different resource: the bed, `/beds/2`, whose representation offered the form. The server compares the two, refuses a mismatch with 412, and answers with the bed's new representation, naming it in `Content-Location`. SPEC §6.3 calls the bed the act's *owning resource*.

**Why that stretches RFC 9110.** §13.1.1 evaluates `If-Match` against "the selected representation of the target resource", and the target resource of this request is `/beds/2/actions/water-cell`, not `/beds/2`. HEART's defence, written on 8 October, is that the origin server decides what a target's selected representation is (§3.2), and that it decides it to be the owning bed's representation. That defence holds up only halfway: today `/beds/2/actions/water-cell` has no representation of its own at all. `GET` on it is not served. So the "selected representation" is a fiction the server asserts, not something a client, a cache or a proxy could observe. A strict reader of Rule 2, which forbids changing the protocol beyond filling its underspecified parts, could fairly call this a change to what `If-Match` means. It works, it is consistent, and every client and test relies on it; but it is the one place where HEART leans on its own reading of HTTP rather than HTTP's.

**The obvious fix, and why it is worse.** Post the act to the bed itself: `POST /beds/2`, with the bed's own `ETag` in `If-Match`. That is RFC 9110 exactly. But then the request has to say *which* act it is, and there are only two places left to say it:

- **In the body**, as `{"act": "water-cell", "milliliters": 250}`. This is tunnelling: the operation is hidden inside the payload, the way SOAP hides it in the envelope.
- **In a header**, as SOAP 1.1 did with `SOAPAction: "urn:garden#waterCell"`. That is a protocol extension of HEART's own, the thing Rule 2 forbids, and it is still invisible to most tools.

Either way, the operation disappears from the request line. That costs a great deal in practice:
- **Logs:** access logs, which record the method and URI, can no longer tell a watering from a harvest. Filtering by operation means parsing bodies or custom headers.
- **Gateways:** they route, rate-limit and authorize on the URI. A rule like "only the irrigation service may `POST …/water-cell`" can no longer be written there.
- **Metrics and traces:** these group requests by route. Every act would collapse into one `POST /beds/{id}` series.
- **Caches:** they invalidate by URI. They already invalidate `/beds/2` after any unsafe request to it, so nothing is gained there.

Fielding's own reply covers the method: "You don't get to decide what POST means — that is decided by the resource". It doesn't ask for one URI per resource with a multiplexed body. Distinct, readable URIs for distinct things are what he encourages, as long as the server provides them, which HEART's forms do.

**What this review recommends: make the action resource real.** Keep the per-act URIs. Give each one an actual representation, so that its `ETag` is genuinely the target's own:

- **What `GET` returns:** `GET /beds/2/actions/water-cell` returns that act's form for bed 2: its title, schema and choices, the same form the bed embeds or links. When the act isn't offered, it returns an explanation (the act's entry in `hr:unavailable`) with a 409-style problem, or a representation saying so.
- **Its validator:** the action resource's `ETag` names that representation, and changes whenever the bed's state does, since the form exists only by virtue of the bed's state. The simplest form: the bed's state version plus the form's digest.
- **The act:** `POST` with `If-Match` is then compared against **the target resource's own** selected representation, exactly as §13.1.1 says. A stale view of the bed yields a stale view of the action resource, so the 412 behaviour is unchanged.
- **What clients may send:** a client that already holds the bed's `ETag` keeps working if the server accepts either validator. This review suggests one release where both are accepted, then the action resource's own.

That keeps everything the URI buys for observability, removes the fiction, and adds something useful. An action resource becomes a thing you can fetch, link to, cache-revalidate and log. That is the "define more resources" advice from the batch-calls reply below.

**An alternative worth naming: nouns for acts.** An event-sourced domain could expose each act as creating a subordinate resource: `POST /beds/2/waterings` answers `201` with `/beds/2/waterings/17`, one of the bed's history events. The operation stays in the URI, and the collection has its own representation and validator. It suits domains whose acts are records, such as waterings, fouls and comments, better than ones whose acts are commands, such as thaw or resolve. It is a modelling choice per domain. HEART's declaration could allow it, since an act's path is already the domain's to name.

**Status:** resolved 25 October 2026, the simplest way the recommendation allows. Every act's target answers `GET` and `HEAD` (SPEC §6.2, "An act's target is a resource"), over HTTP and CoAP. The body is small: a link `up` to the owner, a link `hr:form` to the act's form where it already lives in the owner's shared affordance set (by a JSON Pointer fragment, with the set's integrity and the form's own), and `ownerETag`. It holds no copy of the form and none of the owner's state, so its bytes change exactly when the owner's representation does, and the owner's ETag is a strong validator for it. The comparison `If-Match` gets is therefore with the target's own ETag, as RFC 9110 §13.1.1 reads, and it is the value every client already sends: no client changed, and none needs the `GET` before acting, so there is no release in which two validators are accepted. An act not offered answers its `GET` with 409 and why. Evidence: the HEART conformance suite's three new scenarios, passing on every server that runs it (the lamp over http4k, the gateway's lamp and its GitHub, the garden, basketball and tickets); `coap.feature`'s two; the garden's identifier check, which now finds `hr:form` and its page. Still not served: `GET` on an act the bookmark offers. The richer answers the same address could give, all opt-in, are parked in `spec/ideas/act-resource.md`.

<a id="r3">

### "A REST API should spend almost all of its descriptive effort in defining the media type(s) used for representing resources and driving application state, or in defining extended relation names and/or hypertext-enabled mark-up for existing standard media types."

*Rule 3*

**✅ Supports:** The spec defines a profile, one relation and processing rules. It defines no endpoints.

H.E.A.R.T. adds a profile on top of existing media types (`application/hal+json` with HAL Schema Forms, and `application/prs.hal-forms+json`). It names its own extended relations, `hr:forms` and `hr:unavailable` under `https://github.com/jogoshugh/heart-rise/rels/`. The example server names each of its acts by a relation too (`garden:water-cell`, a CURIE for `/profiles/garden#water-cell`), and describes them in an ALPS document at that address. This is the “extended relation names” half of the rule. And it defines how to process the three layers. The garden beds and their action URLs are an example server, not part of the protocol: nothing in the spec requires `/beds` or `/actions`.

- **Spec:** §6.1 profiles and the forms relation; §6.4 layering
- **Tests:** `forms-link.feature`, `form-relations.feature` (client and server), `layers.feature` (heart-core)

<a id="r4">

### "A REST API must not define fixed resource names or hierarchies … allow servers to instruct clients on how to construct appropriate URIs, such as is done in HTML forms and URI templates"

*Rule 4*

**✅ Supports:** The server instructs; the client reads nothing from a URL; the spec names no paths. (Resolved 8–9 Oct 2026.)

Every act goes to a target the server sent. ARTIE's `act()` takes an action name, never a URL. Where a target is a template, the form itself says how to fill it: `"hr:templatePointers": {"id": "/id"}`, a JSON Pointer (RFC 6901) into the bed's representation, taken from JSON Hyper-Schema's `templatePointers`. That is an instruction "within media types", defined in the published H.E.A.R.T. profile of HAL Schema Forms, not a rule the client knows from elsewhere. The earlier gap is closed: ARTIE used to verify shared layers by reading the hash out of the URL path. It now checks bytes against an `integrity` value carried in the linking representation, as Subresource Integrity does, and reads nothing from any URL. The spec no longer names paths either (9 Oct 2026): §6.5 requires only one URL per distinct body, served `immutable`. Since 11 October the reference server names each body by the first 16 hex digits of its digest (`/affordance-sets/588773b4c4ba671e`), with no `sha256-` in front, so the name no longer looks like a digest; integrity is the only check, and a server refuses to start if two bodies would share a name. The reason is this rule, not URI opacity: Fielding wrote that “REST does not require that a URI be opaque” and that RESTful applications are “encouraged to use human-meaningful, hierarchical identifiers”, which the reference server’s paths are.

- **Tests:** `layers.feature` (heart-core: the pointer `id` at `/id`), client `conditional-acts.feature` (pointers, fields, refusals), `sync-l2.feature` (a hash-shaped URL is ignored; mismatched integrity is refused)
- **Spec:** `spec/HAL-SCHEMA-FORMS-HR.md` Rules 1 and 2; §6.5 "Naming is the server's", §6.9.1

<a id="r5">

### "A REST API should never have “typed” resources that are significant to the client … The only types that are significant to a client are the current representation’s media type and standardized relation names."

*Rule 5*

**✅ Supports:** ARTIE knows nothing about beds.

The client branches only on the media type and profile (its `Dialect`) and on the forms relation. A resource's state is opaque JSON that it carries but never interprets. The garden appears only in test data. The planned evaluation uses five unrelated domains, plus renamed actions, precisely to check that nothing domain-specific has crept in.

- **Code:** `heart/Dialect.kt`, `HeartResponseParser.kt`
- **Plan:** `spec/EVAL-L6.md` §3

<a id="r6">

### "A REST API should be entered with no prior knowledge beyond the initial URI (bookmark) and set of standardized media types that are appropriate for the intended audience (i.e., expected to be understood by any client that might use the API). From that point on, all application state transitions must be driven by client selection of server-provided choices that are present in the received representations or implied by the user’s manipulation of those representations."

*Rule 6*

**✅ Supports:** One bookmark reaches everything, and every transition is a choice or a form the server offered.

`enter("/")` takes the only URL ARTIE is ever given: the service index. From there it searches beds by name or looks one up by id, using GET forms whose targets fill from their own fields, and follows a result chosen by its `item` relation and title. After that, every act must name a choice in the current manifest. A name that is not offered is refused before any request is sent. Each response becomes the new state and manifest. A `412` or `409` makes the client re-sync instead of trusting what it held. At L6 the reasoner can only pick among the offers it is shown, and the agent rejects anything else.

Filling a template is "the user’s manipulation of those representations": the form and its template pointers come from the server, as an HTML form does, so L1 and L2 meet this rule as fully as L0. An earlier version of this review rated the rule "Partly", arguing from a "strictest reading" that required a complete link per action. That reading depended on quoting the rule without its last clause, and is withdrawn. The entry point, a real gap until `91fb71a`, is closed: `GET /` is a service index, and live scenarios go from it alone to a watered bed at L0 and L2. Since 12 October the garden's bookmark also offers `garden:build-bed`, an act that brings a new bed into being: ARTIE enters `/`, builds a bed from the form, lands on it from the response and sows it, with no URI of its own.

What remains is the rule’s first sentence: "standardized media types … expected to be understood by any client". ARTIE’s prior knowledge is HAL, HAL Schema Forms and the H.E.A.R.T. profile of it. HAL is an IETF draft, HAL Schema Forms is a published but unstandardized specification, and the profile is this project’s own. A generic HAL Schema Forms client can use L0, which uses none of the profile’s additions, but not L1 or L2. That is a question of adoption, not of how ARTIE behaves, and it is the same theme as the typed-relations reply below. Both dialects have the entry point: HAL-FORMS cannot put a field into a target’s path, so its index uses GET templates whose fields go in the query (`/beds?name=`, `/beds?id=`), and a live scenario goes from `/` alone to a watered bed in HAL-FORMS too.

The bookmark also tells ARTIE what the server can do, without a second bookmark. Asked for `application/json-home`, the same `/` answers with a home document whose hints list the dialects and the `Prefer` values honoured. ARTIE asks for the best it offers, never more than its own setting, and then believes each response’s `Preference-Applied` over the declaration. A server just starting at L0 declares nothing and is used at L0. Live, the real server was started at L0, L1 and L2 in turn, and ARTIE found and worked at each level from `/` alone. `/.well-known/api-catalog` (RFC 9727) would go one step further, from a host name alone; it is parked, because it adds a fixed name and ARTIE is always given the index.

- **Tests:** `service-index.feature` (client and server), live "build a new bed and sow it", the HEART conformance suite's bookmark-act scenarios (201, 428, 412, 422), `act-unknown.feature`, `conditional-acts.feature`, `reason-l6.feature`; live "From the bookmark alone" at L0 and L2, full cycles at L0, L1, L2 and over HAL-FORMS, discovery against servers capped at L0, L1 and L2; client `rung-discovery.feature`. Buster campaign (12 Oct): an offered act was removed from the manifest 13 times, twice in the scenario that builds a bed, and ARTIE never sent it
- **Open:** the media type and profile are published but not standardized

## From his replies in the comments

Fielding answered readers in the comments for three weeks. Several replies read as if written for machine clients like ARTIE.

<a id="hypertext">

### "the simultaneous presentation of information and controls such that the information becomes the affordance through which the user (or automaton) obtains choices and selects actions"

*His definition of hypertext · reply to llimllib and Bjorg, 20 Oct*

**✅ Supports:** This is where H.E.A.R.T. gets the word "affordance".

A Layer 1 response is state and manifest together, and the absence of an action is information: you cannot water an empty bed. Since 10 October the server can also say why. An `hr:unavailable` link leads to a list of acts not offered now, each with a reason, and a client fetches it only when it needs to explain. At L0 everything arrives in one response. At L1 and L2 the controls are linked rather than inline, but they are still part of the same representation, through a named relation, and a `304` proves the link is unchanged.

- **Spec:** §3.4, §6.1 three layers, §6.7
- **Tests:** `sync-l0.feature`, `sync-l2.feature`, `explaining-absence.feature`, `layers.feature` (heart-core)

<a id="typed-relations">

### "Typed relations, specific media types, and action-specific elements provide the guidance needed for automated agents."

*Reply to snoopdave, 20 Oct*

**✅ Supports:** Each act is now a typed relation, described where it is defined. (10 Oct 2026.)

Each action comes with its own JSON Schema (Layer 3), and every act is named by a relation, not by a server-chosen key: `garden:water-cell` expands to `/profiles/garden#water-cell`, and that address is an ALPS document with the act’s title and description. The form carries the title and description too, and each schema field is annotated, so a reasoner sees what an act is for, not only what it is called. A deterministic client can key off the relation URI. What remains is scope, not mechanism: these relations belong to one service’s profile. A vocabulary shared across services, so that two shops’ “submit order” are the same relation, is the RISE work, parked.

- **Tests:** `form-relations.feature` (client and server), `hal-forms-on-ramp.feature`
- **Open:** spec §7.5 RISE shared relation vocabulary

<a id="interface">

### "The interface doesn’t need to be discovered. It is defined right there in the hypertext. The representation tells the client how to compose all transitions to the next application state."

*Reply to nkallen, 21 Oct*

**✅ Supports:** A form entry is everything needed to build the request.

Each `_forms` entry carries the target, the method, the content type and the schema. ARTIE builds the request from that entry and nothing else. Since 15 October a property can also say where its choices are (HAL-SCHEMA-FORMS-HR Rule 8): from the resource's own state, such as "this team's players who have not fouled out", or from another resource, such as "the agents on duty for this priority". So the form tells the client not only how to compose the transition but which values it may carry right now. ARTIE resolves them deterministically, shows a model only that closed set, and never sends a value outside it. The server refuses one if it arrives. Content addressing keeps "right there" cheap. The set and its schemas are fetched once and served from cache afterwards. Two beds in the same state cost one set fetch, proved live with two clients behind one cache.

- **Tests:** client `act.feature`, `l1-and-sharing.feature`, `choices.feature`; heart-core `options.feature`; basketball and tickets features; live L3 scenario

<a id="client-decides">

### "The client (user or agent) has to decide what transition to take, not what interface to use."

*Reply to TheOtherMarcus, 21 Oct*

**✅ Supports:** This is exactly how the L6 work is split.

The reasoner decides the transition: an action name and field values, or a question for the user. It is never shown a URI or a method, and its output type has no field for one. Deterministic code then builds the interface: target, method, `If-Match`, body. A mutation test proved that skipping the agent's checks lets bad selections through, so the boundary is enforced, not just intended.

- **Code:** `heart/Reasoning.kt`, `ArtieAgent.kt`
- **Tests:** `reason-l6.feature`

<a id="cool-uris">

### "Because implementations change, but cool URIs don’t."

*Reply to TheOtherMarcus, 21 Oct*

**✅ Supports:** Content-addressed URIs cannot change, and the server's implementation can.

An affordance-set or schema URL is named by the server from the hash of its bytes, so it is valid forever and served as `immutable`. The client checks the bytes against the `integrity` value in the link that led to them, never against the URL. Action URLs can change freely, because clients read them again on every sync. Today's evidence: the server moved from Spring MVC on Tomcat to WebFlux on Netty, and ARTIE's live scenarios passed unchanged.

- **Tests:** `content.feature` (heart-spring), `content-addressing.feature` (heart-core), live hash check, `runtime-stack.feature`

<a id="post-meaning">

### "You don’t get to decide what POST means — that is decided by the resource. Its purpose is supposed to be described in the same context in which you found the URI that you are posting to."

*Reply to TheOtherMarcus, 21 Oct*

**✅ Supports:** The purpose sits next to the target, and the answer is the next hypertext.

A form carries its title and schema beside its target. The response to an act is a full representation: what changed, and what can be done next. That matches his description of a POST answered with "another hypertext representation (telling you what happened and what can be done next)". Since 10 October the response also says, in `Content-Location`, which resource it is a representation of: the bed, not the action URL. An act on the bookmark that builds a bed is answered `201`, with the new bed in `Location` and its representation in the body. When the resource refuses, the problem's `detail` uses the same words the form does: it names the act by its relation and gives the domain's reason (`garden:sow-seed is not offered now: The bed is already planted.`), never a name from the code. A rule the schema cannot say is refused as `rule-refused`, pointing at the field (`/milliliters`: "The bed would be waterlogged: at most 600 ml now.").

- **Tests:** client `act.feature` (the manifest reflects the transition); heart-core `acts.feature` (a refusal's detail); garden `garden.feature` (building a bed, over-watering refused)

<a id="prior-knowledge">

### "Of course the client has prior knowledge. … What REST does is concentrate that need for prior knowledge into readily standardizable forms."

*Reply to jdubray, 22 Oct*

**✅ Supports:** ARTIE's prior knowledge fits on one card.

ARTIE knows HTTP, two media types, two profile URIs, two link relations (`hr:forms`, `hr:unavailable`), two `Prefer` tokens, the meaning of `409`, `412`, `422` and `428`, and RFC 9457 problem details for their bodies. ALPS and CURIEs are read only to describe an act, never to decide one. To learn what a server serves it adds one more media type, `application/json-home`, and three of its standard hints, rather than a format of this project’s own. All of it is written in spec §6, and none of it is about gardens. Both of today's decisions were about making that knowledge standardizable: naming the forms relation, and giving HAL-FORMS a clear, limited role.

- **Spec:** §6.1 decisions, §6.3 client behaviour

<a id="no-batch">

### "If you find yourself in need of a batch operation, then most likely you just haven’t defined enough resources."

*Reply to tlainhart, 22 Oct*

**✅ Supports:** The layers are more resources, not a batch call.

Instead of one big response or a bulk endpoint, the manifest and each schema are their own resources, each with its own address and cache life. L0's "everything in one response" is a rendering of the same resource, chosen with `Prefer`, not a batch operation.

- **Spec:** §6.1 layer table, §6.10.1–6.10.3

<a id="follow-nose">

### "follow your nose … Basically, how far ahead is the client looking?"

*Reply to Robert Cerny, 23 Oct*

**✅ Supports:** ARTIE looks zero steps ahead.

It keeps no script of request sequences. Each act is decided from the current manifest. A stale act is dropped, never resent. It does not even assume the rung it asked for: each response says, in `Preference-Applied`, what it is. The live full cycle (sow, water, harvest) is discovered one response at a time, at every rung and in both dialects. Under Buster, a forged `ETag` was planted 21 times where ARTIE would act next; every time, the act was refused with `412`, ARTIE re-synced, and it never resent the stale act.

- **Tests:** `live-server.feature`; client `conditional-acts.feature` (412 drops the act); `scripts/buster-campaign.sh`

<a id="key-off-content">

### "If they are keying off of something unique within the content … then it’s okay. Usually, however, what they do is assume that the response has a specific structure beyond that defined by the media type."

*Reply to Robert Cerny, 23 Oct*

**✅ Supports:** ARTIE checks the declared profile before reading the structure.

A response must name the hal-schema-forms (or hal-forms) profile in `Link: rel="profile"`, or ARTIE refuses it. Only then does it read `_forms` or `_templates`. One difference from his wording: the profile is declared in a header rather than in the body, following the W3C profile-negotiation draft. Buster tests this side of the rule from the server’s end, by changing what the content says. ARTIE was attacked 46 times in this way (curies removed, the `Preference-Applied` header removed, link `integrity` stripped, one byte flipped), and every time it declined to use what the content no longer vouched for. 44 of those 46 verdicts are also confirmed by ARTIE’s own decision log, matched by trace id.

- **Tests:** `sync-l0.feature` (three ways a missing or wrong profile is refused)

<a id="app-state">

### "Don’t confuse application state (the state of the user’s application of computing to a given task) with resource state (the state of the world as exposed by a given service)."

*Reply to TheOtherMarcus, 22 Oct*

**✅ Supports:** The ETag carries resource state; the client holds application state.

The ETag's state version is the resource state. The representation ARTIE holds, with its manifest, is the application state. A `412` is precisely the moment the two have drifted apart: someone else changed the resource. ARTIE resolves it by fetching fresh application state, not by forcing its old intent through.

- **Code:** `StateETag.kt` (heart-core), `ArtieClient` refusal handling

<a id="public-ids">

### "Identifiers are public information."

*Reply to Dima, 1 Nov*

**✅ Supports:** Content addresses are explicitly not secrets.

The spec states that a content address can be computed by anyone who can rebuild the bytes, so it must never be used as a capability. It then works through what that means for caching and confidentiality.

- **Spec:** §6.5 "Content addresses are not capability URLs", §6.9.4, §6.9.5

<a id="ai-driven">

### "All it needs to know is the meaning of those instructions and some idea of what it wants to do next, whether that purpose be user-driven, configuration-driven, or some sort of AI-driven."

*Reply to billburke, 8 Nov*

**✅ Supports:** He named the AI-driven client in 2008. ARTIE runs one, against a real server, knowing only the instructions and the purpose. (10 Oct 2026.)

ARTIE works with no model at all: a configuration-driven client is fully conformant. L6 adds an AI-driven purpose behind the same rules. The scripted tests prove a bad choice can never reach the server. Since 10 October the reasoner is shown each act’s title and description, and it may ask for the act’s ALPS description or for why an act is missing before it chooses. The first real-model run (10 October) is one scripted case against the real server: asked to “water bed 2 with 250 ml”, Claude chose `garden:water-cell` with 250, ARTIE’s checks passed it, and the server’s own history shows the watering. That is what this reply asks for: the client knows the meaning of the instructions (relation names, titles, descriptions, schemas, and no URI or method) and a purpose (a plain sentence), and the purpose can be AI-driven. An earlier version of this review held the row at Partly until a model was shown to choose *well*. That is a fair question, but it is not this sentence’s, so it now has its own row below. The caveat here is that it is shown working on one real case, not yet measured.

- **Tests:** `reason-l6.feature` (scripted); two `@llm` scenarios with a real Claude model, one against the real server, both passing
- **Plan:** `spec/EVAL-L6.md`

## Beyond the post

Questions the project asks of itself that Fielding’s rules do not. They are scored the same way, but they are not his.

<a id="model-quality">

### How often does a real model choose well, across domains and when the names change?

*SPEC §10 success metrics; plan in `spec/EVAL-L6.md`*

**🟡 Partly:** Shown working on real cases; not yet measured.

Two cases pass with a real Claude model. One is against the real server: “water bed 2 with 250 ml” led to the right act and the right value, and the server recorded it. Since then there have been many more live runs with a real model: the seven server changes of the OpenAPI-vs-HEART exhibit, the seed bench's goal runs, GitHub through the gateway, and on 25 October seven asks in words on basketball and a support desk: Claude did the three that named someone who could be chosen, refused the three that named someone who couldn't and said who could, and asked which on the one that fit two. That is still a record of runs, not a rate. The evaluation in `spec/EVAL-L6.md` would answer that. It needs five small domains, 60–75 reviewed requests with answer keys, renamed copies of each domain, and several runs per case. It would measure selection accuracy, precondition sensitivity (asking instead of doing a different legal act), clarification, structural hallucination, and cost. A scorer proven first on scripted reasoners would grade it, against a pass bar agreed before the first run.

- **Tests:** two `@llm` scenarios (`llmTest`); live runs in `exhibits/openapi-vs-heart`, `exhibits/seed-bench`, `exhibits/choices-live`
- **Open:** the eval harness, the domains and cases, the pass bar, local and hosted models

<a id="inference">

### How little does a run need a model, and is a run without one still a REST client?

*The project's goal: Fielding's rules, with minimal inference per run*

**🟡 Partly:** A model decides once; afterwards a script does the same work with none, and a changed server is caught before anything is sent. When a guard refuses, the run stops; it does not yet hand the step back to a model.

**The question.** Rule 6 asks that every transition be "driven by client selection of server-provided choices". A model reading each representation does that, at the cost of a model call per step. A script does it for free, but it names its relations in advance: `act("garden:water-cell", …)`. That is the early-bound client the OpenAPI review set aside as "failing rule 6 by its own design". So the cheap path and Fielding's path seem to pull apart, and a project that wants both has to say how they meet.

**How HEART meets them: a script is a cached decision, and its lock file is its validator.** REST already has an answer for doing work once and reusing it safely: the cache constraint. A response is reused while its validator matches and checked again when it doesn't. `artie generate` applies the same rule to decisions. A run where a model chose each step becomes a script, plus a lock file that pins, for every relation the script uses, the integrity of the form it was chosen from (HAL-SCHEMA-FORMS-HR Rule 10). Before the script sends an act, ARTIE checks the form the server offers now against the pin (`ScriptGuard`). If it matches, the cached decision still applies and is sent with no model. If it doesn't, the guard refuses and nothing is sent. Lenient mode with `on-change: values` lets a changed form through only when it kept its method and what the script sends fits its new schema. So the script is early-bound in its names but never acts on a server that has changed beneath it. Everything else a generic client does, it still does: it enters at the bookmark, acts only on forms offered in the current representation, sends `If-Match`, follows the server's targets, and reads choices from state deterministically. What it skips is only the model.

**What is deterministic now, with no model at any point:**
- choosing a form's values from state (`hr:options`), resolved from the resource or a linked one;
- a stale act decided again from fresh state (`decide { }`), its values recomputed by the script;
- noticing that something changed: listeners poll what they last read, answered 304 while unchanged, one shared poll per resource;
- waiting for a server catching up, before a strict guard refuses.

**Evidence.**
- The seed bench (`docs/reports/seed-bench/`): run b20 with Claude deciding each part of a goal once; `artie generate` wrote it as a script; run g4, the script alone, did the same work on new issues, 229 steps, none reasoned, no model called.
- `script-guard.feature`: lenient and strict pins, a changed form refused before sending; `script-replay.feature`, `script-catch-up.feature`; `decide-again.feature`.
- The listeners run (`docs/reports/listeners-live/`): three handlers watching GitHub for minutes with no model, each answering once.
- The care dial (`docs/reports/care-dial/`): what each guard costs, from careful to `--guard off-the-rails`.

**What remains.**
- **The way back to a model.** When a guard refuses because the server changed, the run stops and says why. Handing that one step back to a model, then pinning its new decision, would close the loop: inference only where the world changed. It is designed, not built (`spec/ideas/replay-guards-lenient-strict.md`, "repaired on the fly"; `spec/ideas/self-repair-without-repeating-acts.md`).
- **Not measured.** How many runs a decision serves before it must be made again, and what the first run costs, across domains. One live proof per exhibit, no rates.
- **The first run still needs a model,** or a person writing the script. That is by design: meaning that isn't in the representation has to come from someone.

- **Code:** client `ScriptGuard.kt`, `artie-cli` `generate`, heart-script `Flow.kt` (`decide`), `Listening.kt`, `Choices.kt`
- **Open:** the step back to a model on a guard's refusal; rates

**Gaps to carry forward:** CoAP's experimental option numbers, unregistered (now including 65020 for trace context), and Buster attacks only the HTTP binding (`spec/ideas/buster-over-coap.md`) (Rule 1); the draft `Accept-Profile` (Rule 2); a published but unstandardized media type and profile (Rule 6); action relations that are typed but belong to one service, with no shared vocabulary yet (the "typed relations" reply); how well a model chooses, shown on real cases but not yet measured (its own row); the step back to a model when a script's guard refuses, and how many runs one decision serves ([Inference per run](#inference)); and, to watch, the gateway's held read borrowing `Prefer: wait` (Rule 2). Resolved 8–9 October 2026: content addresses recognised by URL shape (Rule 4); no entry point (Rule 6); the `If-Match` narrowing (Rule 2). Resolved 10 October 2026: an AI-driven client working against the real server (the AI-driven reply); a protocol-neutral core with HTTP and CoAP bindings chosen by URI scheme, proven live over UDP (Rule 1); actions named by typed relations with an ALPS description (the "typed relations" reply). Resolved 12 October 2026: no trace context over CoAP (Rule 1). Resolved 14 October 2026: no `coaps` (Rule 1), now DTLS 1.2 with a pre-shared key per client. Added 15 October 2026: choices from state (the "interface in the form" reply). Resolved 25 October 2026: the act's precondition scope (Rule 2), now that an act's target has a representation of its own. Withdrawn: "template filling at L1 and L2" (Rule 6), which rested on a truncated quote.

Source: Roy T. Fielding, "REST APIs must be hypertext-driven", Untangled, 20 October 2008, and his comments on it. Quotes are verbatim. Evidence refers to `~/repos/artie` as of commit `e5c16e8`, 25 October 2026; rows not touched since are as checked at `a7831ce`, 12 October. The Buster campaign, run at that commit: 22 rounds, 125 planned mutations, all made, 74 pass, 51 pass (attested), 0 FAIL, no holes.
