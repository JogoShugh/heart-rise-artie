# Plan: generated scripts as data (YAML), the DSL kept

Status: proposed, 2026-11-02; checked against the code the same day, which corrected the first draft (see "Corrected
on checking"). Step 0 done the same day (see "Step 0: results"); no product code. The author decides next. Follows
spec/PLAN-SCRIPT-GENERATOR.md (kept scripts), spec/PLAN-GOAL-MARKDOWN.md (goal files) and the code-mode discussion
of the same day (exhibits/own-controls, "Where the tokens go").

## The idea

The scripts `artie generate` writes (intent scripts and kept scripts) hold the same steps whether they are Kotlin
or data. Written as YAML, they are run by ARTIE as data: no code is evaluated, and a step can do only what the
vocabulary says and what the server offers. The Kotlin DSL stays, for people.

- **Inference authors, determinism operates, and the server's controls hold both.**
- **Not a level above the DSL:** the same steps, held as data instead of code.
- **Optional:** `artie run` takes either; `artie generate` writes Kotlin unless asked or configured otherwise.
- **For generated scripts only.** Hand-written scripts use free Kotlin that YAML should not try to hold: `var`
  reads, `if`s, `?:` in templates, local functions, loops (dependabot-sim/watch.kts). They stay `.kts`.
- **Prior art, credited:** Arazzo (OpenAPI Initiative) is YAML steps over OpenAPI operations; workflow engines
  such as Kestra run YAML tasks. A HEART script differs in what a step binds to: a relation, run only if offered
  now, held to pins, falling back to its words. An engine could schedule `artie run` as a task.

## Scope

| Script | In this plan | Why |
|---|---|---|
| Intent script (`NAME.intent.kts` today) | Yes | Generated; words only, so it maps cleanly |
| Kept script (`NAME.kts` today) | Yes | Generated; the main case |
| Generated from a goal file's run | Yes, fully | Every line comes from the run and the goal file |
| Generated from a `.kts` script's run | Only where its copied lines map | The generator copies the run's own script lines as written (ScriptWriter.kt:8, 149-150): input defaults, waits, checks. Free Kotlin among them has no YAML; generate then fails and names the line |
| Bare script | No; its own fixes come first (below) | It is the unguarded baseline, and its HTTP handling is wrong today |
| Hand-written scripts | No | Free Kotlin |

## Why: claims to test

| # | Claim | Evidence so far |
|---|---|---|
| 1 | A YAML script runs no code: a model-written one needs no sandbox. It is not harmless: it can do whatever the server offers its token, and a `reasoned` step hands the server's words to a model. What it may do is the owner's controls' to limit (token scopes, the gateway's `connector` rung) | By construction; to be shown in step 8 |
| 2 | YAML starts faster: every `.kts` run starts a script engine and compiles | ScriptHost.kt:26-39: a new JSR-223 engine per run, no compile cache. Measured (3 runs each): JVM and argument parsing alone about 0.19 s (`artie --help`); `heart.kts --inputs-of`, which compiles and makes no calls, 1.9 s; `heart.kts`, a full run of 3 calls to the local test GitHub, 2.5 to 2.6 s. So about 1.7 s is the engine and the compile |
| 3 | The `.kts` engine can't run in a native image | It needs an embedded compiler, `wholeClasspath` and a Power-assert jar found by path (ScriptHost.kt:42-47). A script compiled ahead into its own native build could run, at a build per script. No GraalVM setup exists |
| 4 | Other runtimes (heart-ts) can run the same file | Later; not in this plan |

Claim 2 has cheaper rivals: a compile cache for `.kts`, keyed by the script's hash, and an ARTIE daemon
(spec/ideas/artie-daemon.md, a future idea). Step 0 measures the cache.

## What the file holds, and what comes from elsewhere

No media type is built in anywhere. Each part of a request is either in the file, or comes from the server's
controls, or from ARTIE's configuration; the format's spec says which.

| Part of a request | Intent and kept scripts |
|---|---|
| Method | The server's form |
| Address | The entry (`start`), then the server's links and forms |
| Query | Form fields, for a GET form |
| Accept, Accept-Profile | ARTIE negotiates: `Accept-Profile` from the dialect, by the pinned rung (`rung` in the pins; HttpBinding.kt:107) |
| Content-Type of a write | The form's declared `contentType` (Layers.kt:137, 181). It is part of the form's integrity (FormIntegrity.kt:10-21), so the pins catch a change |
| Prefer | ARTIE, from the rung (HttpBinding.kt:108), and `wait=` when it waits (HttpBinding.kt:18) |
| Body | `fields`, checked against the form's schema before sending |
| Authorization | ARTIE's credentials for the host; never in the file |
| If-Match | ARTIE, from the read the step began from |
| If-None-Match, caching | ARTIE's HTTP cache of affordance sets and schemas |
| A refusal | Typed: not offered, stale, invalid input, guard; with the server's reason |

For these scripts the absence is the point: what the server's controls carry, the file can't get wrong.

## Step 0: settle it (no product code)

- **0a. Split the 1.7 s** into the engine's start and the compile, with a throwaway timer around
  `ScriptHost` (not committed), and try a compile cache in the same throwaway: how much of a repeat run it saves.
- **0b. Native spike, throwaway.** In a worktree, a native-image build of artie-cli without the script engine.
  Does it build; its startup time. Not committed unless the author says.
- **0c. Coverage on paper.** Hand-write the YAML of `exhibits/merge-watch/merge.intent.kts`,
  `exhibits/merge-watch/merge.kts` and `exhibits/mcp-over-openapi/artie/t1.kept.kts` by the rules below, and a
  draft JSON Schema. List every construct the generator can emit (ScriptWriter.kt:117-191; the watch's text,
  WatchText.kt) with its YAML, and anything that doesn't map. The mockups go to `exhibits/*/` beside their `.kts`.
- **Decision with the author:** go; no-go; or Kotlin only, with a compile cache.

## Step 0: results (2026-11-02)

**0a. Where a `.kts` run's time goes.** A throwaway timer (scratchpad, not committed) made ARTIE's engine and
compiled scripts through it, in one JVM:

| | Time |
|---|---|
| The engine made | 0.12 to 0.14 s |
| The first compile in the process (a one-line script) | 1.6 to 1.7 s |
| Later compiles in the same process: one line, `heart.kts`, `merge.kts` | 0.03, 0.05, 0.15 to 0.17 s |
| A second, fresh engine in the same process, made and compiling | 0.11 s |

- The cost is the Kotlin compiler warming up, once per process, not the compile of each script.
- So a compile cache on disk, which never starts the compiler, would save about 1.6 s a run (not tried); a
  daemon would too (spec/ideas/artie-daemon.md); and so would YAML, which needs no compiler.

**0b. Native image.** GraalVM 25 (ARTIE needs Java 22; GraalVM 21 can't load its classes). Throwaway, in the
scratchpad.

- With the script engine on the class path, the build fails at once: the Kotlin compiler jar carries native-image
  settings for jline that point at a file it doesn't have.
- Without the engine's jars (the Kotlin compiler, scripting and Power-assert), it builds: 45 MB, in 56 s, at
  3.4 GB of memory.
- A `.kts` on it stops: "no Kotlin script engine". Claim 3 holds.
- Start-up, 3 runs each (the first native run 0.46 s, then):

| | JVM | Native |
|---|---|---|
| `artie --help` | 0.19 s | 0.015 s |
| A goal file, `--inputs-of` | 0.74 s | 0.015 s |

- Caveat: those are small workloads. The native settings were traced from them only; a real run's HTTP, TLS and
  model calls weren't exercised natively and may need more.

**0c. Coverage.** Mockups, written by hand line for line with their `.kts`:
`exhibits/merge-watch/merge.intent.heart.yaml`, `exhibits/merge-watch/merge.heart.yaml`,
`exhibits/mcp-over-openapi/artie/t1.kept.heart.yaml`. Draft schema: `spec/schemas/heart-script.draft.schema.json`.

- All three are valid against the schema.
- Refused by the schema: a key outside the vocabulary, an expression or a call in a `${…}` reference, a header on
  a step, an address to call, an unknown kind. A reference the schema lets through is still only looked up by
  name, never evaluated: `${java.lang.Runtime}` is an input or kept value nobody declared, and the run stops.
- Rules the schema can't hold, for the runner: `kind: intent` has no `act`; in `kind: kept`, every step without
  `act` or `follow` says `reasoned: true`.
- Found while writing them:
  - every Kotlin block becomes `then` (or `steps`), and statements in it are steps of their own: `keep-as`, `sync`,
    `whenever`, `has`, `leave-where`, `once`;
  - `then: []` matters: with a block, even an empty one, the script visits what a step led to and returns
    (Heart.kt:123); without one, it stays there;
  - long words fold with `>-`, so the text is the Kotlin string exactly.

| What the generator emits | YAML | In a mockup |
|---|---|---|
| `heart(entry) { }` | `start`, `steps` | yes |
| `val x by input(question, default)` | `inputs` | yes |
| a default the goal file named (`now: MMdd-HHmmss`), written today as Kotlin (`LocalDateTime.now()…`) | `default: now: …` | schema only |
| `act(rel, values, fields, intent or inferred and fromIntent) { }` | `act`, `fields`, words, `then` | yes |
| `act(intent or inferred)`, no relation | words, and `reasoned: true` in a kept script | yes |
| `follow(rel, title, intent)` | `follow`, `title` | yes |
| `keepAs(name)` | `keep-as` | yes |
| `sync()` | `sync` | yes |
| `watch(until = { facts })` | `watch` | yes |
| `whenever { }`, `has { }`, `leaveWhere(words) { }` | `whenever`, `has`, `leave-where` | yes |
| `once(rel, giveUp, otherwise = { why -> })` | `once` | yes |
| `check(facts)` (Waits.kt:32) | `check` | schema only |
| `until { … }` (Waits.kt:30) | `until`: `offered` or `facts` | schema only |
| `at(scope) { watch }`, `listeners { listen(match, from) { } }` | `at`, `listeners` | schema only |
| `madeBy(…)`, `saying(…)` | `made-by`, `saying` | schema only |
| `given { facts } then { }` | `given`, `then` | schema only |
| `doing(words) { }`, `doneWhere { }` | `doing`, `done-where` | schema only |
| `actOnce(rel, fields, listedIn)` | `act-once` | schema only |
| a comment the generator writes (`// review: …`, "not decided in this run") | a YAML comment | — |

**Doesn't map yet:**
- **A value carried from an earlier answer**, the commonest gap: 7 of the 10 generated scripts in the repository
  have it, and the three mockups happen to be the three without. The generator writes `var pull = 0L`, sets it in
  the step that brought it (`pull = state.long("number")`), and uses it later (`"Fixed in 1.2 by #$pull."`,
  client/scripts/generated/github-task-from-reasoned.kts:11, 39, 51); or `val n by kept("issue") { long("number") }`
  (Bindings.kt:20). In YAML: `keep-as: pull` on that step, and `${pull.number}` where it's used, read from the
  state as it was kept, as the variable was. A change to how the generator binds values, not a new key. A fourth
  mockup, of github-task-from-reasoned.kts, should show it.
- `until { … }`'s condition is Kotlin text the run copied (`step.waitedFor`, Waits.kt:30). Only `offers(rel)` and
  facts map; any other text fails generate.
- Lines copied as written from a `.kts` source (ScriptWriter.kt:149-150), as the scope table says.
- A member name with a `.` in it can't be a reference path. None is known yet.

## Steps, if go

Each step features first: Gherkin with tables, written through Edit/Write, a red run at test level logged before
any `src/main` write, then green; a local commit per step.

1. **The watch as data in the capture.** A run's watch is kept in its capture as Kotlin text
   (Capture.kt:32, `watch.script`, built by StepWatch.kt:55 and WatchScript.kt:62 from `Decisions`). Keep the
   `Decisions` themselves (WatchScript.kt:11, Chosen.kt:38-107) in the capture, as data; the Kotlin text is made
   from them when needed. RailsWriter re-parses that text word by word today (RailsWriter.kt:49-132); it reads
   the data instead. A capture format change: older captures are regenerated, not shimmed (pre-release).
2. **A step model for generated scripts.** The generator emits Kotlin strings straight from the capture's
   `Node`s (ScriptWriter.kt:42). Give it one data model of steps, the watch's included, and have ScriptWriter
   emit Kotlin from it. A refactor: generate's features stay green with byte-identical output. **It needs the
   author's refactor switch.**
3. **Write YAML.** `artie generate --format yaml` writes `NAME.heart.yaml` (and `NAME.intent.heart.yaml`) from
   the same model. The built-in default stays `kts`. One setting in a config file makes YAML the default
   (`generate: format: yaml`), and `--format` overrides it either way. Today's config is per script and for runs
   (`NAME.config.yaml`, Config.kt:38); this setting is for generate and needs a place of its own (open). The JSON
   Schema is published beside the plan's code. If a construct has no YAML, generate fails and names it.
4. **Run YAML.** `artie run` dispatches on `.heart.yaml` (today every file that is not a goal is taken as Kotlin,
   ArtieCli.kt:100). The file is checked against the schema, then its steps are interpreted by calling the same
   `Here` functions a `.kts` calls: one runtime, one behaviour. What today reads the Kotlin source reads the YAML
   instead:
   - step lines: from `StackWalker` frames in `.kts` files (Run.kt:159-166); for YAML, the node's line, through
     `run.place` (Run.kt:122), as goal runs do;
   - reason templates, parsed from the source line (Sources.kt:37-39): from the step's words;
   - the `until(` text in the trace (Run.kt:269-270): from the condition;
   - the check that a script needing a model was given `--reasoner`, a regex on the text (ArtieCli.kt:105): from
     `kind: intent`, or any `reasoned: true`.

   **Parity feature:** one capture, generated both ways, each run against the same service; the traces are equal
   but for file, line and source text.
5. **Convert to Kotlin.** `artie convert NAME.heart.yaml --to kts`: the DSL, for a person who wants to add code.
   One way only; Kotlin to YAML isn't possible in general.
6. **Native runner**, only if 0b is promising: a native ARTIE that runs `.heart.yaml` and goal files; `.kts` stays
   with the JVM ARTIE.
7. **`heart_do` writes YAML** (spec/PLAN-HEART-MCP-ARTIE-BACKED.md, itself not built yet).
8. **Measure and report:**
   - startup and run time: `.kts` on the JVM (with and without a cache), YAML on the JVM, YAML native;
   - a model-written YAML script that reaches outside the vocabulary, refused by the schema before any request.

Not in this plan: a heart-ts runner, emitters for other languages, an Arazzo export of the OpenAPI twin, and the
ARTIE daemon.

## The format (draft)

**Files**

| File | Written by | Role |
|---|---|---|
| `NAME.goal.md` | a person | The source; never changed by a tool |
| `NAME.intent.heart.yaml` | `artie generate` | The intent script; derived, never hand-edited |
| `NAME.heart.yaml` | `artie generate` | The kept script; derived, never hand-edited |
| `NAME.pins.yaml`, `NAME.intent.pins.yaml` | `artie generate` | The lock files, as now |
| `NAME.derivation.yaml` | `artie generate` | Ties a script to its source and run, as now |
| `NAME.kts` | `artie generate --format kts`, or `artie convert` | For a person to extend |

`.goal.yaml` stays refused (GoalFile.kt:15).

**Rules**

1. **One YAML style:** block maps and lists only; no flow `{ }` or `[ ]`. Braces in a value then never collide
   with YAML's own.
2. **References are `${…}`**, as in the Kotlin DSL's templates, so converting to Kotlin is direct: an input
   (`${owner}`), a kept value (`${watch}`), a member of the state as read (`${state.title}`), the server's reason
   (`${why}`). A name or a dotted path, nothing more; nothing is evaluated. An empty reason reads "no reason
   given" (the Kotlin writes `${why ?: "no reason given"}`).
3. **`kind`** at the top is `intent` or `kept`, and the schema holds each to its shape:
   - `intent`: no step has `act` or `fields`; the model decides every step, every run;
   - `kept`: a step either has `act` (pinned: no model) or says `reasoned: true` (the model decides it every run,
     as the goal file's `` `reasoned` `` asked). Neither is implied by an absence.
4. **Words, as the DSL has them** (Said, Reason.kt:43): `intent` is a person's words; `inferred` is a model's,
   with `from-intent` the person's words they serve. The words a step reasons on are `intent`, else `inferred`.
5. **Conditions are data:** `at` (a kept name, else the resource held), `field`, and `is` or `has`. The schema
   checks them.
6. **Inputs:** `default` and `question` as values. A computed default only as a named generator
   (`now: MMdd-HHmmss`), as goal files have; any other Kotlin expression fails generate (scope table).

**Vocabulary: what the generator emits, and its YAML**

| DSL | YAML key |
|---|---|
| `heart(entry) { }` | `start`, `steps` |
| `act(rel, values, intent, inferred, fromIntent, fields)` | `act`, `fields`, `intent`, `inferred`, `from-intent` |
| `act(intent = …)` with no relation | `intent` (or `inferred`), and in a kept script `reasoned: true` |
| `follow(rel, title)` | `follow`, `title` |
| a step's block | `then` |
| `keepAs(name)` | `keep-as` |
| `sync()` | `sync: true` |
| `decide { }` | `decide` |
| `if (offers(r)) … else note(why(r))` | `if-offered`, `then`, `else`, `note` |
| `check(facts)` | `check` |
| `"m" eq v`, `"m" has v`, `at(name) { }` | `field` with `is` or `has`, and `at` |
| `watch(every, atMost, until = { })` | `watch`: `every`, `at-most`, `until`, `steps` |
| `whenever` with `has`, `leaveWhere` | `whenever`: `has`, `leave` (each a `where` and its `say`) |
| `once(rel, giveUp, otherwise = { why -> })` | `once`: `offered`, `give-up`, `steps`, `otherwise` |
| `val x by input(default, question)` | `inputs`: `x`: `default`, `question` |

0c checks this table against what the generator really emits; constructs it finds missing are added there.

## Bare scripts: their own fixes, before any YAML

Bare scripts are kept out of this plan's YAML, but checking them found what is wrong with them now, whatever the
format:

- `Bare.kt` builds every request with `Content-Type` and `Accept` fixed at `application/json` (`CONTENT`); the
  script can override them only for all its calls at once (`bare(headers = …)`).
- Every body is sent as JSON, and every answer parsed as JSON.
- `artie generate --bare` drops the headers the run sent, though the run's recording of its calls keeps them
  (`*.upstream.yaml`, e.g. client/scripts/generated/github-task-from-reasoned.upstream.yaml).

A bare script can point at GitHub, at the gateway passed through, at the gateway's HEART side, or at a native HEART
server, which speaks `hal+json`, `prs.hal-forms+json`, `problem+json`, `merge-patch+json` and others, with
`Prefer` and `Accept-Profile`. Against a native HEART server, a bare script with validators gets the server's own
atomic conditional write.

The fixes, a step of their own (spec/ideas/bare-runs.md):
- headers per call, and no built-in media type: each call says what it sends, as recorded;
- bodies and answers by their media type: JSON types as data, others as text; `problem+json` read as a refusal;
- `generate --bare` writes each call's recorded headers (Authorization never);
- a ladder row: a bare script against a native HEART server.

Whether bare scripts then become YAML too (`kind: bare`, with `call`, `url`, `headers`, `body`) is open.

## Open

- **Extension:** `.heart.yaml`, or another.
- **Where generate's format setting lives:** beside the goal file, in the project, or for the person.
- **Bare scripts as YAML:** after their fixes, or never.
- **Two formats to keep in step:** generate fails on any construct without YAML (step 3), and the parity feature
  runs for every construct.
- **Vocabulary creep:** every new key is a step toward code. A key is added only for a construct the generator
  emits.

## Corrected on checking (2026-11-02)

- **Start-up numbers.** The first draft said a goal file runs in 0.2 s and that `--inputs` lists a script's inputs.
  `--inputs` takes a file (Arguments.kt:105); those runs stopped at once with exit 2. The listing flag is
  `--inputs-of`. The numbers above are re-measured with exit codes checked; a goal file's start-up is not measured.
- **Words.** A mockup put a model's inferred words under `intent`. The DSL keeps them apart (rule 4).
- **Style and references.** Mockups mixed flow and block maps, and wrote references as `{name}`, which YAML reads
  as a map. Rules 1 and 2.
- **HTTP.** The first draft said nothing of headers or media types. The table above, and the bare fixes.
- **Claims 1 and 3** were stated too strongly; reworded.
- **Missed in the code:** the capture keeps the watch as Kotlin text (step 1); generate copies a source script's
  lines as written (scope); the `--reasoner` check is a regex on the text (step 4).

## Gate notes

- Features before `src/main`, through Edit/Write; the red run at test level, logged and checked.
- Step 2 is a refactor: the author switches refactor mode on.
- No change to spec/SPEC.md is expected. If one turns out to be needed, stop and ask.
