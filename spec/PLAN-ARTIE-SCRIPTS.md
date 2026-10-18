# ARTIE scripted, then scripts captured from words

Status: steps 1 to 3 authorized (2026-10-17); steps 4 to 7 wait until 1 to 3
are done, then we revisit them.

The aim: ARTIE driven by a Kotlin script, with no reasoner, from a bookmark
and the profile's vocabulary alone (relation names, field names, values),
never URLs; then a reasoner run, asked in words, captured as a trace and
compiled into such a script, whose parameters are the values that came
from the words (slot filling, after the frame-based dialogue systems of the
1990s, ATIS). Later runs need no model.

Every step is feature first (bdd-gates skill): tables, a red run, then
green. Every report follows the heart-run-report skill.

## Now

### 1. ARTIE scripted by hand against the GitHub gateway (phase 11a)

Find and close what ARTIE lacks against the gateway, each gap a client
feature, tested against the fake GitHub through the gateway in process.
Expected, unverified: templated search and lookup forms at declared
collection paths; item paths and template pointers; sub-collection links;
following Location after a 201.

Done when a Kotlin test drives ArtieClient from the bookmark to issue 10,
comments, follows the comment link, edits the comment and closes the
issue, with no URL in the test.

### 2. The HEART script DSL and its host

A Kotlin internal DSL for scripts, defined below ("The DSL"), in its own
module, `heart-script`, with ARTIE as its first implementation; a Gradle
runner for `.main.kts` scripts, their inputs as `name=value` arguments.
Every step writes a trace step in the heart-run-report format.

Done when a hand-written `comment-and-close.main.kts` (owner, repo,
number, comment) runs live on JogoShugh/heart-github-test, and its run
report is published.

**Status of the DSL design (2026-10-17):** drafted with the author over
several rounds; under challenge, not yet committed to. Decided so far:

- Inputs: `input()`, `input(default)`, `input(question)` declared up
  front, and inline `ask(question, default)`; nothing asks a person
  without `--interactive` (below).
- `act` for every form, safe ones included: the client knows a form is
  safe from its method (as its binding maps it) and the relation's ALPS
  type, and the script never declares it.
- A block after `act` or `follow` visits what the selection led to and
  returns to where the script was, synced again.
- `expect` is not in everyday scripts: it lives in `heart-script-test`, an
  add-on for conformance and demonstration scripts that provoke refusals
  or outcomes on purpose. Anyone may include it; for us it is for explicit
  testing.
- A client's own refusal of an act not in the manifest is `NotOffered`,
  the same kind as the server's; the report says which side refused.
- Code lives in `client/`, made multi-project: `heart-script` (the DSL and
  its service-provider interface, no ARTIE), `heart-script-test`, ARTIE
  (implementing the interface) and the `artie` CLI.
- `--inputs` files: JSON and YAML.
- Layout: `client/` a multi-project root: `client/artie` (today's ARTIE,
  moved), `client/heart-script`, `client/heart-script-test`,
  `client/artie-cli`; our scripts in `client/scripts/`, test scripts in
  `client/scripts/tests/`.
- A model key, for `reason(…)` and the live reasoner run: `ANTHROPIC_API_KEY`
  in the environment first, else an entry in `~/.artie/credentials`; never
  stored by ARTIE, never printed. Built 2026-10-17 as `ModelKey.standard`;
  it says where a key came from, never its value.
- heart-mcp in step 3: both a scripted MCP host (repeatable, no model) and
  Claude Code as the host, asked in words.
- Order: step 1 first (done 2026-10-17), then the DSL, the test add-on and
  `artie run`; a commit at each step. Java 25 and the typed layer's choices
  wait in `spec/ideas/`.

**Built (2026-10-17), not yet committed:**

- `heart-script`: the DSL (`heart`, `act`, `follow`, blocks that visit and
  return, `check`, `note`, `until`, `members`, inputs and `ask`), its
  `Navigator` contract found by ServiceLoader, and the trace in the
  heart-run-report format, written by meaning: outcome, refusal kind and
  side, what the client held. A missing link or an act the manifest lacks is
  refused by the client, traced, before anything is sent.
- `heart-script-test`: `expect(kind) { … }` for `Done`, `NewResource`,
  `Unchanged` and each refusal; a test failure exits 1.
- ARTIE implements the contract (`ArtieNavigator`), registered for
  ServiceLoader, over http, https, coap and coaps; a network failure is
  "unreachable", exit 4.
- `artie run` (`client/artie-cli`, installed with `installDist`): inputs,
  `--inputs` (JSON, YAML), `--inputs-of` (with inline questions counted),
  `--interactive`, `--entry`, `--trace`, `--report` (the run report, with the
  script in it), `--quiet`, `--output json`; exit codes 0 to 5. It checks for
  Java 22 or newer before starting.
- The run report reads script traces as well as HTTP ones.
- Live: `client/scripts/tests/github-comment.kts` through the GitHub gateway
  on JogoShugh/heart-github-test; `client/scripts/tests/garden-bed.kts` over
  coap, coaps and http, unchanged, with only its `entry` input differing
  (`docs/reports/heart-script-garden/both.html`).
- `--rung L0|L1|L2` pins the rung through the contract (`NavigatorProvider.navigator(rung)`);
  checked live against the garden (`Prefer` sent, affordance sets and schemas fetched at L2).
- ARTIE checks a script's fields against the form's schema before sending: a value the
  schema doesn't allow is refused by the client, nothing sent; a server's 422 is refused by
  the server. An entry with no binding stops the run before its first request (exit 2).
- Live: `client/scripts/tests/github-pulls.kts`, read-only: merge offered on a ready pull
  request, refused on a draft with the gateway's reason.
- `reason("…")`: one step. The client's reasoner chooses one selection from what is offered
  (a form with its fields, or a link); ARTIE checks it against the manifest and the form's schema,
  then makes it; the trace records an intent step, the words as said and the choice as its values.
  `--reasoner claude` allows it, the key from `ModelKey`; without `--reasoner` a script that
  reasons stops before its first request (exit 2); with no key, the step is refused, saying where
  a key goes. Live: `client/scripts/tests/garden-reason.kts` over CoAP, Claude choosing
  `garden:sow-seed` with `seedType` basil and `depthCm` 2 from "plant some basil in it, about two
  centimetres deep".

### 3. Live: ARTIE scripted, ARTIE reasoning, heart-mcp (phase 11b)

The step 2 script live; the reasoner asked in words ("comment 'fixed in
1.2' on issue 10 and close it"); the same through heart-mcp.

Done when all three run live, each as a run report, and the GitHub
Gateway Status report has ARTIE scripted, ARTIE reasoning and heart-mcp
columns.

## Later (revisit after 1 to 3)

### 4. Trace capture

DecisionLog keeps replayable steps: enter, search(rel, fields), pick(rel,
title), act(rel, fields), follow(rel), with each result's status and
Location. Each value is tagged by its source: the user's words, the
server's state, or a constant. Done when a reasoner run's trace, replayed,
makes the same requests against the fake GitHub.

### 5. Script synthesis (slot filling)

From a trace, a `.main.kts`: each step a call; values from the words become
parameters, named and typed from the form schemas; values from state are
read again at run time, never hard-coded. `expectOffered` before every act;
a clear stop with the server's reason. A person confirms the parameters
before the script is kept. Done when "close issue 10 as not planned"
produces `close-issue-not-planned.main.kts(owner, repo, number)`, run live
with other inputs and no model.

### 6. Robustness

The synthesized scripts against the garden's Wednesday changes (move-url,
rename-field, retitle, new-required-input) and the OpenAPI twin's clients.
Expected: URL and title changes survive; vocabulary and schema changes stop
with a reason. Done when the change by outcome matrix is in a report.

### 7. Beyond one path

Loops and branches ("for each item of search X, if Y is offered, act") in a
small DSL, traces generalized into it. Out of scope until 5 and 6 hold.

## The DSL (draft, under challenge)

### What it is modelled on

The DSL is HEART's own semantics, nothing else: the protocol-neutral core
(SPEC §6.0), the Sync-Reason-Act cycle (§6.8), the entry point and
collections (§6.11) and explaining absence (§6.12). It names no API's
domain and no protocol: no URIs but the entry, no methods, headers,
validators or status codes. Relations, field names, titles and JSON
Pointers are plain values, the profile's vocabulary, which client and
server share by design; nothing is generated from the remote, so a server
that adds members, links or forms never breaks a script. Typed
vocabularies are an optional layer that compiles down to the same calls:
drafted beside this one in `spec/HEART-SCRIPT-TYPED.md`.

Not CRUD: nothing is created except by an act some resource offers, the
service index included.

### The cycle, as a script

A script is the Reason step written in advance. Its whole output is the
selection space of §6.8: a form with field values, a link by relation and
title, or a question; deterministic code does the rest.

| Cycle step | DSL | Notes |
|---|---|---|
| Sync | `heart(entry) { … }`; `sync()`; implicitly after every selection | the entry's scheme chooses the binding (http, https, coap); no binding: refused before anything is sent |
| Reason, in advance | the script itself | |
| Reason, now | `reason(intent)` | hands one decision to the reasoner; its selection runs like any other and is recorded as an intent step (optional, L6) |
| Act: a form | `act(rel, field to value…)` | any form; a safe form (search, lookup) only syncs its target |
| Act: a link | `follow(rel, title)` | `item`, `next`, any relation |
| Act: a question | inputs, and `ask(question, default)` | see "Inputs and questions" below: the slots |

A block after `act` or `follow` visits what it led to, then returns to the
resource it came from, synced again: navigation is the block structure.

### The representation

Inside any block, `this` is the representation currently held (the
client's `HeartRepresentation`), and there is only one:

| Part | Script sees it? |
|---|---|
| state | `state[pointer]`, by JSON Pointer; missing is null |
| manifest: forms offered, their schemas, why-absent | `offers(rel)`, `forms()`, `choices(rel, field)`, `why(rel)`, `describe(rel)` |
| links, by relation and title | `links(rel)`, and `follow` |
| validator | no: the client attaches it |
| address | no: the run report shows it |
| rendering (dialect, rung) | read-only `rendering`; a run option pins a rung |

### Outcomes, refusals and failures

Closed sets of core concepts, named by meaning:

- Outcomes: `Done` (the resource's new state), `NewResource` (its
  representation), `Unchanged` (a sync found it current).
- Refusals, the server's: `NotOffered`, `Stale`, `InvalidInput`,
  `PreconditionRequired`, `NotAuthenticated`, `Forbidden`, `NotFound`.
- Failures, the client's own, before sending: `NoBinding`,
  `IntegrityMismatch`, `InvalidInput` (by the form's schema), `NotAChoice`,
  `Unreachable`.

An everyday script just acts: any refusal or failure stops it with the
reason, which the run
report shows, and says whether the client or the server refused. An act
whose relation is not in the held manifest is refused by the client,
without sending; the runner fetches `why(rel)` for the report. A script
that wants another path branches on the manifest (`offers(rel)`, `why(rel)`)
or waits for it (`until`).

`heart-script-test` adds `expect(kind) { … }`: the outcome or refusal a
step must have (`NewResource`, `Unchanged`, `NotOffered`, `Stale`, …), the
scripted twin of the run report's `expect` field.

### Also

- `until(timeout) { condition }`: sync until the server settles.
- `members { … }`: each `item` link in turn, following `next`.
- `onStale(retry = 1)`: sync and repeat the selection once.
- `check(…)`, `note(…)`: assertions and remarks, both in the run report.
- `heart-script-test`: `expect(kind) { … }`, for testing only.
- RISE (§7) is on hold in the spec; when it returns, `act(…) awaiting { }`.

### Inputs and questions

Scripts mostly run unattended, so nothing ever waits on a person unless the
run says `--interactive`, and whether a run can go unattended is known
before its first request. Two words: `input` (declared at the top) and
`ask` (inline).

```kotlin
val i = inputs {
    val owner   by input()                                   // required
    val comment by input(default = "Fixed in 1.2.")          // optional
    val reason  by input(question = "Reason to close?")      // no default; asked when first read
}
// anywhere in the script: a question must carry a default
act("github:close", "state_reason" to ask("Reason to close?", default = "completed"))
```

| Written as | Unattended | `--interactive` |
|---|---|---|
| `input()` | must be given; else exit 2 before the first request | asked before the run |
| `input(default = …)` | the given value, else the default | the same |
| `input(question = "…")` | must be given; else exit 2 before the first request | asked when first read, in those words |
| `ask("…", default = …)` inline | the default, noted in the run report | asked when reached |

- `by` is Kotlin property delegation: the block declares each input up
  front, and reading it supplies the value (given, default, or asked).
- Inline `ask` has a required `default`: a question with none must be
  declared, so the runner knows it before starting.
- A default is any expression: a constant, a variable, a value read from
  the state (`default = state["/title"]`).
- Any input, a question included, can be given on the command line
  (`reason=completed`) or in `--inputs`, so such a script can still run
  unattended.
- `artie run --inputs-of <script>` lists them without running:
  required, defaults, questions, and how many inline questions with
  defaults.

### Running a script: `artie run`

Installed with the client (Gradle `installDist`); no Gradle at run time.

```
artie run <script.kts> [name=value …] [options]

  name=value              an input
  --inputs <file>         more inputs, JSON or YAML; name=value wins
  --inputs-of             list the script's inputs and questions (inline ones counted), and exit
  --interactive           let the run ask a person (off by default)
  --entry <uri>           another entry than the script's (an environment, CoAP …)
  --rung L0|L1|L2         pin the rung; default: the best both sides share
  --reasoner <name>       allow reason(…) steps to call a model (off by default)
  --report <dir>          also build the run report (heart-run-report), the script shown in it
  --trace <file>          the trace (default ./runs/<script>-<time>.trace.json)
  --quiet | --output json one summary line, or JSON for CI
```

| Exit | Meaning |
|---|---|
| 0 | every step done; in a test, every expectation met |
| 1 | a test expectation not met (`heart-script-test`) |
| 2 | stopped before the first request: a missing input, a question with no `--interactive`, bad arguments, no binding for the entry's scheme, `reason(…)` without `--reasoner` |
| 3 | HEART refused a step: not offered, stale, invalid input, not authenticated, forbidden, not found; the refusing side named, client or server |
| 4 | the client failed: unreachable, integrity mismatch |
| 5 | an error in the script's own code |

Each run prints one line per step (what was selected, with its values, and
the outcome), and a stop names the step and HEART's reason. Secrets are
never printed nor traced. Unattended runs are model-free and person-free by
default.

### Not ARTIE's: a contract

Scripts depend on `heart-script`; it calls a small service-provider
interface any HEART client can implement (enter, act, follow, sync, why,
describe), found by `ServiceLoader`, as JDBC finds a driver. ARTIE is the
first implementation; a script conformance kit, run against the lamp and
garden servers, proves another. Clients in other languages share the
selection language and the trace format, not the `.kts` file.

### Sample: a GitHub issue, end to end

```kotlin
val i = inputs {
    val owner   by input()
    val repo    by input()
    val number  by input()
    val comment by input(default = "Fixed in 1.2.")
}

heart("http://localhost:8392/") {
    val text = i.comment
    act("github:issues-get", "owner" to i.owner, "repo" to i.repo, "issue_number" to i.number)

    act("github:comment", "body" to text) {
        act("github:edit", "body" to "$text (edited)")
    }

    follow("github:issues-list-comments") {
        follow("item", title = "$text (edited)") { check(state["/body"] == "$text (edited)") }
    }

    until(timeout = 20.seconds) { offers("github:close") }
    act("github:close")
    check(state["/state"] == "closed")
}
```

The same scenario as a test (with `heart-script-test`) states what each
provoked step must answer:

```kotlin
heart("http://localhost:8392/") {
    act("github:issues-get", "owner" to i.owner, "repo" to i.repo, "issue_number" to i.number)
    expect(NewResource) { act("github:comment", "body" to "Fixed in 1.2.") }
    act("github:lock", "lock_reason" to "off-topic")
    expect(NotOffered) { act("github:lock") }          // locked: lock is not offered
    act("github:unlock")
}
```

The same over CoAP changes only the entry: `heart("coap://localhost:5683/")`
against the garden, with the garden's relations.
