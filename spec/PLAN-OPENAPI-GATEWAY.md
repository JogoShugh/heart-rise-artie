# Plan: an OpenAPI-to-HEART gateway

*Draft, 15 October 2026. Not started; no code exists yet.*

## The idea

Put a small gateway in front of a service that only publishes an OpenAPI
description, and present that service to HEART clients as an ordinary HEART
server: a bookmark, forms with schemas, relations described in an ALPS
profile, validators, refusals as problem details, and the shared, immutable
caching layers. ARTIE, heart-ts, heart-mcp and the dashboards talk plain HEART
to the gateway and never learn that OpenAPI is underneath.

The one thing that makes the result genuinely state-aware is something the
upstream must send: a per-state list of what is allowed now. The cleanest form
is HEART's own affordance link (`Link: <…>; rel=".../rels/forms"`, see SPEC
§6.1 and the OpenAPI twin's `x-heart-affordances`). The gateway also accepts
other common shapes (an `allowedTransitions`-style array in the body), and
without any of them it runs in a clearly marked permissive mode.

## Why a gateway, not a client adapter

- **ARTIE stays generic.** Its prior knowledge stays media types and
  relations (Fielding's rules 5 and 6). An OpenAPI binding inside ARTIE would
  put one API style's knowledge into its core.
- **Every HEART client benefits at once:** ARTIE, heart-ts, heart-mcp, the
  dashboards, Buster and the conformance kit.
- **The caching layers live where clients talk.** The gateway can serve
  content-addressed, immutable affordance sets and schemas even when the
  upstream has no such thing.

## What exists elsewhere (searched 15 October 2026)

Pieces exist; the combination does not, as far as a web search found.

- **Sirenity** compiles an OpenAPI 3.1 document into a Siren engine inside the
  application. The app passes in the capabilities per request, so the state
  rules stay in the app. It is a library, not a gateway, and proprietary.
- **hydra-openapi-parser** turns OpenAPI into a Hydra API description at design
  time.
- **Zuplo** can rewrite responses into HAL: links, not forms or state.
- **Spring HATEOAS** emits HAL-FORMS from annotated server code, not from a spec.
- **x-openapi-flow** adds state-machine metadata to OpenAPI at design time
  (SDKs and docs); nothing at run time.
- Teams hand-roll **allowed-transition lists** in responses (an
  `allowedTransitions` array; commercetools' state transitions).

Nothing found combines a gateway in front of an unchanged service, per-state
affordances read at run time, a standard forms media type, and immutable
shared layers.

## Technology

### Decision (15 October 2026): http4k as a second HEART binding, then the gateway on it

The author chose to build HEART's HTTP binding on http4k as a library first,
`heart/heart-http4k`, beside heart-spring, and then the gateway,
`heart/heart-gateway`, on that library.

- **Kotlin, in the heart build.** heart-core is framework-free (Jackson, the
  JSON Schema validator, JCS canonicalization, content addressing, the act
  checks), so both modules reuse it directly and stay in step with the
  profile.
- **heart-http4k: a full second binding.**
  - It turns declared `HeartResource`s into one `HttpHandler`, with the same
    routes, negotiation, ETags and 304s, act checks, refusals as problem
    details, credentials, content-addressed layers, ALPS profiles, listings,
    OpenAPI description and Swagger UI as heart-spring.
  - **Acceptance:** the conformance kit (heart-tck) passes against the lamp
    served by heart-http4k on a real port, exactly as it does against
    heart-spring. Met on 15 October 2026: 23 of 23 scenarios.
  - It favours what http4k already has: static routing for the Swagger UI
    webjar, http4k's ETag parser for `If-None-Match`, its query parsing.
- **heart-gateway: on heart-http4k.** The gateway builds `HeartResource`s at
  run time from the upstream's spec, and heart-http4k serves them. So the
  gateway adds only what is new: reading the spec, calling the upstream, and
  the per-state signal.
- **http4k for HTTP, both sides.** The server, and the client that calls the
  upstream.
  - **Server as a function:** the whole gateway is a `(Request) -> Response`,
    so features can test it in memory as well as on a port.
  - **Small:** a handful of jars and fast start-up, which suits a container.
  - **One stack for both sides:** the http4k client for upstream calls.
  - **Pinned:** http4k 5.47.0.0 (February 2025), built for the same Kotlin
    line as the heart build (2.0).
- **Why not Spring for the gateway.** A gateway's resources are discovered at
  run time from a spec; Spring would add start-up time and image size without
  a matching benefit.
- **The cost of a second binding.**
  - A few pieces of heart-spring exist again in http4k form: the credentials
    and auth filter, problem responses, `If-None-Match` handling.
  - Phase 0 moves the framework-free ones (the credential check, problem
    bodies) into heart-core, so both bindings share them. That move is a
    refactor and waits for the author's refactor mode.

### Alternative considered: a standalone gateway project

A separate repository or build outside `heart/`. It is simpler to release on
its own, but it drifts from heart-core. Not recommended until the profile
settles.

### Container

- **Build:** a multi-stage Dockerfile.
  1. A Gradle build stage produces the gateway's distribution.
  2. The runtime stage is a slim JRE 21 image (Eclipse Temurin), run as a
     non-root user.
- **Port:** the container exposes one port, 8090 by default.
- **Health:** a `HEALTHCHECK` on `/` (the bookmark).
- **Configuration** is by environment, with an optional connector file
  (below). Secrets come from the environment or a mounted file, never baked in:

  | Variable | Means |
  |---|---|
  | `UPSTREAM_URL` | The service's base URL |
  | `UPSTREAM_SPEC` | The spec's URL, or a path to a mounted file |
  | `GATEWAY_CONNECTOR` | Path to one API's connector folder |
  | `GATEWAY_PUBLIC_URL` | How clients reach the gateway |
  | `UPSTREAM_AUTH` | How the gateway calls the upstream (below) |
  | `UPSTREAM_TOKEN`, `UPSTREAM_USER`, `UPSTREAM_PASSWORD` | The gateway's own credential at the upstream |
  | `GATEWAY_PROFILE` | The profile name its relations are named in, such as `garden`; a connector names its own |
  | `PORT` | The port the gateway listens on; 8090 by default |
  | `HEART_AUTH_BEARER_TOKENS`, `HEART_AUTH_BASIC_USERS` | Who may call the gateway, as on every HEART server |

- **Compose example:** the gateway in front of the garden's plain JSON twin, so
  the first spike runs with one `docker compose up`.

## How the gateway maps OpenAPI to HEART

### Resources, from the spec plus a connector file

The gateway reads the spec at start-up and revalidates it by ETag
periodically. A new upstream release changes its forms without a gateway
restart.

- **Inference by convention.**
  - A `GET` on a path ending in a parameter (`/pets/{petId}`) that returns an
    object is an **item** resource.
  - A `GET` on its parent path (`/pets`) is a **search**.
  - A `POST` (or other unsafe method) under the item's path is an **act** on
    that item.
  - An unsafe operation with no item parameter is an **act the bookmark
    offers**. Its result is a new resource; this is not a create operation,
    the same rule HEART servers follow.
- **The connector file.**
  - It overrides the conventions where a spec doesn't follow them: which `GET`
    is the item, which parameter is the id, which field holds the state, and
    which operations belong together.
  - It also names the per-state signal (below).
  - It is optional for specs that follow the conventions.

### Representations

- **The bookmark.** It is built from the spec:
  - a search form per search operation;
  - a lookup form per item `GET`, with its `{id}` template and a template
    pointer;
  - a form per bookmark act.

  It is served in both dialects and as JSON Home, like any HEART service index.
- **An item.** The gateway `GET`s the upstream resource and wraps its JSON as
  the state.
  - It adds `_links.self` (the gateway's own address), the profile link, and
    the item's forms: inline at L0, by link at L1 and L2.
  - It adds the forms explanation link where the upstream explains absent acts.
- **Forms.** Each operation becomes a form:
  - the `summary` is its title, and the `description` its description;
  - the request body schema is its schema (OpenAPI 3.1 is JSON Schema 2020-12,
    so it passes through; 3.0 is translated);
  - path parameters become template variables, filled by template pointers
    from the item's state where the state has the value;
  - the target is the gateway's own act address,
    `/pets/{petId}/actions/{operationId}`, so operations stay visible in logs,
    gateways and metrics.
- **Relations.**
  - Each operation's relation is its `operationId` under a per-service profile
    the gateway serves: `/profiles/{service}`, an ALPS document generated from
    the operations' summaries and descriptions.
  - ARTIE's `describe` therefore works.
  - The connector file can rename relations, or map them to existing ALPS
    vocabularies.
- **Links between resources.** The spec's `links` objects (operation-to-
  operation, with runtime expressions) become HAL links on the item.
- **Shared layers.**
  - Affordance sets and schemas are computed from the spec, canonicalized,
    content-addressed and served `immutable`, exactly as heart-core does.
  - A set is keyed by the set of operations a state allows, so two items in the
    same state share one set.
  - This needs a small change in heart-core: today `Offers` lists every key in
    advance, and the gateway learns keys as it sees them. Phase 1 adds that.

### Which acts are allowed now

In order of preference, chosen per service in the connector file:

1. **The HEART affordance link.**
   - The upstream sends `Link: <…>; rel="https://w3id.org/heart-rise/rels/forms"`
     (with `x-heart-affordances` in its spec), as HEART's own OpenAPI twin does
     when `HEART_AFFORDANCE_LINKS=true`.
   - The gateway fetches that set, checks its integrity, maps its forms to
     operations by method and path template, and offers exactly those.
2. **A signal in the upstream's own answer, read by a connector.** Real APIs
   that publish only OpenAPI often say per resource what is allowed now, each
   in its own way (researched 15 October 2026):
   - a **names list**: a field of allowed action names;
   - a **boolean map**: Google Drive's `capabilities` (`canTrash`, `canDelete`,
     ...), Box's permissions;
   - a **link array**: PayPal's `links`, each with `rel`, `method` and `href`;
   - **OData annotations**: OData 4.01's `"#Namespace.Action": {}` for
     available and `null` for not, the one formal standard for this;
   - a separate call (Jira's `/transitions`, Salesforce's record actions) is
     left for later.

   The gateway reads the first four with no code, from a connector (below),
   and offers what they allow. A connector can hide the signal from the
   state, so the controls appear only as forms.
3. **A state table** in a connector, for an upstream that says nothing: "when
   `status` is `OPEN`, `close` is allowed". It is early binding, in the
   gateway rather than the client, and the least preferred signal; it is the
   only one besides the affordance link that can say why an act is absent.
4. **Permissive mode.**
   - No signal at all: every operation under the item is offered.
   - The gateway says so in the representation: a profile link
     `https://w3id.org/heart-rise/gateway/permissive`, with an identifier page
     added to the site.
   - Clients and reports never mistake it for real HEART. A refusal then comes
     from the upstream, mapped as below.

### Connectors: a folder per API, readers that recognize answers

A connector says how one upstream API signals what is allowed. Connectors are
small, well-named files, so that someone who knows an API can add or fix its
connector in a pull request that touches only that API's folder.

Most APIs signal one way everywhere, so most connectors are one default
reader. Standards need no connector at all: the gateway reads HEART's
affordance link, and OData 4.01's advertised actions, by itself.

```
connectors/
  google-drive/
    connector.yaml            the API: its profile, and its readers in order
    file.reader.yaml          the API's default reader: matches every answer
  acme/
    connector.yaml
    vault-door.reader.yaml    matches vault doors only, listed first
    door.reader.yaml          the default, listed last
    door.allowed.jsonata      an expression, beside the reader that uses it
```

- **Choosing the API is explicit**: `GATEWAY_CONNECTOR` names its folder.
- **Choosing the reader is by sniffing**: each reader says which upstream
  answers it recognizes, and the first that does, reads it. A reader's
  `matches` recognizes an answer by:
  - `body`: members at JSON Pointers equal to values, or present at all
    (Google Drive's resources carry `kind: drive#file`, Stripe's `object`);
  - `mediaType`: the answer's media type;
  - `path`: the description's path template the answer was asked at.

  Every test a reader has must hold. A reader with none matches every
  answer: the API's default, listed last.
- **Order is explicit**: `connector.yaml` lists its readers, and the first
  that recognizes an answer reads it. A reader file that isn't listed, or a
  listed one that isn't there, stops the gateway at start.
- **A reader's signal** is one of `names`, `booleans`, `links`, `odata`,
  `table`, or `jsonata: <file>`, the escape hatch for a shape the others
  don't cover, with the upstream's names `map`ped to operationIds where they
  differ, and the JSON Pointers to `hide` from the state.
- **JSONata** (dashjoin's jsonata-java: no dependencies, a 1:1 port of the
  reference implementation, with a time and recursion bound per evaluation)
  is the only expression language. An expression maps the upstream's JSON to
  the operationIds allowed now; it has no I/O. Kotlin scripts, as in Buster,
  were considered and left out: they run with full JVM power, which suits a
  development tool, not a gateway loading connectors others wrote.
- An answer no reader recognizes, with no standard signal, is refused: the
  gateway never guesses what is allowed.
- The gateway refuses to start on a reader that names an operation the spec
  doesn't have, or a path it doesn't serve, naming the file.
- When a signal gives reasons for absent acts (a table, or the affordance
  link), the reasons are part of the offer key, so two answers that allow the
  same acts for different reasons explain themselves differently, and equal
  reasons still share one affordance set.

In the gateway, each signal is one small class behind one interface, with
its terms named by type aliases so that their representations can change
without changing every signal:

```kotlin
typealias OperationId = String
typealias Reason = String
typealias Rel = String
typealias UpstreamBody = JsonNode
typealias UpstreamLinks = Map<Rel, Uri>
typealias AllowedOperations = Set<OperationId>
typealias AbsenceReasons = Map<OperationId, Reason>

/** What one upstream answer allows now, and why others are absent where the signal says. */
class Allowed(val operations: AllowedOperations, val reasons: AbsenceReasons = emptyMap())

/** One way an upstream signals what is allowed: the affordance link, or a connector's signal. */
fun interface AllowedNow {
    fun of(body: UpstreamBody, links: UpstreamLinks): Allowed
}
```

### Conditional acts

- **The upstream supports ETags and `If-Match`.**
  - The gateway passes the client's `If-Match` through, translated to the
    upstream's ETag.
  - It names each item's representation by an ETag of its own that includes
    the upstream's.
  - The 412 guarantee holds end to end.
- **The upstream doesn't.**
  - The gateway computes an ETag from the upstream representation and the
    offered set.
  - On an act it re-reads, compares, and forwards only on a match.
  - Another write can land between that check and the forward. The profile says
    so (`check-then-act`), and the gateway never claims the stronger guarantee.
- **Either way:** an act without `If-Match` is refused with 428, as on every
  HEART server.

### Refusals

| Upstream | HEART |
|---|---|
| 400 / 422 | 422 `invalid-input` (or `rule-refused` where the connector says a status means a rule) |
| 409 | 409 `not-offered` |
| 412 | 412 `stale` |
| 404 | 404 |

- Upstream `application/problem+json` bodies are kept as the `detail` and
  `violations` where they fit.
- Network failures and 5xx answers become 502 or 504 with a problem body.

### Authentication

- **Who may call the gateway:** HEART's own credentials (`HEART_AUTH_*`), as on
  every HEART server.
- **How the gateway calls the upstream:** `UPSTREAM_AUTH` is one of:
  - `bearer` or `basic`, from the environment or a mounted file;
  - `none`.
  - `forward` (pass the client's `Authorization` header through) is left out
    of the first version, by the author's decision of 15 October 2026. heart-core's
    resource calls carry no request, so forwarding needs a request context in
    every binding. It waits until an upstream needs to know each caller.
- Credentials are never logged or echoed. The same rules as ARTIE's credential
  sources apply.

### Observability

- **Tracing:** the W3C `traceparent` is passed through to the upstream and
  logged with each request.
- **Logs:** access logs show the gateway's act addresses
  (`POST /pets/42/actions/placeOrder`), so operations can be filtered without
  reading bodies.

## Phases

Each phase is feature-first, with a red run that fails at the test level
before any production code, then green across every suite.

0. **heart-http4k, then shared pieces into heart-core.**
   - heart-http4k passes heart-tck (done, 15 October 2026). Features for what
     the conformance kit doesn't cover: plain JSON, `?asOf=` and history,
     listings, the OpenAPI description and `/docs`.
   - Move the framework-free parts both bindings need (the credential check,
     problem bodies) into heart-core, in refactor mode.
   - No behaviour change; every suite stays green.
1. **Dynamic offer keys in heart-core.**
   - Affordance sets keyed as they are first seen, still content-addressed and
     shared.
   - Features in heart-core.
   - Done, 15 October 2026: `Offers(open = true)`, `open-offers.feature`.
2. **The spike: the gateway in front of the garden's plain JSON twin**, with
   affordance links on.
   - In-memory features (the bookmark, an item, a form, an act, a 412).
   - Then the conformance kit (heart-tck) run against the gateway. Done, 15
     October 2026, in memory, in front of the lamp's plain JSON twin: 23 of 23.
     The in-memory features above are covered by it for now.
   - Then the exhibit: ARTIE, unchanged, through the gateway on the frost suite
     and the six Wednesday changes. The frost suite is done, 15 October 2026
     (`exhibits/openapi-vs-heart/run-gateway.sh`): Monday 3 of 3 and Tuesday
     morning 7 of 7, the same as ARTIE against the native garden.
   - The six Wednesday changes, done the same day
     (`exhibits/openapi-vs-heart/run-gateway-changes.sh`, results in
     `results/changes-gateway/`): the ARTIE dashboard watered after all six,
     as natively. ARTIE driven by a script that names `garden:water-cell`
     watered after five; it was refused 422 under new-required-input, as
     natively. One result differs, for a reason: under rename-act the native
     script fails, because the garden renamed its HEART relation to
     `garden:irrigate`. The garden's OpenAPI operation is unchanged
     (`waterCell`, same path). The gateway names relations after operation
     ids, so through it the relation is still `garden:water-cell` and the
     script waters. A gateway's vocabulary is the upstream's OpenAPI
     contract, and that contract didn't change.
   - **Acceptance:** the same results as ARTIE against the native HEART garden.
3. **Connectors, then permissive mode.**
   - The connector loader and the five signals (names, booleans, links, OData,
     table) and the JSONata escape hatch, each with a scenario against a small
     in-memory upstream that signals that way, and heart-tck run through the
     gateway in front of it.
   - Then permissive mode, with its own profile.
   - The cost of each is measured and reported, not argued.
4. **A third-party spec.**
   - The classic Petstore OpenAPI 3 document against its public demo server,
     or a local copy, with a small connector file.
   - Shows it works on an API we don't control, and finds where the
     conventions fail.
5. **Container and compose.**
   - The Docker image, the compose example, a health check, configuration by
     environment.
   - A live run from `docker compose up`.
6. **Write-up.** A report on Pages: what the gateway does, the measured cost of
   each signal, and the ask of API owners (send the link).

## Out of scope for the first version

- **CoAP:** the gateway speaks HTTP only.
- **Choices from state (`hr:options`):** OpenAPI has no source for them, beyond
  fixed `enum`s.
- **OpenAPI 2.0 (Swagger):** it could be converted to 3.x first with existing
  tools.
- **Writing back to the upstream's spec.**

## Risks

- **Conventions vs real specs.** Real specs vary a lot (ids in the body,
  actions as `PATCH` of a status field). The connector file absorbs that, but
  some APIs will need a lot of it.
- **The weaker guarantee without upstream `If-Match`.** It must stay visible in
  the profile, never hidden.
- **Two framework stacks in the heart build.** Phase 0 keeps the shared part in
  heart-core so they can't drift.
- **Permissive mode looks like HEART but isn't.** The profile link and a
  distinct identifier keep that honest.
