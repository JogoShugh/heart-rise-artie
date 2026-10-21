# Plan: a plain-text run, from a bookmark and a paragraph to the final script

Status: plan, 2026-10-20. Nothing built.

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

## Open

- **Where the text may say structure.** Plain paragraphs only, or also lines and bullets as hints of order? Lines
  first; the planner gets both.
- **Value judgement.** "Fixed in 1.2 by #<the pull request>": the planner fills it from the pull request's number;
  generate traces it. A value the planner judged (not quoted, not traced) is marked for review, as today.
- **Inputs not declared.** A literal the text gives (a repository name) stays a literal unless declared; generate
  could propose inputs for literals that appear in addresses. Later.
- **Cost.** One model call per move on the first run only. Prompt caching of the text and the profile's
  descriptors keeps it down.
- **Other services.** The garden first after GitHub: "water bed 2 with about 250 ml, then check it's watered", the
  same text over HTTP and CoAP.
