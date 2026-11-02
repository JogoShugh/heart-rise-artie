# Plan: kept scripts as data (YAML), the DSL kept

Status: proposed, 2026-11-02. No code. The author is not yet sold; step 0 exists to settle it before anything is
built. Follows spec/PLAN-SCRIPT-GENERATOR.md (kept scripts), spec/PLAN-GOAL-MARKDOWN.md (goal files) and the
code-mode discussion of the same day (exhibits/own-controls, "Where the tokens go").

## The idea

A kept script (what `artie generate` writes from a run) holds the same steps whether it is Kotlin or data. Written
as YAML, it is run by ARTIE as data: it can do only what its vocabulary says and what the server offers, so a
script a model wrote needs no sandbox and no review as code. The Kotlin DSL stays, for people.

- **Inference authors, determinism operates, and the server's controls hold both.**
- **Not a level above the DSL:** the same steps, held as data instead of code.
- **Optional:** `artie run` takes either. A person still writes an intent script straight in Kotlin.
- **For generated scripts only.** Hand-written scripts use free Kotlin that YAML should not try to hold: 43
  `var` reads, 25 `if`s, `?:` in templates, local functions, loops (dependabot-sim/watch.kts). They stay `.kts`.
- **Prior art, credited:** Arazzo (OpenAPI Initiative) is YAML steps over OpenAPI operations; Kestra and Kogito
  are workflow engines. A HEART kept script differs in what a step binds to: a relation, run only if offered
  now, held to pins, falling back to its words. Kestra could schedule `artie run` as a task.

## Why: claims to test

| # | Claim | Evidence so far |
|---|---|---|
| 1 | A model-written YAML script is safe to run unreviewed: closed vocabulary, no code evaluated | By construction; to be shown (step 7) |
| 2 | YAML starts faster: every `.kts` run compiles | ScriptHost.kt:26-39 makes a new JSR-223 engine per run, no compile cache. A one-line `.kts` with no calls took 1.8 to 2.2 s; `heart.kts` (3 calls, local) 2.6 to 2.7 s; a goal file 0.2 s; `artie --help` 0.18 s. The JVM is not the cost; the engine and the compile are |
| 3 | Only YAML can run in a native image | The script engine needs an embedded compiler, `wholeClasspath` and a Power-assert jar by path (ScriptHost.kt:42-47). No GraalVM setup exists |
| 4 | Other runtimes (heart-ts) can run the same file | Later; not in this plan |

Claim 2 has a cheaper rival: a compile cache for `.kts`, keyed by the script's hash. Step 0 measures both.

## Step 0: settle it (no product code)

- **0a. Split the 2.6 s.** Time `artie run X.kts --inputs` (compiles, makes no calls: `heart` returns early in
  listing mode, Heart.kt:22) against a full run, for `heart.kts` and `merge.kts`.
- **0b. Native spike, throwaway.** In a worktree, a native-image build of artie-cli with the goal-run path only
  (no script engine). Does it build; its startup time. Not committed unless the author says.
- **0c. Coverage on paper.** Hand-write the YAML for `exhibits/merge-watch/merge.kts`,
  `exhibits/own-controls/scripts/heart.kts` and `exhibits/mcp-over-openapi/artie/t1.kept.kts`, plus a draft JSON
  Schema. List every construct `artie generate` can emit (ScriptWriter.kt:117-191, WatchText.kt) with its YAML
  key, and anything that doesn't map.
- **Decision with the author:** go; no-go; or DSL only, with a compile cache.

## Steps, if go

Each step features first: Gherkin with tables, written through Edit/Write, a red run at test level logged before
any `src/main` write, then green; a local commit per step.

1. **A step model for kept scripts.** Today the generator emits Kotlin strings straight from the capture's
   `Node`s (ScriptWriter.kt:42); the only structured step model is the watch's (`Decisions`, `Chain`, `Chosen`,
   `Bound`: WatchScript.kt:11, Chosen.kt:38-107). Give the generator one data model of steps, and have
   ScriptWriter emit Kotlin from it. This is a refactor: generate's features stay green with byte-identical
   output. **It needs the author's refactor switch.**
2. **Write YAML.** `artie generate --format yaml` writes `NAME.heart.yaml` from the same model. The default stays
   `kts`; one setting in a config file makes YAML the default (`generate: { format: yaml }`), and `--format`
   overrides it either way. Today's config is per script, for runs (`NAME.config.yaml`, Config.kt:38); this one
   is for generate, so it needs a place of its own (open below). The JSON Schema is
   published beside it. If a construct has no YAML key, generate fails and names it.
3. **Run YAML.** `artie run NAME.heart.yaml` validates against the schema, then interprets the steps by calling the
   same `Here` functions a `.kts` calls: one runtime, so one behaviour. What today reads the Kotlin source must
   read the YAML instead:
   - step lines, from `StackWalker` frames in `.kts` files (Run.kt:159-166): from the YAML node's line,
     through `run.place` (Run.kt:122), as goal runs do;
   - reason templates parsed from the source line (Sources.kt:37-39): from the step's `inferred` text;
   - the `until(` text in the trace (Run.kt:269-270): from the condition.

   **Parity feature:** one capture, generated both ways, each run against the same service; the traces are equal
   but for file and line.
4. **Convert to Kotlin.** `artie convert NAME.heart.yaml --to kts`: the DSL for a person who wants to add code.
   One way only; Kotlin to YAML isn't possible in general.
5. **Native runner**, only if 0b is promising: a native ARTIE that runs `.heart.yaml` and goal files; `.kts` stays
   with the JVM ARTIE.
6. **`heart_do` writes YAML** (spec/PLAN-HEART-MCP-ARTIE-BACKED.md): a model's work, kept as data.
7. **Measure and report:**
   - startup and run time, for `.kts` on the JVM, YAML on the JVM and YAML native;
   - a model-written script that reaches outside the vocabulary, refused by the schema before it runs.

Not in this plan: a heart-ts runner, emitters for other languages, an Arazzo export of the OpenAPI twin, and an
ARTIE daemon (spec/ideas/artie-daemon.md, a future idea).

## The format (draft)

**Files**

| File | Written by | Role |
|---|---|---|
| `NAME.goal.md` | a person | The source; never changed by a tool |
| `NAME.heart.yaml` | `artie generate` | The kept script; derived, never hand-edited |
| `NAME.pins.yaml` | `artie generate` | The lock file, as now |
| `NAME.derivation.yaml` | `artie generate` | Ties the script to its source and run, as now |
| `NAME.kts` | `artie generate` or `artie convert`, on request | For a person to extend |

`.goal.yaml` stays refused (GoalFile.kt:15). `heart: 1` at the top tells a script from a trace.

**Vocabulary: what the generator emits, and its YAML**

| DSL | YAML |
|---|---|
| `act(rel, pairs, intent, inferred, fromIntent, fields)` | `act`, `intent`, `inferred`, `from-intent`, `fields` |
| every step's words | `intent`: always the words, whether the step is pinned or not |
| `act(intent = …)`, no relation: the goal file's `` `reasoned` `` | `reasoned: true`: the model decides this step on every run. Said, not implied by a missing `act` |
| `follow(rel, title)` | `follow`, `title` |
| nested block | `then: [steps]` |
| `keepAs(name)` | `keep-as` |
| `sync()` | `sync: true` |
| `decide { }` | `decide: [steps]` |
| `if (offers(r)) … else note(why(r))` | `if-offered: r`, `then`, `else`; `{why}` in a note |
| `check(facts)` | `check: [facts]` |
| `"m" eq v`, `"m" has v`, `at(name) { }` | `{ field, is }`, `{ field, has }`, `{ at, field, is }` |
| `watch(until = { facts })` | `watch: { until, every, at-most, steps }` |
| `whenever` with `has`, `leaveWhere` | `whenever: { has, leave: [{ where, say }] }` |
| `once(rel, giveUp, otherwise = { why -> })` | `once: { offered, give-up, steps, otherwise }` |
| `val x by input(default, question)` | `inputs: { x: { default, question } }`; a computed default only as a named generator (`{ now: MMdd-HHmmss }`), as goal files have |

**Values:** `{name}` fills an input, a kept value (`{watch}`), the state as read (`{state.title}`) or the server's
reason (`{why}`). It is text substitution; nothing is evaluated. Conditions are data, so the schema checks them.

**Mockup:** the merge watch, as written in the conversation of 2026-11-02, line for line with `merge.kts`. It
goes to `exhibits/merge-watch/merge.heart.yaml` in step 0c.

## Open

- **Extension:** `.heart.yaml`, or another.
- **An empty reason:** the Kotlin writes `${why ?: "no reason given"}`; YAML needs a rule. Proposed: the runner
  always fills "no reason given".
- **Bare scripts:** left as `.kts`. They are the unguarded baseline; making them data adds nothing.
- **Generate's default format:** `kts`, built in. The author switches it with one config setting, without a code
  change. Where that setting lives (beside the goal file, in the project, or for the person) is open.
- **Two formats to keep in step:** generate fails on any construct without a YAML key (step 2), and the parity
  feature runs on every new construct.
- **Vocabulary creep:** every new key is a step toward code. A key is added only for a construct the generator
  emits.

## Gate notes

- Features before `src/main`, through Edit/Write; the red run at test level, logged and checked.
- Step 1 is a refactor: the author switches refactor mode on.
- No change to spec/SPEC.md is expected. If one turns out to be needed, stop and ask.
