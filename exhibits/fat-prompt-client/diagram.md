# ARTIE — Generic Sync-Reason-Act Loop

## The failure mode Fielding complained about (anti-pattern — not what ARTIE does)

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant D as Docs / OpenAPI spec
    participant C as "REST" client
    participant S as Server

    Dev->>D: read spec once, offline
    Dev->>C: hardcode path + fields into client logic
    Note over Dev,C: knowledge baked in at dev time,<br/>not discovered at runtime

    C->>S: POST /api/v2/widgets/42/sub
    S-->>C: 200 OK

    Note over S: Server evolves — endpoint renamed
    C->>S: POST /api/v2/widgets/42/sub
    S-->>C: 404 Not Found
    Note over C: No hypertext to discover the new shape —<br/>client just breaks until Dev edits the docs and redeploys
```

## ARTIE loop, applying Fielding guidance

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant U as User
    participant A as ARTIE
    participant S as Server

    Dev->>A: update ARTIE release for<br/>H.E.A.R.T./HAL spec compliance
    Note over Dev,A: coupling is to the protocol/media type,<br/>never to any one server's domain

    U->>A: types or speaks intent only
    A->>S: Sync (GET href)
    S-->>A: state + _forms/_links
    opt ambiguous or missing field
        A->>U: clarify?
        U->>A: answer
    end
    A->>S: Act (submit affordance)
    S-->>A: result + next href
    A->>U: report outcome
    Note over A,S: loop restarts at Sync
```

## Full detail, as a flowchart

```mermaid
flowchart TD
    Start(["Bookmark URI (turn 1)<br/>or href from prior Act"]) --> S1

    subgraph SYNC["1. Sync"]
        S1["curl the resource<br/>Accept-Profile: cold=full schema, warm=hash-only<br/>+ param:behaviour=interactive"] --> S2{"Response has state<br/>+ _forms together?"}
        S2 -->|"yes"| S3["For each _forms entry:<br/>compare hash vs registry"]
        S2 -->|"only _links present"| S4{"Usable _links<br/>entry available?"}
        S2 -->|"neither"| STOP(["Stop — flag non-compliant,<br/>report to user"])
        S4 -->|"yes"| S5["Plain GET on _links.rel.href<br/>(navigation, not an Act —<br/>no schema/hash)"]
        S5 --> S1
        S4 -->|"no"| STOP
        S3 --> S6{"hash hit<br/>or miss?"}
        S6 -->|"hit — cached schema valid"| S7["resolved"]
        S6 -->|"miss/changed — dirty"| S8["Fetch full schema,<br/>store in registry by hash"]
        S8 --> S7
    end

    S7 --> R1

    subgraph REASON["2. Reason"]
        R1["Map user intent to a rel<br/>within the manifest just synced —<br/>nothing else exists"] --> R2{"exactly one<br/>matching affordance?"}
        R2 -->|"no match, or 2+"| R3["Ask the user — never guess"]
        R3 --> R1
    end

    R2 -->|"yes"| A1

    subgraph ACT["3. Act"]
        A1["Populate fields within<br/>the affordance's schema"] --> A2{"required field<br/>missing?"}
        A2 -->|"yes"| A3["Ask user for it —<br/>never default/fabricate"]
        A3 --> A1
        A2 -->|"no"| A4["Submit curl to affordance href<br/>+ hash, using schema's method"]
        A4 --> A5{"response status"}
    end

    A5 -->|"2xx"| S1
    A5 -->|"403"| E1["Tell user: unavailable now.<br/>Do not retry."] --> S1
    A5 -->|"404"| E2["Tell user: resource gone. Stop."]
    A5 -->|"405"| E3["Surface client/method bug plainly"]
    A5 -->|"409"| E4["Re-sync from scratch —<br/>do not resubmit stale act"] --> S1
    A5 -->|"422"| E5["Ask user to correct/supply fields"] --> A1
    A5 -->|"428"| E6["Evict stale hash,<br/>re-sync & re-fetch"] --> S1
```

A couple of things worth noting about how it's drawn: the `_links`-without-`_forms` branch
(`S4`/`S5`) is the HAL-navigation escape hatch added later — it loops back into Sync rather
than dead-ending. Also not shown: `DEBUG ON` logging is an orthogonal overlay that wraps
every `curl` call in this graph (screen print + YAML append) without changing the control
flow itself.
