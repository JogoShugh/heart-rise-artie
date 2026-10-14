# From OpenAPI to HEART, side by side

The walk-through "From OpenAPI to HEART" asks two questions of a stateful API:
*what can ever be done with it?* and *what may be done to this resource, right
now?* This exhibit runs both against one real garden server. That way the two
ways of interacting with it, OpenAPI and HEART, are seen against the same
beds, the same rules and the same frost.

## What the server serves

One garden, two renderings of every bed, chosen by `Accept`:

| | Plain JSON (`Accept: application/json`) | HEART (`Accept: application/hal+json`) |
|---|---|---|
| A bed | its state alone | its state, a self link and the forms its state allows |
| Which acts are legal now | not said; read the description's prose | the forms present, and nothing else |
| An act | the same `POST`, no `If-Match` | the form's target, with the ETag as `If-Match` |
| An act the state forbids | sent, then refused: 409 | never offered; why is at `hr:unavailable` |
| A write that raced a change | lands, or 409 | 412: re-read and decide again |

From `/`, `service-desc` links the OpenAPI 3.1 description at `/openapi.json`.
It covers every operation the server could ever be asked. It is generated from
the same declarations that render the forms, and its bytes are fixed by them:

- [`openapi/garden-1.0.0.json`](openapi/garden-1.0.0.json) is Monday;
- [`openapi/garden-1.1.0.json`](openapi/garden-1.1.0.json) is Tuesday, after the frost.

The server's tests check that it serves exactly these files
(`server-spring/src/test/resources/features/openapi.feature` and `frost.feature`).
`./gradlew test -Popenapi.write=true` in `server-spring` regenerates them.

Their diff is the walk-through's "spec 1.1": `FROZEN` joins the status enum,
`/beds/{id}/actions/thaw` appears, and `info.version` becomes 1.1.0. The rule
for watering stays a sentence ("Only allowed when status is PLANTED."), which
is the only way OpenAPI can carry it.

## Monday and Tuesday

`GARDEN_FROST=true` is Tuesday. A hard frost can then freeze a planted bed.
The operator reports it with `POST /actuator/frost`: it is weather, not a
client's act, so no form offers it and the description does not list it.
A frozen bed offers only `garden:thaw`. Watering would split the roots, so it
is absent, and `hr:unavailable` says so.

## Run it

```sh
exhibits/openapi-vs-heart/scenario.sh
```

It needs Docker (for Postgres), `curl` and `jq`. It makes development
credentials (`scripts/dev-credentials.sh`), seeds the garden with bed 2
planted with tomato at moisture 40, and walks through these steps:

1. **Monday.** `/` links the description. The description lists every
   operation, with status as a closed enum of three. Bed 2 is shown in both
   renderings: the plain one is data only; HEART offers `garden:water-cell`.
2. **The team ships the frost.** The garden restarts with
   `GARDEN_FROST=true`, and the description's own diff is printed.
3. **The frost hits.** Beds 2 and 4, the planted ones, freeze.
4. **Tuesday.** Bed 2 in both renderings. The plain one says `FROZEN`, a value
   Monday's enum never listed. HEART offers only `garden:thaw`, and says why
   watering is absent, in the server's own words.
5. **The Monday dashboard presses Water.** Its rule ("water if not empty") was
   copied from the prose. The plain `POST` goes out and comes back 409.
6. **The HEART client** never shows Water. It finds Thaw in the response,
   which it was never coded for, sends it with the ETag it read, and the bed
   is planted again.

Every response is saved under `run/`, which git ignores.

## Not built yet

The clients the walk-through describes, built against Monday's description
only: a generated client with default enum handling, a dashboard with the
rules copied by hand, and an agent whose tools come one per operation from
the spec. On the HEART side there will be ARTIE and an LLM agent that reads
the forms. All of them will be scored with the same cases, per
`spec/ideas/openapi-twin-comparison.md`.
