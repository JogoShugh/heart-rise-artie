# Plan: the agent face-off, on a service that changes while the agents run

Status: idea, written 2026-10-19 and revised the same day: the axis is evolution. No code until the user says go.
Related: exhibits/openapi-vs-heart (the six Wednesday changes and the frost), the garden's OpenAPI twin (planned
2026-10-11), "OpenAPI at its best", PLAN-SCRIPT-GENERATOR.md (lock files, guards), SPEC.md §6.5 and §6.9
(Layers 2 and 3, content addressing).

## The question

A service changes. URLs move, names change, limits tighten, a new input becomes required, a new state appears and
forbids an act. **Which agent keeps doing the task correctly, at what cost, with no retraining and no change to its
own code?**

That is the axis HEART competes on. A happy path against an unchanging, famous API proves little: there, a model's
training or a hand-made tool bag does about as well. Evolution is where a contract made once (the training cut-off,
a generated tool list, a client built from Monday's spec) goes stale, and where a client that reads what is allowed
now, at run time, should not.

**The bet on cost:** the optimiser is caching, Layers 2 and 3 above all.
- Affordance sets and schemas are content-addressed and immutable, so a client fetches each one once, ever.
- A client can also key its own decisions to the address of what it was offered. While a step's affordance set is
  unchanged, the model is never asked about it again.
- When the service evolves, the addresses change, and only the changed steps cost tokens. The rest stay cached,
  with no retraining and no code change.

## In short

**The expected winner is ARTIE over HEART, on robustness and on tokens. This is a prediction, not a result, and
the token win depends on caching.**

- **Robustness: a clear win, expected.** Under every kind of release, ARTIE should stay correct. The nearest
  rival, B2 (a tool bag rebuilt from the live OpenAPI), keeps up with shape (renames, moved paths, new schemas) but
  not with state rules such as "no planting in a frost": OpenAPI can say those only in prose, while HEART simply
  doesn't offer the act.
- **Tokens: a clear win only with caching (A2, A3).**
  - Plain ARTIE (A1) reads each step's representation and forms, about 1–3k tokens. On a small API like the
    garden, that is close to B2's cost: no clear win.
  - The big win comes from the decision cache or a generated script. The model is then asked only about steps
    whose affordance set changed, and costs almost nothing otherwise.
- **Where it loses: latency.** Layer 1 is revalidated on every step, a round trip the others skip.
- **The real risk:** B2 may come close on releases that only change shape. If it does, that is reported.

### The variants

| Variant | Who | What it sees | How it notices a release | Model calls |
|---|---|---|---|---|
| **A1** | ARTIE over HEART, reasoning each step | The current state and the forms allowed now | Every step reads fresh forms | Every step |
| **A2** | A1 with caching: Layers 2 and 3, plus the decision cache | The same, cached by integrity | A changed integrity misses the cache; only that step is re-reasoned | Only steps whose affordance set changed |
| **A3** | A script from `artie generate`, held to its lock file, with the reasoner as fallback | The same, compiled | The guard refuses a changed form; the reasoner decides that one step | Only refused steps |
| **B1** | An MCP tool bag from Monday's OpenAPI, frozen | Every tool, every turn | It doesn't: 4xx answers, then retries | Every step |
| **B2** | An MCP tool bag rebuilt from the live OpenAPI on change (`tools/list_changed`): OpenAPI at its best | Every current tool, every turn | A new tool list: renames, paths, schemas; never state rules | Every step |
| **C** | The model's training, with a raw fetch tool | The base URL only | Probing and 4xx answers | Every step, plus retries |
| **D** | HEART through heart-mcp | Tools that are the forms allowed now | A new tool list per state | Every step |
| **X** | Control: a script naming `garden:water-cell` and `milliliters` | Nothing | It doesn't; it breaks on renames | None |

### Predicted ranking (not measured)

| | 1st | 2nd | 3rd | Last |
|---|---|---|---|---|
| **Robustness** | A1 ≈ A2 ≈ D | A3 (refused, never wrong) | B2 | X, B1 |
| **Tokens, no release** | A3 ≈ A2 (near zero) | A1 ≈ D | B2 | B1 |
| **Tokens at a release** | A2 ≈ A3 (only what changed) | A1 ≈ D | B2 | C, B1 |
| **Latency** | B2, C | B1 | D, A3 | A1 |

The face-off exists to turn these predictions into numbers; until it runs, none of this is a claim.

## What stays fixed (enforced, not promised)

- **No retraining.** The same model and weights for the whole face-off; its cut-off is before every release we make.
- **No agent code changes.** Each contestant's code, prompts and tool definitions are hashed before the first run
  and checked after the last; a changed hash voids the contestant's results. A contestant whose tools are generated
  from the service (B2 below) regenerates them only through its own run-time mechanism, never by hand.
- **No one tells the agent a release happened.** It finds out from the service, or it doesn't.

## The evolving garden

The garden can change today, but only by restarting with `garden.change` set to a variant. The face-off needs
releases **applied while an agent is mid-task**, without restarting, and in sequence:

- **A release switch on an operator port**, never on the agent's port and never in any representation. It applies a
  release atomically between two requests. A release is a named set of the variants below. (A test-only operator act;
  the agents can't see or reach it.)
- **A release schedule** per run, written before the run: "release Wednesday after the agent's 3rd act", or "after
  40 seconds", or "between runs 4 and 5". The runner applies it; the scorer records exactly which request was the
  first to see the new release.
- **Canary mode:** for a window, half the requests are served by Monday and half by Wednesday, as a rolling deploy
  would. `ETag`s differ per representation, so `If-Match` keeps writes safe.

### Releases (each one change from the one before; combined later)

| Release | Change | What a stale client does |
|---|---|---|
| `move-url` | An act's target moves | 404 |
| `rename-field` | A state member is renamed | Reads `undefined` |
| `rename-act` | `garden:water-cell` becomes `garden:irrigate` | Calls a tool or relation that's gone |
| `retitle` | Titles and descriptions change | Picks by stale wording |
| `tighter-limit` | `milliliters` max drops from 1000 to 500 | 422 at 800 ml |
| `new-required-input` | `method` (drip or spray) is required | 422 |
| `new-optional-input` | `method` added with a default | Nothing breaks; only a rich client uses it |
| `frost` | A new state; planting isn't allowed in it | Tries anyway: 409 |
| `split-act` | `harvest` becomes `harvest-all` and `harvest-ripe` | Calls the old one |
| `removed-act` | An act is withdrawn for some states | Tries anyway |
| `new-capability` | A new act the task can use (`compost`) | Can't know it exists |

The first six already exist as restart variants (exhibits/openapi-vs-heart); they move onto the switch.

## Contestants

The variants are defined in "In short" above. Every contestant gets the same model, the same task wording, the
same starting state and the same budget, with N runs per cell. D separates protocol from client: D close to A1 means the gain is hypermedia's, not ARTIE's.

### The decision cache (A2): caching taken up to the model

- **Layer 1** is revalidated on every step (`Cache-Control: no-cache`, `If-None-Match`), as SPEC.md requires.
  A 304 costs a round trip and no bytes.
- **Layers 2 and 3** are fetched once per address and never again (`immutable`). Sets are shared across resources
  in the same state, so ten beds in the same state cost one fetch, not ten.
- **The decision** the reasoner made for an intent is cached under the intent's words, with their slots, plus the
  integrity of the affordance set it chose from. The next time the same intent meets the same set, the client
  replays the decision: the same relation and the same bindings, filled from the current state. It never builds a
  URI from memory; the target always comes from the current form.
- **On a release**, a changed set has a new integrity, so the cache misses and the model is asked, for that step
  only. A form whose own integrity is unchanged inside a changed set can be replayed leniently, with the warning
  the guards already give (HAL-SCHEMA-FORMS-HR Rule 10).

A3 is the same idea, compiled. The generated script is the cache, its lock file the keys, and a guard refusal the
miss. New: on a miss, ask the reasoner for that step alone, then offer to regenerate the script. That is
self-healing, with no human and no code change.

## Tasks

- **Garden (primary):** keep beds 1–3 watered within limits for a simulated week, harvest what's ripe, and plant
  when allowed. Long enough for several releases to land mid-task.
- **GitHub (secondary, the fake GitHub through the gateway):** the issue → branch → commit → pull request → merge →
  comment → close task, with releases made by editing the gateway's connector (a renamed field on `github:close`, a
  new required input on merge). This is the live counterpart of TODO.md's "a changed form, live".
- **A last live round** on the scratch repository, JogoShugh/heart-github-test, only. Nothing goes anywhere else.

## Schedules

| Schedule | What it tests |
|---|---|
| S0: no release | Baseline: the steady-state cost of each contestant |
| S1: one release between runs | Monday run, then a Wednesday run: adapting cold |
| S2: one release mid-task | Adapting while the task is under way |
| S3: a release every k steps (all eleven, cumulatively) | Drift that never stops; the cost of staying correct |
| S4: canary window | Two versions at once; safety under a rolling deploy |
| S5: S0 repeated 10 times, then S2 | Steady-state cost after warm-up, then what one release costs warm |

## Measures (pre-registered, before any run)

The garden scores everything from its own event history, never the contestant.

| Measure | How |
|---|---|
| Task success | End state checked by the garden |
| Survived the release | The first act after the release is correct, with no 4xx |
| Wrong attempts | 4xx answers; calls to operations, tools or fields that no longer exist |
| Unsafe acts | An act the state didn't allow, on the wrong resource, or one the task didn't ask for |
| Adaptation cost | Tokens and model calls spent from the release until the next correct act, minus the steady-state cost |
| Steady-state cost | Tokens and model calls per step with no release, after warm-up (S5) |
| Context per decision | Tokens of tool, schema and representation text the model read at each decision |
| Cache behaviour | 304 rate on Layer 1; Layer 2 and 3 hits and fetches; decision-cache hits and misses; bytes on the wire |
| Latency | Per step and per run |
| Code untouched | The contestant's hash before and after, equal |

## Estimates before measuring

These are written down so the runs can prove them wrong. They come from what each approach puts in front of the
model, the six-change exhibit (the ARTIE dashboard watered under 6 of 6 changes; Claude through it under 5,
asking a real question under the 6th; the static bespoke client under 4), and the live GitHub runs (a reasoned
run made 77 upstream calls; the script generated from it made 65, with no model). No face-off has been run.

### Robustness under evolution

| Release kind | Holds | Breaks |
|---|---|---|
| `move-url`, `split-act`, `removed-act` | A1, A2, A3, D, B2 | B1, C, X |
| `rename-act`, `rename-field` | A1, A2, D, B2 (if it reads the new names) | B1, C, X; A3's guard refuses, then its fallback decides |
| `tighter-limit`, `new-required-input` | A1, A2, D; B2 if it reads the schema | B1, C (a 422, then guessing) |
| `frost`, state gates | A1, A2, A3, D: the act isn't offered, and `hr:unavailable` says why | B1, B2, C learn from a 409; B2's tool list has no state |
| `new-capability` | A1, A2, D see it when offered; B2 sees it in the list | B1, C, A3 (until it re-reasons) |
| Canary | A1, A2, A3, D: `If-Match` refuses a write against the other version's state | B, C: whatever wins the race |

**Overall estimate:** A2 ≈ A1 ≈ D > A3 > B2 > C > B1 > X.

- A3 sits below A1 only because it must first be refused before it re-reasons. It never acts wrong in the
  meantime, though: refused, not wrong.
- B2 is the strongest non-HEART contestant. It adapts to shape (paths, names, schemas) but not to state, which
  OpenAPI can only describe in prose.

### Token efficiency under evolution

- **Steady state (S0, S5): A3 < A2 << A1 ≈ D < B2 < C < B1.**
  - A3 and A2 approach zero model tokens per step once warm: a cached decision or a compiled step, with only the
    Layer 1 revalidation on the wire.
  - A1 and D pay a representation and a handful of forms per decision, about 1–3k tokens.
  - B pays its whole tool list every turn. It is cached in price but not in attention, and for GitHub-sized APIs
    that is tens of thousands of tokens.
- **At a release (S1–S3): A2 ≈ A3 < A1 ≈ D < B2 << C < B1.**
  - A2 and A3 pay only for the steps whose sets or forms changed; every other step stays cached.
  - B2 re-reads the whole new tool list and still learns state rules from refusals.
  - C and B1 pay in retries and probing, often without converging.
- **Over S5**, I expect A2 and A3 to cost one to two orders of magnitude less than B2 and C, and the gap to widen
  with each release, since their cost of adapting scales with what changed rather than with the size of the API.

### Where HEART may lose, said up front

- **Latency per step.** Layer 1 is revalidated on every step, so A pays a round trip the others skip. A 304 makes
  it cheap but not free.
- **A2 adds a new risk:** a cached decision replayed when the intent's meaning depended on state the integrity
  doesn't cover. Rule: a decision is cached only when every value it sent is bound from the words, inputs or the
  current state, never a frozen judgement. (This is the same line `artie generate` draws, where a value marked
  "review:" isn't replayed blindly.)
- **The guardrail is only as good as the server's state model.** A release that wrongly offers a dangerous act is
  not caught.
- **B2 could come close on pure shape changes.** If it does, that is a fair result and gets reported.

## Fairness rules

- B2 is OpenAPI at its best: generated from the live description, refreshed on change, with good descriptions. C
  may probe. All contestants retry.
- The same model, temperature, turn budget and schedule for every contestant; order randomised.
- Raw captures (YAML), every exchange and the scorer's history are published. Results are reported whichever way
  they fall, including where HEART loses.

## The report

`docs/reports/agent-face-off/`, in the heart-run-report style:

- **Tiles per contestant.**
- **A release × contestant grid:** survived, refused, wrong or unsafe.
- **A cost timeline per schedule:** tokens per step, with each release marked, so the steady state, the spike at a
  release and the return to steady state are visible for each contestant.
- **Cache panels:** Layer 1 304s, Layer 2 and 3 fetches, and decision-cache hits.
- **Drill-downs:** line by line per run; for A3, the derivation report of its script, and what the fallback
  re-derived after each release.

## Work, in order (each test first)

1. **The garden's release switch:** an operator port, atomic, scheduled; the six existing variants moved onto it,
   then the five new ones; canary mode.
2. **The runner:** same model and budget for every contestant; schedules applied at exact requests; every capture
   in YAML; code hashes checked.
3. **A2's decision cache in ARTIE:** keyed by intent and set integrity; only fully bound decisions; misses re-reason.
4. **A3's fallback:** a guard refusal hands that one step to the reasoner, and the run offers a regenerated script
   and its new derivation report.
5. **B1 and B2:** an MCP server from Monday's OpenAPI, and one that regenerates from the live description and sends
   `tools/list_changed`. Then C's harness.
6. **The garden rounds, then the fake GitHub through a changed connector, then one live round on the scratch
   repository.**
7. **The report.**

## Open choices for the user

- **Which model,** or two: one strong and one small, to see whether evolution hurts the small one more without
  guardrails.
- **N per cell,** against the API budget.
- **Whether A2's decision cache is shared** between agents, like a shared HTTP cache, so one agent's adaptation
  pays for all.
- **Whether D runs through a real third-party MCP client** (one that may ignore `list_changed`), or only our own
  harness.
