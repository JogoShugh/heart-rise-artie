<!-- Converted from the published review (claude.ai artifact NiZdhyhpwHrhdpDgkMJgWr, v14). Keep the two in step. -->

*H.E.A.R.T. + R.I.S.E. · ARTIE · read against Roy T. Fielding, 20 Oct 2008*

# Fielding's Rules, Checked

> **Note.** The evidence below cites paths as of commit `b7f4d00`. Since then the server's HEART code moved into the library at `heart/` (`heart/ARCHITECTURE.md`); the garden server keeps only its domain and its declaration. The verdicts are unchanged: the conformance suite and the Buster campaign pass the same.

Each heading below is Fielding's own wording, from ["REST APIs must be hypertext-driven"](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven) and his replies in its comments. Under each is a short account of what the spec and today's code do about it, with the evidence. Where the work falls short, this review says so. Revised 8–9 October 2026: Rule 4 resolved, the entry point added, Rule 6 re-read in full, the `If-Match` narrowing removed, and rung discovery added (a JSON Home index and `Preference-Applied`). Revised 10 October 2026: forms named by typed relations described in an ALPS profile, refusals as RFC 9457 problem details, absent acts explained by the server, Layer 1 always read with `Cache-Control: no-cache`, act responses naming their resource in `Content-Location`, and ARTIE checked by an adversarial proxy, Buster. The spec now has a protocol-neutral core with two bindings, HTTP and CoAP, and ARTIE works live over both (Rule 1).

> "if the engine of application state (and hence the API) is not being driven by hypertext, then it cannot be RESTful and cannot be a REST API. Period."
>
> — The post's thesis. H.E.A.R.T. takes it literally for machine clients: the manifest in each response is the only source of what the client may do next.

Key: ✅ Supports the work does this · 🟡 Partly mostly, with a stated gap · 🔴 Tension the work departs from it

## At a glance

Each row jumps to its full account below. Columns are the commits this review has been checked against, all re-assessed with Fielding’s full wording.

| Axis | `c16e61b` 4 Oct · first review | `c5db0e4` 8 Oct · integrity, template pointers | `91fb71a` 8 Oct · service index | `e2a773f` 9 Oct · strong If-Match | `9a39cfc` 9 Oct · discovery, no fixed paths | `360865f` 10 Oct · typed relations, Buster | `d10c048` 10 Oct · any protocol | `4c52390` 10 Oct · CoAP binding | `b7f4d00` 10 Oct · first real-model run |
|---|---|---|---|---|---|---|---|---|---|
| [Any protocol](#r1) Rule 1 | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 | 🟡 | ✅ | ✅ |
| [No protocol changes](#r2) Rule 2 | 🟡 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Media types first](#r3) Rule 3 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Server-taught URIs](#r4) Rule 4 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [No typed resources](#r5) Rule 5 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Bookmark only](#r6) Rule 6 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Hypertext = affordance](#hypertext) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Typed relations](#typed-relations) Reply | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ✅ | ✅ | ✅ | ✅ |
| [Interface in the form](#interface) Reply | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Client decides](#client-decides) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Cool URIs](#cool-uris) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [What POST means](#post-meaning) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Concentrated prior knowledge](#prior-knowledge) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [No batch calls](#no-batch) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Follow your nose](#follow-nose) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Key off the content](#key-off-content) Reply | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [App vs resource state](#app-state) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [Public identifiers](#public-ids) Reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| [AI-driven clients](#ai-driven) Reply | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ✅ |
| [How well a model chooses](#model-quality) Project, §10 | – | – | – | – | – | – | – | – | 🟡 |

## The six rules in the post

<a id="r1">

### "A REST API should not be dependent on any single communication protocol … any protocol element that uses a URI for identification must allow any URI scheme to be used"

*Rule 1*

**✅ Supports:** A protocol-neutral core with two real bindings, HTTP and CoAP, chosen by URI scheme. (10 Oct 2026.)

The full rule allows that a "successful mapping to a given protocol may be dependent on the availability of metadata, choice of methods, etc." So depending on a protocol's details is not the problem. The problem is mixing identification with interaction. The spec now separates them. §6.0 states the core in protocol-neutral terms: read, requested and applied rendering, validator, never fresh, act, done, stale, not offered, invalid, refusal detail. It then maps each one to a binding. Any URI scheme may appear in any link. A client picks a binding by the URI’s scheme, refuses a scheme it has no binding for before sending anything, and never rewrites a URI to another scheme.

There are two real bindings. §6.1–§6.12 are the HTTP binding. §6.0.1 is a CoAP binding (RFC 7252). CoAP has no header for a profile or a preference, so the binding carries them in experimental CoAP options, one for one with their HTTP headers. A Layer 1 read carries a critical, unsafe `No-Cache` option, which no proxy can answer from its cache. The validator is 8 bytes, CoAP’s limit, and the refusals are CoAP codes with the same problem types. The reference server answers CoAP natively on UDP beside HTTP, from the same services and the same act checks. ARTIE’s core is unchanged; `CoapBinding` serves `coap:` URIs. Live, over UDP against the real server, ARTIE takes a bed through sow, water and harvest at L0 and at L2, and goes from the `coap://…/` bookmark to an act. Nothing is sent over HTTP in any of them.

What remains is narrower than the rule. The CoAP option and format numbers are experimental until registered with IANA. There is no `coaps` (DTLS) yet. HAL-FORMS, the HTTP on-ramp, is not served over CoAP. A CoAP request carries no client identity or trace context.

- **Code:** client `heart/Binding.kt`, `HttpBinding.kt`, `CoapBinding.kt`; server `coap/CoapFrontDoor.kt`, `web/BedActs.kt`
- **Tests:** `any-protocol.feature`, `coap-binding.feature` (client), `coap.feature` (server), live "Over CoAP" scenarios
- **Spec:** §6.0 "Core and bindings", §6.0.1 "The CoAP binding"

<a id="r2">

### "A REST API should not contain any changes to the communication protocols aside from filling-out or fixing the details of underspecified bits of standard protocols"

*Rule 2*

**✅ Supports:** Standard mechanisms only, used as written. (Narrowing removed 9 Oct 2026.)

Every mechanism is an existing standard: `Prefer` tokens (RFC 7240, which allows unregistered tokens), conditional requests (RFC 9110, RFC 6585), `immutable` (RFC 8246), `Link: rel="profile"` and `Accept-Profile` (W3C content negotiation by profile). Spec 0.4 dropped its private use of the media-type `profile` parameter for exactly this reason. Conditional acts follow RFC 9110 §13.1.1 as written. HTTP is stateless, so an act repeats the `Accept`, `Accept-Profile` and `Prefer` its client synced with; the server selects the owning bed’s representation from them and compares `If-Match` with that representation’s `ETag` by strong comparison, the whole value. An earlier rule read only the state number inside the `ETag` and ignored the rest, a narrowing of RFC 9110; it was removed in `e2a773f`. Declaring what a server serves adds nothing new either: `Preference-Applied` (RFC 7240) says which rung each response is at, and the service index is also served as `application/json-home`, whose link hints `formats`, `accept-prefer` and `precondition-req` were written for exactly this. OPTIONS was considered and rejected, since its response has no defined body and is not cached. The 10 October work kept to the same line: a client reads Layer 1 with the request directive `Cache-Control: no-cache` (RFC 9111 §5.2.1.4), refusals are `application/problem+json` (RFC 9457) with JSON Pointers (RFC 6901), an act’s response names the bed it represents in `Content-Location` (RFC 9110 §8.7), and every request carries a W3C `traceparent`. Things that need watching: `Accept-Profile` is a W3C draft, and JSON Home and HTTP link hints are Internet-Drafts. Using a draft mechanism is not a change to the protocol, but it may move.

- **Spec:** §6.1 "Why not a media-type parameter", §6.3 "Precondition scope"
- **Tests:** `profile-negotiation.feature`, `layer1-revalidation.feature`, `home-document.feature`

<a id="r3">

### "A REST API should spend almost all of its descriptive effort in defining the media type(s) used for representing resources and driving application state, or in defining extended relation names and/or hypertext-enabled mark-up for existing standard media types."

*Rule 3*

**✅ Supports:** The spec defines a profile, one relation and processing rules. It defines no endpoints.

H.E.A.R.T. adds a profile on top of existing media types (`application/hal+json` with HAL Schema Forms, and `application/prs.hal-forms+json`). It names its own extended relations, `hr:forms` and `hr:unavailable` under `https://github.com/jogoshugh/heart-rise/rels/`. The example server names each of its acts by a relation too (`garden:water-cell`, a CURIE for `/profiles/garden#water-cell`), and describes them in an ALPS document at that address. This is the “extended relation names” half of the rule. And it defines how to process the three layers. The garden beds and their action URLs are an example server, not part of the protocol: nothing in the spec requires `/beds` or `/actions`.

- **Spec:** §6.1 profiles and the forms relation; §6.4 layering
- **Tests:** `forms-link.feature`, `form-relations.feature` (client and server), `rendering-ladder.feature` (server)

<a id="r4">

### "A REST API must not define fixed resource names or hierarchies … allow servers to instruct clients on how to construct appropriate URIs, such as is done in HTML forms and URI templates"

*Rule 4*

**✅ Supports:** The server instructs; the client reads nothing from a URL; the spec names no paths. (Resolved 8–9 Oct 2026.)

Every act goes to a target the server sent. ARTIE's `act()` takes an action name, never a URL. Where a target is a template, the form itself says how to fill it: `"hr:templatePointers": {"id": "/id"}`, a JSON Pointer (RFC 6901) into the bed's representation, taken from JSON Hyper-Schema's `templatePointers`. That is an instruction "within media types", defined in the published H.E.A.R.T. profile of HAL Schema Forms, not a rule the client knows from elsewhere. The earlier gap is closed: ARTIE used to verify shared layers by reading the hash out of the `/sha256-` path. It now checks bytes against an `integrity` value carried in the linking representation, as Subresource Integrity does, and reads nothing from any URL. The spec no longer names paths either (9 Oct 2026): §6.5 requires only one URL per distinct body, served `immutable`, and gives the reference server’s `/sha256-` paths as an example. The reason is this rule, not URI opacity: Fielding wrote that “REST does not require that a URI be opaque” and that RESTful applications are “encouraged to use human-meaningful, hierarchical identifiers”, which the reference server’s paths are.

- **Tests:** `templated-targets.feature`, client `conditional-acts.feature` (pointers, fields, refusals), `sync-l2.feature` (a hash-shaped URL is ignored; mismatched integrity is refused)
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

Filling a template is "the user’s manipulation of those representations": the form and its template pointers come from the server, as an HTML form does, so L1 and L2 meet this rule as fully as L0. An earlier version of this review rated the rule "Partly", arguing from a "strictest reading" that required a complete link per action. That reading depended on quoting the rule without its last clause, and is withdrawn. The entry point, a real gap until `91fb71a`, is closed: `GET /` is a service index, and live scenarios go from it alone to a watered bed at L0 and L2.

What remains is the rule’s first sentence: "standardized media types … expected to be understood by any client". ARTIE’s prior knowledge is HAL, HAL Schema Forms and the H.E.A.R.T. profile of it. HAL is an IETF draft, HAL Schema Forms is a published but unstandardized specification, and the profile is this project’s own. A generic HAL Schema Forms client can use L0, which uses none of the profile’s additions, but not L1 or L2. That is a question of adoption, not of how ARTIE behaves, and it is the same theme as the typed-relations reply below. Both dialects have the entry point: HAL-FORMS cannot put a field into a target’s path, so its index uses GET templates whose fields go in the query (`/beds?name=`, `/beds?id=`), and a live scenario goes from `/` alone to a watered bed in HAL-FORMS too.

The bookmark also tells ARTIE what the server can do, without a second bookmark. Asked for `application/json-home`, the same `/` answers with a home document whose hints list the dialects and the `Prefer` values honoured. ARTIE asks for the best it offers, never more than its own setting, and then believes each response’s `Preference-Applied` over the declaration. A server just starting at L0 declares nothing and is used at L0. Live, the real server was started at L0, L1 and L2 in turn, and ARTIE found and worked at each level from `/` alone. `/.well-known/api-catalog` (RFC 9727) would go one step further, from a host name alone; it is parked, because it adds a fixed name and ARTIE is always given the index.

- **Tests:** `service-index.feature` (client and server), `act-unknown.feature`, `conditional-acts.feature`, `reason-l6.feature`; live "From the bookmark alone" at L0 and L2, full cycles at L0, L1, L2 and over HAL-FORMS, discovery against servers capped at L0, L1 and L2; client `rung-discovery.feature`. Buster campaign (10 Oct): with an offered act removed from the manifest 11 times, ARTIE never sent it
- **Open:** the media type and profile are published but not standardized

## From his replies in the comments

Fielding answered readers in the comments for three weeks. Several replies read as if written for machine clients like ARTIE.

<a id="hypertext">

### "the simultaneous presentation of information and controls such that the information becomes the affordance through which the user (or automaton) obtains choices and selects actions"

*His definition of hypertext · reply to llimllib and Bjorg, 20 Oct*

**✅ Supports:** This is where H.E.A.R.T. gets the word "affordance".

A Layer 1 response is state and manifest together, and the absence of an action is information: you cannot water an empty bed. Since 10 October the server can also say why. An `hr:unavailable` link leads to a list of acts not offered now, each with a reason, and a client fetches it only when it needs to explain. At L0 everything arrives in one response. At L1 and L2 the controls are linked rather than inline, but they are still part of the same representation, through a named relation, and a `304` proves the link is unchanged.

- **Spec:** §3.4, §6.1 three layers, §6.7
- **Tests:** `sync-l0.feature`, `sync-l2.feature`, `explaining-absence.feature`, `unavailable.feature`

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

Each `_forms` entry carries the target, the method, the content type and the schema. ARTIE builds the request from that entry and nothing else. Content addressing keeps "right there" cheap. The set and its schemas are fetched once and served from cache afterwards. Two beds in the same state cost one set fetch, proved live with two clients behind one cache.

- **Tests:** client `act.feature`, `l1-and-sharing.feature`; live L3 scenario

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

- **Tests:** `ContentAddressHttpTest`, live hash check 7/7, `runtime-stack.feature`

<a id="post-meaning">

### "You don’t get to decide what POST means — that is decided by the resource. Its purpose is supposed to be described in the same context in which you found the URI that you are posting to."

*Reply to TheOtherMarcus, 21 Oct*

**✅ Supports:** The purpose sits next to the target, and the answer is the next hypertext.

A form carries its title and schema beside its target. The response to an act is a full representation: what changed, and what can be done next. That matches his description of a POST answered with "another hypertext representation (telling you what happened and what can be done next)". Since 10 October the response also says, in `Content-Location`, which resource it is a representation of: the bed, not the action URL.

- **Tests:** client `act.feature` (the manifest reflects the transition)

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

It keeps no script of request sequences. Each act is decided from the current manifest. A stale act is dropped, never resent. It does not even assume the rung it asked for: each response says, in `Preference-Applied`, what it is. The live full cycle (sow, water, harvest) is discovered one response at a time, at every rung and in both dialects. Under Buster, a forged `ETag` was planted 19 times where ARTIE would act next; every time, the act was refused with `412`, ARTIE re-synced, and it never resent the stale act.

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

- **Code:** `StateETag.kt` (server), `ArtieClient` refusal handling

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

Two cases pass with a real Claude model. One is against the real server: “water bed 2 with 250 ml” led to the right act and the right value, and the server recorded it. That proves nothing about rates. The evaluation in `spec/EVAL-L6.md` would answer that. It needs five small domains, 60–75 reviewed requests with answer keys, renamed copies of each domain, and several runs per case. It would measure selection accuracy, precondition sensitivity (asking instead of doing a different legal act), clarification, structural hallucination, and cost. A scorer proven first on scripted reasoners would grade it, against a pass bar agreed before the first run.

- **Tests:** two `@llm` scenarios (`llmTest`)
- **Open:** the eval harness, the domains and cases, the pass bar, local and hosted models

**Gaps to carry forward:** CoAP's experimental option numbers, unregistered, and no `coaps` (Rule 1); the draft `Accept-Profile` (Rule 2); a published but unstandardized media type and profile (Rule 6); action relations that are typed but belong to one service, with no shared vocabulary yet (the "typed relations" reply); and how well a model chooses, shown on real cases but not yet measured (its own row). Resolved 8–9 October 2026: content addresses recognised by URL shape (Rule 4); no entry point (Rule 6); the `If-Match` narrowing (Rule 2). Resolved 10 October 2026: an AI-driven client working against the real server (the AI-driven reply); a protocol-neutral core with HTTP and CoAP bindings chosen by URI scheme, proven live over UDP (Rule 1); actions named by typed relations with an ALPS description (the "typed relations" reply). Withdrawn: "template filling at L1 and L2" (Rule 6), which rested on a truncated quote.

Source: Roy T. Fielding, "REST APIs must be hypertext-driven", Untangled, 20 October 2008, and his comments on it. Quotes are verbatim. Evidence refers to `~/repos/artie` as of commit `b7f4d00`, 10 October 2026. The Buster campaign: 22 rounds, 120 planned mutations, all made, 70 pass, 50 pass (attested), 0 FAIL.
