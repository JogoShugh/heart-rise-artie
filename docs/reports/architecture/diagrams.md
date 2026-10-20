# HEART architecture diagrams

The Mermaid sources of [the architecture report](index.html), October 20, 2026. Paste any block into Markdown that renders Mermaid.

## 1. The system

```mermaid
flowchart TB
  subgraph People["People and agents"]
    person["A person"]
    claude["Claude, as reasoner"]
    host["An MCP host<br/>Claude Code, Claude Desktop"]
  end
  subgraph Clients["Clients"]
    artie["ARTIE<br/>Kotlin client library"]
    cli["artie CLI<br/>run, generate"]
    dash["ARTIE dashboard<br/>exhibit"]
    mcp["heart-mcp<br/>MCP server over ARTIE"]
    ts["heart-ts<br/>second client, TypeScript"]
  end
  subgraph Services["HEART services"]
    garden["Garden<br/>server-spring, HTTP and CoAP"]
    bball["Basketball<br/>domain"]
    tickets["Tickets<br/>domain"]
    gateway["heart-gateway<br/>any OpenAPI service as HEART"]
  end
  github["GitHub REST API"]
  buster["Buster<br/>chaos filter, optional"]
  person --> cli
  person --> dash
  host --> mcp
  cli --> artie
  dash --> artie
  mcp --> artie
  artie -. reason steps .-> claude
  artie --> garden
  artie --> bball
  artie --> tickets
  artie --> gateway
  ts --> garden
  buster -. tampers with answers .- garden
  gateway --> github
```

## 2. The server library

```mermaid
flowchart TB
  subgraph Core["heart-core"]
    decl["Declaration<br/>resource types, states, acts, inputs"]
    pipeline["ActPipeline<br/>preconditions, schema, domain"]
    layers["Layers<br/>L0, L1, L2 rendering"]
    nego["Negotiation<br/>HAL Schema Forms, HAL-FORMS, OpenAPI twin"]
    etag["StateETag<br/>validator per representation"]
    integrity["FormIntegrity<br/>a hash per form"]
    shared["ContentAddress, SharedContent<br/>affordance sets and schemas by hash"]
    options["Options<br/>choices from state, hr:options"]
    index["ServiceIndex<br/>the bookmark"]
    more["Embedded, SearchPages, ItemPath,<br/>Refusals, ServingLimits"]
  end
  spring["heart-spring<br/>Spring MVC binding"]
  http4k["heart-http4k<br/>http4k binding"]
  coap["heart-coap<br/>CoAP and coaps binding"]
  es["heart-eventsourcing<br/>state from events"]
  starter["heart-spring-boot-starter"]
  tck["heart-tck<br/>conformance features"]
  gw["heart-gateway"]
  decl --> pipeline
  decl --> layers
  layers --> nego
  layers --> etag
  layers --> integrity
  layers --> shared
  layers --> options
  decl --> index
  Core --> spring
  Core --> http4k
  Core --> coap
  Core --> es
  spring --> starter
  coap --> starter
  es --> starter
  http4k --> gw
  tck -. tests .-> spring
  tck -. tests .-> http4k
  tck -. tests .-> gw
```

## 3. The gateway

```mermaid
flowchart TB
  subgraph Config["Read at start"]
    spec["OpenAPI description<br/>api.github.com.json"]
    conn["Connector folder<br/>connector.yaml"]
    res["*.resource.yaml<br/>acts: operation, fields, body,<br/>bind, rename, creates"]
    read["*.reader.yaml<br/>state and links from answers"]
  end
  subgraph Engine["heart-gateway"]
    decl["ActDeclaration<br/>one act, one upstream operation"]
    schema["ActSchema<br/>the form's schema from the operation's"]
    dres["DeclaredResource<br/>state, links, acts"]
    up["Upstream<br/>calls the API as the caller"]
  end
  core["heart-core<br/>on heart-http4k"]
  client["Any HEART client"]
  gh["GitHub REST API"]
  spec --> schema
  conn --> dres
  res --> decl
  read --> dres
  decl --> schema
  decl --> dres
  schema --> dres
  dres --> core
  client -- "GET, and each act by its own method" --> core
  core --> dres
  dres --> up
  up -- "the operation, renamed fields mapped back" --> gh
```

## 4. Rungs and layers

```mermaid
flowchart LR
  subgraph L0["L0: everything in one answer"]
    direction TB
    r0["Resource<br/>state, links, forms with full schemas<br/>no-cache, ETag"]
  end
  subgraph L1["L1: forms in a shared set"]
    direction TB
    r1["Resource<br/>state and links<br/>no-cache, ETag"]
    s1["Affordance set<br/>forms with inline schemas<br/>immutable, by hash"]
    r1 -- "link with hr:integrity" --> s1
  end
  subgraph L2["L2: schemas apart too"]
    direction TB
    r2["Resource<br/>state and links<br/>no-cache, ETag"]
    s2["Affordance set<br/>forms, each with its integrity<br/>immutable, by hash"]
    sc["Schemas<br/>immutable, by hash"]
    r2 -- "link with hr:integrity" --> s2
    s2 -- "$ref with hr:integrity" --> sc
  end
```

## 5. One act, end to end

```mermaid
sequenceDiagram
  participant A as ARTIE
  participant G as heart-gateway
  participant H as GitHub
  A->>G: GET issue, If-None-Match
  G->>H: GET the issue
  H-->>G: 200 issue JSON
  G-->>A: 200 state, links, set link, ETag
  Note over A: set and schemas cached by hash,<br/>checked against hr:integrity
  Note over A: guard: form pinned, values fit
  A->>G: POST /actions/comment, as its form says, If-Match, body
  G->>G: pipeline: 428, 412, 409, 422 checks
  G->>H: POST comments, body
  H-->>G: 201 comment
  G-->>A: 201 Location of the comment
```

## 6. The act pipeline

```mermaid
flowchart TB
  req["A request to an act"] --> meth{"By the act's own method?<br/>POST, PUT, PATCH or DELETE"}
  meth -- no --> e405["405, Allow names it"]
  meth -- yes --> pre{"If-Match sent?"}
  pre -- no --> e428["428 precondition required"]
  pre -- yes --> fresh{"Matches the state now?"}
  fresh -- no --> e412["412 stale"]
  fresh -- yes --> offered{"Act offered in this state,<br/>to this caller?"}
  offered -- no --> e409["409, with the reason"]
  offered -- yes --> valid{"Body fits the schema<br/>and the choices now?"}
  valid -- no --> e422["422 at the field"]
  valid -- yes --> domain["Domain does the act"]
  domain --> ok["200 new state,<br/>or 201 Location"]
```

## 7. The client

```mermaid
flowchart TB
  subgraph CLI["artie-cli"]
    run["artie run<br/>Config, LockFile, Repin"]
    gen["artie generate<br/>Capture, ScriptWriter, LockWriter"]
  end
  subgraph Script["heart-script"]
    dsl["The DSL<br/>heart, act, follow, until, check, offers"]
    nav["Navigator<br/>the contract a client implements"]
    trace["Run and Trace<br/>the capture"]
  end
  hostm["heart-script-host<br/>runs .kts"]
  testm["heart-script-test<br/>Expect, matchers"]
  subgraph Artie["artie"]
    an["ArtieNavigator"]
    ac["ArtieClient<br/>caching, revalidation, integrity"]
    bind["Bindings<br/>HTTP, CoAP, coaps"]
    dial["Dialects<br/>HAL Schema Forms, HAL-FORMS"]
    sc["SchemaCheck"]
    guard["ScriptGuard"]
    reas["ClaudeReasoner"]
    cred["Credentials<br/>env, ~/.artie/credentials, Vault"]
  end
  run --> hostm
  hostm --> dsl
  dsl --> nav
  dsl --> trace
  testm --> dsl
  nav --> an
  an --> guard
  an --> sc
  an --> reas
  an --> ac
  ac --> dial
  ac --> bind
  bind --> cred
  trace --> gen
```

## 8. Scripts, captures and lock files

```mermaid
flowchart TB
  src["Source script<br/>act with intent words, or hybrid"]
  r1["artie run<br/>with Claude"]
  cap["Capture<br/>NAME.trace.yaml"]
  gen["artie generate"]
  kts["NAME.kts<br/>no model"]
  pins["NAME.pins.yaml<br/>rung, form integrity,<br/>method, sets, links"]
  der["NAME.derivation.yaml<br/>where each value came from"]
  cfg["NAME.config.yaml<br/>guard, repin, on-change<br/>written by a person"]
  r2["artie run<br/>guarded"]
  cap2["New capture"]
  rep["Run report<br/>docs/reports"]
  src --> r1 --> cap --> gen
  gen --> kts
  gen --> pins
  gen --> der
  kts --> r2
  pins --> r2
  cfg --> r2
  r2 --> cap2
  cap2 --> rep
  cap2 -- "repin: auto" --> pins
```

## 9. The replay guard

```mermaid
flowchart TB
  start["About to act on a form"] --> pinned{"Pinned in the lock file?"}
  pinned -- "no, lenient" --> unp["Act, marked unpinned"]
  pinned -- "no, strict" --> stop1["Refuse"]
  pinned -- yes --> same{"Form integrity<br/>as pinned?"}
  same -- yes --> strict{"Strict?"}
  strict -- no --> held1["Act, held by set or form"]
  strict -- yes --> set{"Set one recorded<br/>for it?"}
  set -- yes --> held2["Act, held by set"]
  set -- no --> stop2["Refuse: another set"]
  same -- no --> mode{"Lenient with<br/>on-change: values?"}
  mode -- no --> stop3["Refuse: changed"]
  mode -- yes --> method{"Method as pinned?"}
  method -- no --> stop4["Refuse: method changed"]
  method -- yes --> fits{"Values sent fit<br/>the new schema?"}
  fits -- no --> stop5["Refuse: says which field"]
  fits -- yes --> held3["Act, held by values"]
```

## 10. How it is tested

```mermaid
flowchart TB
  subgraph Gates["bdd-gates hooks"]
    g1["Feature first"]
    g2["Red run logged,<br/>failing at test level"]
    g3["Production code"]
    g4["Green, lint and<br/>architecture checks"]
    g5["Commit ordering check"]
    g1 --> g2 --> g3 --> g4 --> g5
  end
  subgraph Suites["Cucumber suites"]
    t1["heart-core, bindings"]
    t2["heart-tck, shared by<br/>spring, http4k, gateway"]
    t3["Gateway against a small GitHub"]
    t4["ARTIE, scripts, replay"]
    t5["heart-ts conformance"]
  end
  subgraph Live["Live"]
    l1["Scratch GitHub repository"]
    l2["Captures and traces, YAML"]
    l3["Reports on GitHub Pages<br/>and as artifacts"]
    l1 --> l2 --> l3
  end
  Gates --> Suites
  Suites --> Live
```
