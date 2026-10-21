# Plan: a plain-text run, from a bookmark and a paragraph to the final script

Status: built and proven live, 2026-10-20 (commits cee2197 to 58e9087). The GitHub goal
(`client/scripts/tests/github-task.goal.yaml`) was done live by Claude's planner (issue #144, pull request #145);
`artie generate` made `client/scripts/generated/github-task-from-goal.kts` from it, which ran live, strict, with
no model (#146, #147). Reports: `docs/reports/line-by-line/github-goal-goal/` and `github-goal-gen/`. What the
live runs taught is under "Learned live"; what is left, under "Open".

## The idea

A person writes what they want done as plain text: a bookmark, then a paragraph or a few lines. No nesting, no
relation names, no `act(…)` calls. ARTIE, with Claude as its reasoner, does it against the live service, one move at
a time from what each representation offers. The run's capture then goes to `artie generate`, which writes the final
form already shown for the GitHub task: nested blocks, relation names, `intent = "…"` words, values traced to where
they came from, a lock file. From then on the task runs with no model.

```
Start at http://localhost:8392/.
inputs: owner = JogoShugh, repo = heart-github-test, stamp
In $owner/$repo, open an issue titled "Crash when a bed has no name (reasoned)", saying it was opened by a HEART
script. From main, start a branch fix-blank-names-$stamp, commit fix-reasoned-$stamp.md saying "Bed names may not
be blank.", and open a pull request into main that relates to the issue. Wait until it can be merged, squash-merge
it, and make sure it merged. Back on the issue, comment "Fixed in 1.2 by #<the pull request>." and close it as
completed.
```

```
artie run task.txt --reasoner claude --rung L2 --trace task.trace.yaml
artie generate task.trace.yaml --out task.kts --script task.txt
```

## Why it is mostly built

- **The final form comes from a capture.** `artie generate` already writes nesting ("nested as the run nested"),
  relation names, intent words with their values as slots (`Intent.kt`), each value's source (`Sources.kt`,
  `Derivations.kt`), waits and checks from what the capture kept (`Waits.kt`), the lock file (`LockWriter.kt`).
- **A reasoned step already chooses from what is offered.** `ClaudeReasoner` picks one `Selection` (act, follow,
  describe, explain, clarify) from the representation held, given the step's words.
- **What the reasoned script adds today** is structure the person wrote: one intent per call, and nesting that says
  which resource each act is taken from. Plain text drops both. The reasoner must work them out.

## What is new

### 1. A text source (artie-cli, heart-script)

- `artie run` accepts a `.txt` (or `.task`) file: a first line `Start at <bookmark>`, an optional `inputs:` line
  (name = value, or a bare name for a value asked at run time, as `input()` is), then free text.
- The text is split into sentences, numbered, and kept as the run's plan. `$name` in the text is an input, filled
  before the reasoner sees it, and kept as a slot so generate writes it back as `$name`.

### 2. A planning loop (artie: a new `Planner` beside `ClaudeReasoner`)

Each turn the reasoner sees: the whole text, sentence by sentence, each marked done or not; the moves made so far,
one line each, with what each answered; the resources visited, by address and title; and the representation held,
its state, forms and links, as a reason step sees them today. It answers with one move:

- **act** on an offered form, with values, naming the sentence it serves;
- **follow** a link (as today);
- **back** to a resource already visited, by its address: "back on the issue";
- **wait** until a relation is offered at a resource, re-reading it: "until it can be merged";
- **check** facts of the state, by pointer and value: "make sure it merged";
- **done** with a sentence, or **stop**: the task is finished, or can't be, saying why;
- **clarify**: ask the person (as reason steps can), when the text and the offers leave it open.

ARTIE does each move as the script DSL does it (the same `Navigator` calls), so every move is checked the same
way: forms only as offered, values against the schema, If-Match, the guard's rules where there is a lock file.

**Drift.** The planner may not stop while a sentence is not done, and may not act twice for one sentence without
saying why. A sentence marked done names the moves that did it. A cap on moves (say 3 per sentence, 40 in all)
stops a loop, as a failed run.

### 3. The capture says what the generator needs

Each move is a step as today, plus:

- `sentence`: the number of the sentence it served, and its words, which become the step's `intent`;
- `from`: the resource the move was chosen from. Nesting follows it: a move from the resource the last act led to
  opens a block; `back` closes blocks up to the resource it names (`within`, `opens` as today);
- `wait` and `check` moves are steps of their own, kept as `waited` and the facts, as `until` and `check` are.

### 4. Generate reads a text source

- `--script task.txt`: intents come from the sentence words, with `$inputs` and traced values as slots; a sentence
  that served several moves is split into one intent per move, quoting the clause each move did, as the planner
  named it.
- `wait` and `check` moves are written by `Waits.written` (no source lines to copy).
- Inputs from the text's `inputs:` line are declared `val owner by input()`.
- Nothing else changes: the lock file, derivation file and value rules are the same.

## Proof

1. **Features first** (client): the planning loop against the small GitHub in process, with a scripted planner, as
   reason steps are tested today (no model): the GitHub task from text; `back` closes blocks; `wait` becomes
   `until`; a sentence left undone stops the run; a cap on moves stops a loop; `clarify` asks.
2. **Generate from text**: the script it writes from the scripted run matches the hand-nested reasoned script move
   for move (relations, nesting, values, waits, checks), apart from the intent words.
3. **Live, with Claude**: the task text against the gateway on the scratch repository, three runs; generate from
   one; run the generated script strict, live. Report: the text, the run line by line, the script generated, side
   by side with the hand-written reasoned one.

## Learned live (2026-10-20)

- **A refused act is a turn, not an end.** The planner kept reaching for GitHub's `git://` refs link to make a
  branch, which ARTIE refuses before sending. Each refusal is now told to the planner, which then goes back to
  the start and looks the branch up; the capture keeps none of it, so the script has only the acts done.
- **A wait must name its form.** Once the planner waited without naming merge, and the run waited for nothing.
- **The person's words are the goal, not the planner's.** A value the goal says is a constant; one only the
  planner wrote (an issue's number, "#141") is the earlier answer's, so the script reads it there, its intent
  words too. A number inside a decimal ("1.2") is no whole value.
- **The planner wanders.** Runs still show a detour or two (the branches list, a repository read twice), which
  the generated script repeats faithfully. Cheap to replay; worth trimming later.

## The goal file (decided 2026-10-20)

A YAML file, `NAME.goal.yaml`, with three keys. Example: `client/scripts/tests/github-task.goal.yaml`.

- `start`: the bookmark.
- `inputs`: what varies per run, each as a script's `input()` declares it. `name: value` is short for a default;
  `name:` with nothing has none, so it must be given or asked; `name: { default, question }` gives either or both;
  `default: { now: PATTERN }` is a fresh value each run, the time now as a `java.time` pattern. A value given when
  running wins, then the default; with neither the run asks the question, or stops before its first request.
- `goal`: the task in words, a `|` block, a paragraph or one step per line. `$name` is an input. It is `goal`,
  not `intent`: `intent` stays the words of one step, which generate cuts from the goal.

Room to grow: an input's map takes more keys later (choices, a type) without changing the short forms, and the file
takes more top-level keys (a rung, a reasoner). A key ARTIE doesn't know stops the run, naming it, rather than being
ignored, so a file written for a later ARTIE never runs half-understood.

## Decided (2026-10-20, with the author)

- **Text shape:** a paragraph or one step per line, both split into numbered sentences; line order is a hint, not
  a rule.
- **Inputs:** declared on the `inputs:` line; `$name` in the text uses them. Generate proposes none of its own.
- **Ambiguity:** the planner asks the person (clarify), as reason steps can; it never guesses.
- **First proof:** the GitHub task, against the hand-nested reasoned script.

## Open
- **Value judgement.** "Fixed in 1.2 by #<the pull request>": the planner fills it from the pull request's number;
  generate traces it. A value the planner judged (not quoted, not traced) is marked for review, as today.
- **Cost.** One model call per move on the first run only. Prompt caching of the text and the profile's
  descriptors keeps it down.
- **Other services.** The garden first after GitHub: "water bed 2 with about 250 ml, then check it's watered", the
  same text over HTTP and CoAP.
- **Trimming detours.** Generate could drop reads and follows that led nowhere (a block with no act, no read of a
  value used later), so the script does only what the goal needed.
- **A clearer stop without a model key.** `artie run` says "this client has no planner" when the reason is a
  missing model key; it should say where the key goes, as reason steps do.
- **A side-by-side report.** The goal text beside the script generated, as the derivation report shows a
  reasoned script beside its generated one.
