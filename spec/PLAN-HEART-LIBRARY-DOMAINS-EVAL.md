# Plan: the HEART server library, four domains, and the L6 eval

Status: **Part A done (2026-10-11); Parts B and C not started.** Written 2026-10-10. It replaces sections 3, 4
and 10 of `EVAL-L6.md`, which planned a simulated server. The eval now runs
against real servers, and those servers are built on a library first.

There are three parts, built in this order:

- **Part A.** Distil a HEART server library out of `server-spring`, consumable
  the way Spring HATEOAS is.
- **Part B.** Build four domain servers on that library: youth basketball
  scoring, a basic accounting ledger, library loans, and support tickets.
- **Part C.** Build the eval that measures how well a model chooses, run
  against those four servers.

Part A must be finished before Part B starts. Part C's scorer can start on
the garden domain as soon as Part A is finished, in parallel with Part B.

---

## 0. What an eval is, in plain words

A test asks "does this work?" and answers yes or no, the same way every time.
An eval asks "how good is this model at the job?" and answers with a score.

It is a fixed exam:

1. Write many questions, each with an answer key. Here a question is "the
   resource is in this state, the user says this sentence; what should ARTIE
   do?" The key says: this act with these values, or ask the user, or follow
   this link.
2. Give every question to a real model, a few times each, because a model
   can answer differently on each try.
3. Grade every answer automatically against its key.
4. Report the score: by domain, by kind of question, by model.

"Eval-driven" means: when the prompt, the client or the model changes, run
the exam again and compare with the last score. A change that lowers the
score is a regression, the same as a red test.

Two rules make the score worth trusting:

- **No inconclusive answers.** Every answer gets exactly one grade from a
  fixed list (section C4). A call that fails, for example an API error, is
  retried. If it still fails, the whole run fails loudly; it is never counted
  as a grade.
- **The grader is tested before any model is.** Fake models with known
  behaviour must score exactly what is predicted (section C5). If they do not,
  the grader is wrong and no model score is believed.

---

## Part A. The HEART server library

### A1. The goal

Today every HEART behaviour lives inside the garden server, mixed with garden
code. A second domain would mean copying it and editing it, which is the
bespoke route you ruled out.

After Part A, a domain server is three things:

1. its domain code (state, events, rules);
2. one **declaration** of its resources and acts;
3. the library as a dependency.

The library then gives it everything HEART requires: L0 to L2 rendering,
content-addressed sets, schemas and absence explanations, negotiation, the
act checks, problem details, the home document, the service index, the ALPS
profile, paging, the rung cap, and the CoAP binding. This is the same deal
Spring HATEOAS offers: you describe links and affordances, and it renders the
media type.

### A2. What is generic today, and what is garden

Read from `server-spring/src/main/kotlin/com/artie/gardenbed/` at `b7f4d00`.

| File | Generic HEART | Garden-specific |
|---|---|---|
| `JsonSchemaDialectSupport.kt` | JCS, SHA-256, SRI, Repr-Digest; layer building; set and schema precomputation; unavailable resources | the three hand-written schemas; `allowedAffordances`; bed fields; `actionPath` |
| `GardenProfile.kt` | the `Relation` shape; CURIEs; the ALPS document builder | the five relations and their words; `whyAbsent` |
| `GardenBedController.kt` | rendering both dialects; ETag; `Vary`; `Preference-Applied`; `Content-Location`; every exception handler | the routes; harvest's yield headers |
| `BedActs.kt` | the check order: 428, then 412, then 409, then 422 | `run`'s `when` over the three acts; `BY_PATH` |
| `ServiceIndexController.kt` | index, home document and search paging | the search and lookup forms; `/beds` |
| `Negotiation.kt`, `Rung.kt`, `StateETag.kt`, `Problems.kt`, `ServingLimits.kt`, `ActBodyBinder.kt` | all of it | none |
| `AffordanceSetController.kt`, `SchemaController.kt`, `UnavailableController.kt`, `ProfileController.kt` | all of it | the profile path |
| `coap/*` | all of the binding | the routes (`beds`, `actions`) |
| `eventstore/*`, `repository/*`, `V1__init.sql` | appending with a sequence, snapshots, `asOf`, history | the `garden_bed_*` tables and event types |
| `domain/*`, `service/*` | none | all of it |

About 80% of the code is generic. It is only tied to the garden by names.

### A3. The modules

The library is a Gradle build at `heart/` in this repo, included by each
server the way `server-spring` already includes Buster. It can move to its
own repo later without code changes.

| Module | Depends on | Holds |
|---|---|---|
| `heart-core` | Kotlin, Jackson, titanium-jcs | The declaration model; layer rendering into plain JSON trees; content addressing; rungs; dialects; ETags; the act check order; problem types. No Spring and no protocol, as SPEC §6.0 asks. |
| `heart-spring` | `heart-core`, WebFlux, Spring HATEOAS | HTTP routes built from declarations; header negotiation; exception-to-problem mapping; HAL-FORMS through Spring HATEOAS, as today. |
| `heart-coap` | `heart-core`, Californium | The CoAP front door, routed from the same declarations (§6.0.1). |
| `heart-eventsourcing` | `heart-core`, R2DBC | Optional. A generic event store: one stream per resource, sequence numbers, snapshots, `asOf`, history. A clash on append becomes "stale". |
| `heart-spring-boot-starter` | all of the above | Auto-configuration, so adding the dependency plus one declaration bean gives a working server. |
| `heart-tck` | `heart-core`, Cucumber | The conformance features (section A6). Any domain runs them against itself. |

**Decision taken in this plan: the library has its own declaration model and
does not extend Spring HATEOAS's.** The reasons:

- Spring HATEOAS has no notion of acts offered by state, of content-addressed
  layers, or of CoAP.
- Its extension point for a new media type renders one representation. It
  cannot produce the shared, immutable Layer 2 and Layer 3 resources.

Spring HATEOAS stays where it fits: rendering the HAL-FORMS dialect, as today.

### A4. The declaration a domain writes

A sketch. The names are not final.

```kotlin
@Bean
fun gardenBeds(service: GardenBedService) = heartResource<GardenBedState, GardenBedStatus>("beds") {
    profile("garden", title = "Garden beds", doc = "The acts and searches a garden-bed service offers.")
    load { id -> service.currentState(id) }
    id { it.id }
    version { it.lastSequenceNo }
    offerKey { it.status }                 // the acts offered are a function of this key alone
    body { mapOf("label" to it.label, "status" to it.status.name, /* ... */) }

    act<WaterCell>("water-cell", title = "Water the bed",
        description = "Pours water into every cell of a planted bed.",
        doc = "Adds the given millilitres ...") {
        offeredWhen(GardenBedStatus.PLANTED)
        absentBecause(GardenBedStatus.EMPTY, "Nothing is planted yet; sow a seed first.")
        absentBecause(GardenBedStatus.READY_TO_HARVEST, "The crop is ready; it needs harvesting, not more water.")
        perform { id, input -> service.waterCell(id, input.milliliters) }
    }
    // sow-seed, harvest ...

    search("search-beds", title = "Find beds by name", by = "name") { name, page -> service.findBeds(name) }
    lookup("get-bed", title = "Get a bed by its id")
}

data class WaterCell(
    @field:Min(1) @field:Max(1000)
    @field:Title("Water per cell") @field:Description("Millilitres of water poured into each cell")
    val milliliters: Int,
)
```

Three rules come with it:

1. **One source of truth for each schema.** The JSON Schema is generated from
   the input class and its Jakarta Validation annotations, using the victools
   generator. The body is validated against that same generated schema with a
   JSON Schema validator. What the form says and what the server enforces
   cannot drift apart, and nothing is written twice.
2. **The offer key.** The acts offered must be a function of one finite key,
   so that every resource with the same key shares one affordance set (the
   sharing property, SPEC §6.9). For the garden the key is the status. Other
   domains may need a derived key. In the ledger, for example, "draft,
   balanced" and "draft, unbalanced" are different keys, because posting is
   offered only when the entry balances. The library precomputes every set
   from the key's values, as the garden does now.
3. **No HTTP in domain code.** An architecture test, like the client's
   `ArchitectureBoundaryTest`, fails the build if a domain module imports
   Spring Web, `HttpHeaders` or Californium.

Several resource types can be declared in one server, with links between them
(for example a journal line to its account). The service index and the home
document list every declared type.

### A5. How the extraction is done: always green

The move is done in small steps. After every step the whole suite stays green:

- server: 132 scenarios;
- client: 159 scenarios;
- live: 24/24, including CoAP;
- the Buster campaign: gate ok, 0 FAIL, no holes.

| Step | Work | Proof that it worked |
|---|---|---|
| A5.0 | Record the baseline counts and the campaign report. Tag `pre-heart-lib`. | The numbers are written in the hack log. |
| A5.1 | Create the `heart/` build with its modules, detekt, and the bdd-gates patterns for `heart/*/src/main` and `heart/*/src/test/resources/features`. | `.githooks/pre-commit` gates a test edit in `heart/`. |
| A5.2 | Move the pure helpers to `heart-core`: JCS, hashes, SRI, `Rung`, `Dialect`, negotiation parsing, `StateETag`, `Problems`, `Violation`. | Green, with no behaviour change. |
| A5.3 | Add the declaration model and re-express the garden in it. `GardenProfile`, the schemas, `allowedAffordances` and `whyAbsent` become one garden declaration. The layer renderer is driven by the declaration. | Green. Every content address (the set and schema hashes) is byte-identical to the baseline, checked by a new scenario. |
| A5.4 | Generate schemas from classes. | The generated schemas equal the hand-written ones, or each difference is listed and approved. The hashes change only where approved. |
| A5.5 | A generic act pipeline replaces `BedActs`: the check order, `Content-Location`, and "stale" on a concurrent append. | Green. |
| A5.6 | Generic HTTP routes built from declarations replace `GardenBedController`, `ServiceIndexController` and the four content controllers. | Green. The garden app has no `@RestController` left. |
| A5.7 | `CoapRoutes` routes from declarations, in `heart-coap`. | CoAP scenarios green, live CoAP green. |
| A5.8 | The generic event store, with Flyway V2 copying the garden rows into it. | Green; `asOf` and history scenarios unchanged. |
| A5.9 | The TCK (section A6). The garden runs it. | Green. |
| A5.10 | Proof of generality: a tiny second resource, a lamp that is on or off, declared only inside the TCK's own tests. | The lamp passes the whole TCK with zero lines of HTTP or CoAP code. |
| A5.11 | The starter, a README written like a reference guide ("a HEART server in five minutes"), and updates to `ARCHITECTURE.md` and the ADRs. | A new empty app with the starter and the lamp declaration answers `/` correctly. |

New capabilities follow bdd-gates order: feature first, a red run, then code.
Pure moves are refactors under the existing features, green before and after.

### A6. The TCK: conformance written once, run by every domain

The server's protocol features today say "bed 2" and "garden:water-cell".
They move to `heart-tck` and are reworded against a **fixture** that each
domain supplies:

> a resource in key K, an act offered in K, an act absent in K with its
> reason, a valid body for the offered act, an invalid body, and a search term
> that matches.

The features that move are: rendering-ladder, conditional-acts,
layer1-revalidation, cross-dialect, problem-details, profile-negotiation,
templated-targets, form-relations, unavailable, paging, home-document,
service-index, and coap. `runtime-stack.feature` stays with each app.

So every domain server is shown to be HEART compliant by the same scenarios,
and nothing is re-tested by hand.

### A7. A library feature the eval needs: vocabularies

The eval asks whether a model reads the manifest, or only recognises names it
has memorised. To ask that, the same server must be able to speak with renamed
relations. For example, `water-cell` becomes `irrigate`, with a new title and
description.

The library takes an optional vocabulary file, `heart.vocabulary=renamed.yaml`,
which maps each relation id, title and description to its replacement. The
domain code does not change. The answer keys are mapped through the same file,
so a key can never disagree with the server.

### A8. Part A is done when

- The garden server holds only domain code and its declaration, and the
  architecture test proves it.
- Server, client, live, CoAP and Buster results equal the baseline.
- The lamp passes the TCK.
- The library builds and publishes to `mavenLocal` as `com.artie.heart:*`.

---

## Part B. The domain servers

### B1. Shape of each domain

Each domain is its own small Spring Boot app under `domains/<name>/`. It uses
the starter, has its own port, and has its own Postgres schema in the shared
container. Separate apps prove that the library works for an outside consumer,
not only for the garden.

Every domain is delivered with the same list:

1. The domain model, event sourced through `heart-eventsourcing`.
2. The declaration: resources, acts, schemas, absence reasons, and searches.
3. Seed data and a reset, so that every scenario and every eval case starts
   from a known state, as the garden's `ARTIE_RESET` does now.
4. Domain features: the rules of the state machine.
5. The TCK, run with the domain's fixture.
6. Live ARTIE scenarios over HTTP and over CoAP.
7. A Buster campaign. `scripts/buster-campaign.sh` takes the domain as an
   argument. Gate ok, 0 FAIL, no holes.
8. The renamed vocabulary file, for the eval.

### B2. Youth basketball scoring

Rebuilt in Kotlin on the library, from `server-node/bball.js`. That file has
a score, a period, possession, pause and turnover, and nothing else.

**Resources**

- **game:** the score, the period, possession, team fouls, and the
  play-by-play as history;
- **team:** its name and roster;
- **player:** number, name, points, and personal fouls.

**Offer keys of a game:** `SCHEDULED`, `LIVE`, `TIMEOUT`, `BETWEEN_PERIODS`,
`FINAL`.

| Act | Offered in | Fields and limits |
|---|---|---|
| `tip-off` | SCHEDULED | `possession`: home or away |
| `score-basket` | LIVE | `team`, `playerNumber` 0–99, `points` 2 or 3 |
| `free-throw` | LIVE | `team`, `playerNumber`, `made` true or false |
| `record-foul` | LIVE | `team`, `playerNumber`, `kind`: personal, shooting or technical |
| `turnover` | LIVE | `team`, `reason`: steal, out of bounds, travel or double dribble |
| `call-timeout` | LIVE | `team` |
| `resume-play` | TIMEOUT | none |
| `end-period` | LIVE | none |
| `start-period` | BETWEEN_PERIODS | none, offered only before the last period ends |
| `final-whistle` | BETWEEN_PERIODS after the last period | none |
| `correct-score` | LIVE, TIMEOUT | `sequenceNo` of the scoring play to take back |

**Youth rules** (proposed defaults; you confirm them at B2's start):

- four periods;
- a player fouls out at 5 personal fouls;
- a team is in the bonus from 7 team fouls in a half.

A fouled-out player's number is refused with a reason.

**Why this domain is good for the eval.** It has many near neighbours:

- a basket or a free throw;
- a foul or a turnover;
- a timeout or the end of a period;
- correcting a score or scoring again.

It also has natural "not now" cases, for example "score for the Eagles" while
in a timeout.

### B3. Basic accounting ledger

**Resources**

- **account:** a code, a name, and a type: asset, liability, equity, revenue
  or expense. It has a balance.
- **journal entry:** a date, a memo, and lines. Each line names an account and
  has either a debit or a credit.
- **period:** a month, open or closed.

**Offer keys of an entry:** `DRAFT_UNBALANCED`, `DRAFT_BALANCED`, `POSTED`,
`REVERSED`, `VOIDED`.

| Act | Offered in | Fields and limits |
|---|---|---|
| `add-line` | DRAFT_* | `account` (a link to the account), `side` debit or credit, `amount` as a decimal string with 2 places, greater than 0 |
| `remove-line` | DRAFT_* with lines | `line` number |
| `post-entry` | DRAFT_BALANCED, and its period open | none |
| `void-entry` | DRAFT_* | `reason` |
| `reverse-entry` | POSTED | `date`, `reason`; creates the reversing entry and links to it |
| `close-period` | an open period with no drafts | none |
| `open-account`, `close-account` | on the chart; closing only at a zero balance | `code`, `name`, `type` |

Money is a decimal string with a JSON Schema `pattern`, never a float.

**Why this domain is good for the eval:**

- Invariants are enforced by the offer key. "Post it" on an unbalanced entry
  must make the model ask; the server's absence reason says why.
- There are dangerous near neighbours: void or reverse ("cancel that entry"),
  debit or credit.
- Answers need navigation across resources, from an entry to an account.

### B4 and B5. The third and fourth domains

**Chosen 2026-10-10: library loans (B4) and support tickets (B5).** The
options that were offered, and what each adds that basketball and the ledger
do not:

| Option | What it adds to the eval |
|---|---|
| Library loans: title, copy, member; borrow, return, renew (at most 2), reserve, a hold queue | dates and durations, counted limits ("renewed twice already"), a queue |
| Support tickets: new, assigned, waiting, resolved, closed; assign, comment, ask the customer, resolve, reopen | free text, people as values, a back-and-forth workflow |
| Clinic appointments: slots, booking, reschedule, cancel, check in, no-show | time slots, reschedule against cancel, rules about how close to the time |
| Café order: line items, sizes, pay, cancel, refund | arrays and enums, cancel against refund (overlaps the ledger on money) |

Library loans add dates, counted limits and a queue. Support tickets add free
text, people as values and a back-and-forth workflow. Their resources, offer
keys and acts are drafted at the start of B4 and B5, in the same form as B2
and B3, for your review.

### B6. Build order for Part B

1. Basketball, end to end through items 1 to 8 of B1.
2. The ledger.
3. Library loans.
4. Support tickets.

Each domain is committed as its own series and has a hack-log entry.

---

## Part C. The eval

### C1. What it measures (SPEC §10)

- **Precondition sensitivity (§10.2), the primary metric.** When the request
  is not legal now, does the model ask (or explain), instead of doing a
  different act that is offered?
- **Selection accuracy:** the right act with the right values.
- **Clarification judgment:** it asks when a value is missing or the request
  is ambiguous, and does not ask when everything is clear.
- **Structural hallucination (§10.3):** it names an act that is not offered,
  or breaks the schema. ARTIE blocks both. The eval counts them, and also
  proves that none reached a server.
- **Domain agnosticism (§10.6):** the scores hold across the four domains and
  under renamed vocabularies.
- **Cost (§10.1):** latency and tokens, with cache reads and cache writes shown
  separately.

### C2. A case

Cases are YAML files, kept apart from the domain code. They live under
`client/src/eval/resources/cases/<domain>/`.

```yaml
id: bb-014
domain: basketball
kind: not-legal-now            # clear | not-legal-now | missing-value | out-of-bounds | near-neighbour | journey
setup:                         # driven through the real server by scripted ARTIE acts, after a reset
  - act: tip-off  { possession: home }
  - act: call-timeout { team: away }
start: /games/1
intent: "Two points for number 23 on the home team"
expect:
  ask: true                    # any question or explanation is right; any act is wrong
why: "Play is stopped for a timeout; scoring is not offered until play resumes."
```

Expectations can say:

- an act, with fields matched exactly or within a stated range;
- ask;
- explain;
- follow a link with a given relation, then act.

A case lists every answer it accepts, and the scorer never guesses.

**Setup is done through the real server, not by writing to the database.**
This way the state a case starts in is a state the domain can really reach.

### C3. The runner

`./gradlew eval -Pmodel=claude-sonnet-5-5 -Pdomains=all -Prepeats=3` lives in
a separate `eval` source set, outside `check`. For each case, and for each
repeat of it, the runner:

1. resets the domain;
2. runs the setup;
3. runs the ARTIE agent with the real reasoner. Journeys are capped at 4
   turns.
4. reads the resource back from the server, so that "acted" means the server
   applied it;
5. records the selection, the decision log, the tokens and the latency.

A wrapper, `scripts/eval.sh`, starts the domain servers the same way the
campaign script does. It checks the ports first, never touches a server it did
not start, and stops what it started.

Offer order is shuffled by a seeded reasoner wrapper, so order bias is
measured without touching the protocol. Every run records its seed, so any
run can be reproduced.

### C4. Grades: one per answer, from a fixed list

`CORRECT`, `WRONG_ACT`, `WRONG_FIELDS`, `ACTED_SHOULD_ASK`,
`ASKED_SHOULD_ACT`, `WRONG_LINK`, `HALLUCINATED_ACT`, `SCHEMA_BREAK`.

There is no other grade. An API or transport error is retried 3 times with
backoff. After that the run stops with an error. It is never graded.

### C5. Trust the grader first

These run in the normal gated suite, as Cucumber features over the scorer:

- the **perfect** fake model, which reads the key: 100%;
- the **always-ask** fake: exactly the share of cases whose key is "ask";
- the **always-first-offer** fake: exactly the score the keys predict;
- the **hallucinating** fake: every answer graded `HALLUCINATED_ACT`, and
  zero requests reach the server.

### C6. Cases: how many, and keeping them honest

- About 18 cases per domain, covering every kind, so about 72 in all. Each
  case also runs under the renamed vocabulary, which gives 144 variants.
- With 3 repeats, that is about 430 model calls per model per run.
- Claude drafts the cases. You review them: all of them, or a sample (to be
  decided).
- **A held-out split.** About a third of the cases are marked held-out. When
  the system prompt is tuned, only the other cases are looked at. The held-out
  score is the honest one, because nothing was tuned to it. Without this split
  the eval only measures how well the prompt was fitted to the exam.

### C7. The report

Each run produces:

- a JSON result file;
- an HTML report, published as a private artifact.

The report shows scores by domain, by kind and by model, with the spread
across repeats. It shows every wrong answer with its full prompt and answer.
It also compares the run with the committed baseline:
`client/src/eval/baseline/<model>.json`.

### C8. The pass bar, agreed before the first real run

Proposed:

| Measure | Bar |
|---|---|
| Hallucinated acts or schema breaks that reached a server | exactly 0, a hard gate |
| Precondition sensitivity, held-out | at least 95% |
| Clear-match accuracy, held-out | at least 95% |
| Clarification judgment, held-out | at least 90% |
| The renamed vocabulary against the original | no more than 5 points lower |

When a model meets the bar, the "How well a model chooses" row in the Fielding
review turns ✅, citing the report.

### C9. Models

1. Claude first, with the key on file: Haiku 4.5, Sonnet 5.5, Opus 5.5. This
   gives one comparison table.
2. Local models through an OpenAI-compatible adapter (Ollama, headless) are
   optional and later. They depend on the MacBook's chip and RAM.

### C10. Build order for Part C

1. The case format, the scorer, and the four fake models, on the garden
   domain. This is gated and can start right after Part A.
2. The runner and the report against the garden server, with one real Claude
   run of about 15 garden cases.
3. Cases for each domain as it lands in Part B.
4. Vocabulary variants, the held-out split, and the baseline files.
5. The full run on all domains, then the pass bar verdict.

---

## Overall order and checkpoints

```
A  library (A5.0 ... A5.11)            checkpoint: baseline equal, lamp passes TCK
│
├── B2 basketball        ── C1 scorer + fakes on garden
├── B3 ledger            ── C2 runner + first real garden run
├── B4 library loans     ── C3 cases per domain as each lands
└── B5 support tickets   ── C4 vocabularies, held-out, baselines
                            C5 full run, pass bar verdict, Fielding row
```

At every checkpoint I stop, report the numbers, and wait for your go.

## Decisions, one at a time, when each is needed

1. ~~Domains 3 and 4~~: library loans and support tickets (decided 2026-10-10).
2. At the start of B2: the youth basketball rules (B2's proposed defaults).
3. At the start of C2: review every case, or a sample.
4. Before the first full run: the pass bar (C8).
5. Later, if wanted: local models, which needs the chip and RAM.

## Risks

- **The content addresses change when the schemas are generated.** Every
  changed hash is listed for approval at A5.4. Clients are unaffected, because
  they follow links and never store the addresses.
- **Some availability rules do not fit one offer key.** If a domain needs
  more, the key becomes a small data class. The sharing property still holds,
  because the sets are per key value.
- **API cost.** About 430 calls per model per full run. The report shows the
  token totals, and a smaller `-Pdomains=garden` run is available for quick
  checks.
- **Scope.** This is a large body of work. The checkpoints exist so it can be
  paused cleanly after any of them.
