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

## Early binding against late binding, run for real

Two dashboards look after the same garden. Both are small htmx web pages, so
the only difference between them is when they bind to what the garden can do.

- **The bespoke dashboard** (`bespoke-1.0/`, TypeScript). Its client is generated
  from `garden-1.0.0.json` by OpenAPI Generator 7.8.0 (`typescript-fetch`). Its
  page binds at build time: the statuses come from the generated `BedStatusEnum`,
  the badge from a `Record<BedStatusEnum, string>`, and the buttons from a
  `switch` copied from the description's prose (sow an empty bed, harvest a
  ready one, water the rest). It reads plain JSON.
- **The ARTIE dashboard** (`artie-dashboard/`, Kotlin, over the ARTIE client).
  It knows the garden's bookmark and nothing else: no status, no rule, no act
  name. Each card is rendered from the HEART response ARTIE just received. The
  state is a table of its members, each offered form becomes an HTML form built
  from its JSON Schema with the form's title on the button, and each card
  re-syncs every 2 seconds. It was written once, on Monday, and not changed.
  Its "Ask" box hands the same offers to Claude through `ArtieAgent`.

One regression suite (`suite/garden-care.feature`, Cucumber-JS) drives either
dashboard over HTTP, as a person would: open a bed, read its buttons and
status, press a button. Only the page objects (`suite/features/support/pages.mjs`)
know where each page puts its buttons. `run-matrix.sh` runs it at three releases.
The dashboards keep running from Monday into Tuesday; only the garden restarts.

### What happened (`results/matrix.json`)

| Release | Server | Bespoke dashboard | ARTIE dashboard |
|---|---|---|---|
| Monday | 1.0.0 | 1.0: 3/3 pass | 3/3 pass |
| Tuesday morning | 1.1.0, the frost | 1.0, unchanged: **3/7 pass** | unchanged: **7/7 pass** |
| Tuesday, after the rework | 1.1.0 | 1.1: 7/7 pass | not needed |

Tuesday morning, the unchanged bespoke dashboard:

- showed the frozen bed's status as `undefined`: `FROZEN` is not in Monday's enum;
- still showed "Water the bed", from the `switch`'s `default`;
- had no "Thaw the bed" button at all;
- pressed Water and got a real `409` from `POST /beds/2/actions/water-cell`.

The unchanged ARTIE dashboard showed `FROZEN`, offered only "Thaw the bed",
thawed the bed, and then offered "Water the bed" again. It sent nothing the
garden refused.

### The rework the bespoke dashboard needed

1. Regenerate the client from `garden-1.1.0.json`: 12 generated files changed,
   +75 −13 lines (`results/bespoke-1.0-to-1.1.generated.diff`). Most of it is
   the version stamp; the real changes are `thaw` in `BedsApi.ts` and `FROZEN`
   in `Bed.ts`.
2. Build, and read what the compiler says (`results/tsc-after-regenerate.txt`),
   one error:

   ```
   src/dashboard.ts(26,7): error TS2741: Property 'FROZEN' is missing in type
   '{ EMPTY: string; PLANTED: string; READY_TO_HARVEST: string; }' but required
   in type 'Record<BedStatusEnum, string>'.
   ```

   The compiler said nothing about the `switch`, whose `default` still gave a
   frozen bed a Water button. Only the regression suite caught that.
3. Edit by hand: 11 lines in and 4 out in `src/dashboard.ts` (the
   badge, a Thaw button, its case, its call and its route), plus the version
   in `package.json` and `generate.sh`
   (`results/bespoke-1.0-to-1.1.hand-written.diff`).
4. Build again, run the suite, release 1.1.0, and deploy it.

Until step 4, the people using the bespoke dashboard had no way to thaw a bed,
and a button that the garden refuses. The ARTIE dashboard needed none of the
four steps.

### What this does and does not show

- **The regression suite ran on both dashboards at every release.** Testing
  is not what late binding saves: a server change is tested either way. What
  it saves is the rework between the server's release and the client's:
  regenerating, fixing what the compiler finds, finding what it misses,
  editing, rebuilding and releasing, and the time users wait for the new
  capability.
- A generic client shows and performs Thaw with no code change, but something
  still decides to press it: a person on the page, or a model. With Claude,
  asked "Bed 2 looks frozen. Please look after it.", `ArtieAgent` chose
  `garden:thaw` from the offered forms, and the bed was planted again
  (`results/claude-ask.html`, `results/claude-ask-calls.json`; one run).
- Both dashboards are deliberately small. The bespoke one is written the
  common way; a team could have made it read more from the description at
  runtime, which is a step toward what HEART makes standard.

Run it again with `exhibits/openapi-vs-heart/run-matrix.sh`. It needs Docker,
Node 20 and a JDK 22 or later for the ARTIE dashboard.

## Six more kinds of change

The frost adds a state and an act, which suits HEART best. A real API also
moves URLs, renames things, tightens limits and asks for new input. So the
garden has a Wednesday release in six variants, each one change from Monday,
chosen with `GARDEN_CHANGE` (`garden.change`): `move-url`, `rename-field`,
`rename-act`, `retitle`, `tighter-limit` and `new-required-input`.

Four clients, none changed since Monday, try to water bed 2 with about 250 ml.
Three are the real contenders; the fourth is a control:

- the bespoke dashboard 1.0;
- the ARTIE dashboard, driven as a person would drive it
  (`changes/drive.mjs`): find the button whose title is about watering
  (`/water|irrigat/i`, the same rule for both dashboards), keep what the form
  shows, and press it;
- Claude, through the ARTIE dashboard's Ask, one run per variant;
- **a control:** a script that uses the ARTIE library but hard-codes the act
  `garden:water-cell` and its field `milliliters`
  (`artie-dashboard/src/main/kotlin/ArtieScript.kt`). It isn't a generic
  client; it shows what early binding costs, whatever the protocol.

The score is the garden's own history: did bed 2 get a new `CellWatered`
event? Run it with `run-changes.sh`; raw attempts are in
`results/changes/attempts.jsonl`, screenshots in `results/screenshots/changes/`.

| Change | Bespoke 1.0 | ARTIE dashboard | Claude | Control: early-bound script |
|---|---|---|---|---|
| `move-url` | 404 | watered | watered | watered |
| `rename-field` | watered, moisture shows `undefined` | watered | watered | watered |
| `rename-act` | watered | watered | watered, chose `garden:irrigate` | "No affordance 'garden:water-cell'" |
| `retitle` | watered | watered | watered | watered |
| `tighter-limit` | watered at 250 ml, 422 at 800 ml | watered | watered | watered |
| `new-required-input` | 422 | watered, with the form's `drip` | asked "drip or spray?", sent nothing | 422, `method` missing |
| **Watered, of 6** | 4 | 6 | 5 | 4 |

No generic client broke under any of the six changes. The ARTIE dashboard binds
nothing about the garden, and watered under all six. Claude reads relations,
titles and schemas at run time; it watered under five and, under
`new-required-input`, asked a real question instead of guessing. The bespoke
dashboard ignores relations and titles, so it shrugged off `rename-act` and
`retitle`, but it hard-codes the URL, the member names and the request body.
The control script broke on exactly the two names written into its code, which
is what early-bound code does; it's not a weakness of HEART.

> **Revised 14 October 2026.** The first version of this section counted the
> script as a fourth ARTIE client and concluded that "late binding protects a
> client exactly as far as the client binds late", using the script's two
> failures as evidence that some changes hurt ARTIE. The project's author
> pushed back, and was right: the script is early-bound code that happens to
> use the ARTIE library, not a generic client, and Fielding's rule 6 lets a
> client's transitions be "determined (or limited by) the client's knowledge
> of media types", which is what the generic ARTIE clients do. That claim is
> withdrawn, and the script is now a control. The Fielding assessment on the
> project's Pages site records the same change of mind.

Rework after each failure:

- **Bespoke, `move-url`:** regenerate; `tsc` reports 0 errors; rebuild and
  release.
- **Bespoke, `rename-field`:** regenerate; 1 error, `Property 'moistureLevel'
  does not exist on type 'Bed'`; rename it in the card.
- **Bespoke, `tighter-limit` and `new-required-input`:** regenerate; 1 error
  each, about the request type's name, not about the limit or the new field.
  The name changed only because each variant is a separate class; a server
  that kept it would give no compiler error. Then edit the field's `max`, or
  add a method field.
- **Control script:** one line each: `"garden:irrigate"`, or
  `put("method", "drip")`, a choice its author now has to make.

The first run of these variants found a HEART bug: the Layer 1 `ETag`
(`"seq-3.hsf.L2"`) named only the state, so after `move-url` the ARTIE
dashboard revalidated with `304`, reused its stored form, and posted to the
old target. Each `ETag` now ends with a digest of the representation
(`"seq-3.hsf.L2.e8e98768123683fa"`); the table above is from the run after
the fix.

## OpenAPI at its best

The bespoke dashboard is OpenAPI as most teams use it: generated once, then
frozen. Two additions show OpenAPI at its strongest, and the typical client
stays as it is.

- **A client that reads the spec while it runs** (`openapi-live/`, port 3004).
  No generated code: on every render it fetches `/openapi.json` (revalidated
  by ETag) and builds the bed's members, a button per non-deprecated operation
  under the bed, and each button's fields (defaults, examples, limits) from
  it. It binds the garden's URL, the spec's URL and the operation `getBed`.
- **Careful changes** (`garden.change`): `move-url-kept`, `rename-field-kept`
  and `new-optional-input`, the three breaking Wednesday changes made the way a
  careful OpenAPI team would make them. One declaration serves both HEART and
  OpenAPI, so HEART gained an act's former paths to keep the same rules.

| Test | Bespoke 1.0 | Spec-reading | ARTIE dashboard |
|---|---|---|---|
| Six Wednesday changes, bed 2 watered | 4 of 6 | 6 of 6 | 6 of 6 |
| Frost suite, Monday | 3 of 3 | 0 of 3 | 3 of 3 |
| Frost suite, Tuesday morning | 3 of 7 | 1 of 7 | 7 of 7 |

The spec-reading client copes with every change to the API's shape. It fails
on state: the spec says which acts are allowed only in prose, so it shows
every act in every state, fails each "shows exactly these buttons" check, and
offers Water on a frozen bed (409).

Under the careful changes, all five clients (bespoke, spec-reading, ARTIE
dashboard, Claude, control script) watered bed 2 under all three. The cost
moves to the server team, which keeps an old path, an old field and a
default until every old client has moved.

Run them with `LIVE_ONLY=1 ./run-matrix.sh`, `RUN=changes-best CLIENTS=live
./run-changes.sh`, and `RUN=changes-careful CHANGES="move-url-kept
rename-field-kept new-optional-input" ./run-changes.sh`. Results are in
`results/changes-best/`, `results/changes-careful/` and
`results/*--live-1.0.*`; screenshots in `results/screenshots/best/`.

## Closing OpenAPI's last gap, with a link

The spec-reading client fails only on state: the description says which acts
are allowed now only in prose. With `HEART_AFFORDANCE_LINKS=true`, each plain
JSON answer about a bed also carries
`Link: </affordance-sets/…>; rel="https://w3id.org/heart-rise/rels/forms"; integrity="sha256-…"`,
naming its state's content-addressed affordance set (the one HEART's L1 links
to). The description documents the header and, in `x-heart-affordances`, how
to use it. It's opt-in: the published 1.0.0 and 1.1.0 keep their bytes, and
the garden marks the option in its version (`1.0.0+affordance-links`).

`openapi-affordances/` (port 3005) is the spec-reading client plus that one
step: it follows the link, checks the set's integrity, and keeps only the
operations whose method and path template a form names.

| Test | Bespoke 1.0 | Spec-reading | Spec-reading + link | ARTIE dashboard |
|---|---|---|---|---|
| Frost suite, Monday | 3 of 3 | 0 of 3 | 3 of 3 | 3 of 3 |
| Frost suite, Tuesday morning | 3 of 7 | 1 of 7 | 7 of 7 | 7 of 7 |
| Six Wednesday changes | 4 of 6 | 6 of 6 | 6 of 6 | 6 of 6 |
| Three careful changes | 3 of 3 | 3 of 3 | 3 of 3 | 3 of 3 |

The last thing OpenAPI couldn't do took one link in the answer to a set of
forms: hypermedia, Fielding's rule 6. OpenAPI keeps the names, schemas and
docs; the link carries what's allowed now.

Run it with `HEART_AFFORDANCE_LINKS=true ONLY=afford ./run-matrix.sh`, and
`HEART_AFFORDANCE_LINKS=true RUN=changes-afford CLIENTS=afford ./run-changes.sh`
(add `RUN=changes-afford-careful CHANGES="move-url-kept rename-field-kept
new-optional-input"` for the careful ones).
