# Plan: a goal in Markdown, with a little structure and strict guards

Status: planned, 2026-10-21. Follows spec/PLAN-PLAIN-TEXT-RUN.md (a goal in words, built and proven live) and
replaces its `.goal.yaml` file. Pre-release: `.goal.yaml` goes once this works; no compatibility.

## The idea

A goal file is Markdown with a short YAML front matter. The front matter is the machine part: where to start and
the inputs. The body is words. A body of paragraphs is today's free goal. A body with one bulleted list is a goal in
steps: one intent per item, nesting for "inside what the step above led to", and two markers, `Wait:` and
`Verify:`. Nothing is numbered: the list's order is the order.

```markdown
---
start: http://localhost:8392/
inputs:
  owner:
  repo: heart-github-test
  stamp: { default: { now: MMdd-HHmmss } }
---

Fix the blank-bed-name crash, start to finish.

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name", saying a HEART script opened it `as issue`
  - From main, start a branch named fix-blank-names-{stamp}
    - Commit fix-{stamp}.md saying "Bed names may not be blank." with message "Refuse a bed with no name"
    - Open a pull request into main titled "Refuse a bed with no name", relating to {issue} `as pr`
      - Wait: it can be merged
      - Merge it by squashing
      - Verify: it merged
  - On {issue}
    - Comment "Fixed in 1.2 by #{pr.number}."
    - Close it as completed
    - Verify:
      - it is closed
      - its reason is completed
```

It renders as a plain nested list on GitHub, in an editor's preview and in the report, and that rendering is what
ARTIE runs.

## Why

- **Free text left the structure to the planner.** The live GitHub run took 22 reason steps for 9 things to do:
  detours, refused tries, a "go back to the issue" the planner had to work out. Nesting and markers say it outright.
- **Markdown is what a person writes notes in.** No `intent:` / `steps:` keys, no YAML quoting, and it renders.
- **Guards keep it strict.** YAML refused an unknown key; the rules below refuse more than that (a typo'd marker, a
  name used before it is given, a second list), each before the first request.
- **Braces, not `$`.** Renderers read `$owner/$repo` as inline math (GitHub included). `{name}` is untouched by
  every renderer, and is already HEART's slot form in trace sentences and URI templates (RFC 6570).

## The file

`NAME.goal.md`, run by `artie run NAME.goal.md --reasoner claude`.

### Front matter

Between `---` lines, first in the file. Keys `start` (required), `inputs` and `limits` (optional), with the input
forms of today's goal file: `name: value`, `name:`, `name: {default, question}`, `default: {now: PATTERN}`. Any
other key stops the run.

### Limits

A goal run's moves are the model's choices, made with no one watching, so a run has caps: a planner stuck on a
refused act, or one that misreads "done" and acts again, stops with a message instead of calling the model and
acting on a live service without end.

- **The run:** at most 50 moves (was 40). `limits: {moves: N}` in the front matter, or `--max-moves N`, changes it.
- **A step:** at most 10 moves for one item, so a stuck item stops the run early, named by its line.
  `limits: {step: N}` sets it for every step; `` `moves N` `` at an item's end sets it for that item alone.
- The command line wins over the front matter, the item over both. A limit that is not a whole number above 0
  stops the run before its first request.

### Body

One of two shapes:

1. **Paragraphs only: a free goal.** As today: split into sentences, a quotation never split.
2. **Paragraphs, then one bulleted list: a goal in steps.** The paragraphs are context: the planner reads them,
   never ticks them off. The list is the steps.

### Steps

| Written | Is | The script it becomes |
|---|---|---|
| `- words` | an intent: one or more acts that do it | `act(intent = …)` |
| an item with sub-items | an intent, then its sub-items inside the resource it led to | a block `{ … }` |
| `` `as name` `` at an item's end | names the resource the item led to | a variable named `name` |
| `- Wait: condition` | read again until the condition holds | `until { … }` |
| `- Verify: fact` or `- Verify:` with fact sub-items | each fact must hold now | `check(…)` |

A bullet is `-`, `*` or `+`; siblings use the same one.

### Names in braces

- `{name}` is an input or an earlier `as` name. `{pr.number}` is a member of a named resource's state, read when the
  step runs. `{{` and `}}` are literal braces.
- A named resource in words stands as its address; the planner is shown each named resource, its address and its
  state, under `<named>`.
- In the generated `.kts` a brace becomes a Kotlin slot: `{stamp}` is `$stamp`, `{pr.number}` is a read of `pr`'s
  state.

## Guards

Each stops the run before its first request, naming the file, the line and what to write instead
(`task.goal.md:14: "Verfy:" is no marker: write Wait: or Verify:, or put the words another way`):

1. Front matter: missing, not first, not YAML, a key other than `start` and `inputs`, an input form not listed.
2. Body shape: a heading, a table, a code block, a quote, a numbered list, a second list, or a paragraph after the
   list.
3. Bullets: siblings with different markers; indentation that is not 2 spaces a level.
4. Markers: an item starting `Word:` where `Word` is not `Wait` or `Verify`. A colon later in the words is words.
5. `Wait:` with sub-items; `Verify:` with both a fact after the colon and fact sub-items, or with neither; a fact
   with sub-items of its own.
6. Backticks: anything but `` `as name` `` and `` `moves N` ``, each at most once, at an item's end; a name given
   twice; a name an input already has.
7. Braces: `{x}` that is no input and no `as` name; `{x}` used before (above, or outside of) the step that names
   it; an unclosed brace.

`artie run NAME.goal.md --plan-only` reads the file, applies the guards, and prints the tree it read, each step with
its kind and depth. It sends nothing.

## The run

### Reading (artie-cli)

- `GoalFile.kt` reads `.goal.md`: the front matter with SnakeYAML, the body with commonmark-java (source spans on),
  so what runs is what renders. The guards walk the parsed document.
- The result is a `Goal` with `start`, `inputs`, `context` (the paragraphs) and either `text` (a free goal) or
  `steps`, a tree of `Step.Intent(words, name, children)`, `Step.Wait(condition)`, `Step.Verify(facts)`, each with
  its line.

### Doing (heart-script)

A free goal runs as today (with braces). A goal in steps runs step by step, and the runner owns the structure the
planner had to work out before:

- **One step at a time.** The planner sees the current step, its ancestors (where it is), the steps done, what is
  held, the named resources, and the context.
- **Moves a step allows.** An intent: `Act`, then `Done` for that step; `Clarify` and `Stop` as now. A `Wait:` step:
  one `Wait` move. A `Verify:` step: one `Check` move. Any other move is refused, told to the planner, and changes
  nothing.
- **Nesting.** A step's sub-items start at the resource its last opening act led to. When they are done, the runner
  goes back to where the step began. No `Back` move in a goal in steps, and no `Finish`: the run ends when the
  last step is done.
- **Names.** At an `as name` step's end, the resource it led to is bound to `name`: its address now, its state read
  again when a later step reads `{name.member}`.
- **The capture** is as today: one synthetic line a move in `run.source`, with braces written as Kotlin slots. Each
  step's line in the goal file is kept beside it, so the report can show which item each move served.

### Planning (artie)

`ClaudePlanner` gets a second turn shape for steps: `<context>`, `<steps>` (the tree, done ones marked, the current
one marked), `<named>`, then `<path>`, `<held>` and `<answers>` as now. The tool's move kinds are narrowed to what
the current step allows, so a wrong kind cannot be chosen.

### Generating (artie-cli)

- Nesting comes from the steps: a step with sub-items is a block.
- An `as name` gives the variable its name: `issue`, `pr`, not `number13`.
- `Wait:` and `Verify:` steps become `until` and `check` lines as the capture kept them.
- Detours (spec/PLAN-PLAIN-TEXT-RUN.md) are still left out; there should be fewer.

### Reporting

The derivation report's goal section is made from the goal file by the builder, not by hand: the front matter
highlighted as YAML; the body rendered; Brief shows the rendered goal, Full adds its source and the explanation.
`docs/reports/goal-to-script/intro.html` goes.

## Build order

Features first in each, red at test level, then code.

1. **The file and its guards** (artie-cli): a feature with a scenario per body shape, and an outline over every
   guard with the message it gives. `--plan-only` prints the tree. A free goal in `.goal.md` runs as the
   `.goal.yaml` one does (`goal-run.feature`, moved to braces).
2. **Steps, run** (heart-script, artie): scripted planner. Nesting and the runner's own way back; moves a step
   allows; `as` names and `{name.member}`; `Wait:` and `Verify:`; the run ending at the last step.
3. **Steps, generated** (artie-cli): blocks from the steps; variables named by `as`; until and check.
4. **ClaudePlanner** for steps: the turn's tags, the narrowed move kinds (`goal-planner.feature`).
5. **The report**: the goal section from the file, Brief and Full.
6. **Live**: `github-task.goal.md` in steps, by Claude, on the scratch repository; generated; again strict with no
   model. Compared with the paragraph run: reason steps, refused tries, detours left out.
7. **`.goal.yaml` goes**: its reading, its tests, its file; spec/PLAN-PLAIN-TEXT-RUN.md points here.

## Decided (2026-10-21)

- **`{issue}` in words is the resource's address.** The planner also sees it under `<named>`.
- **Facts are words.** "it merged"; the planner turns each into a Check move.
- **Limits:** 50 moves a run, 10 a step, each settable (above).
- **`.goal.yaml` goes** once `.goal.md` works.

## Open

- **Promotion.** `artie generate --steps` writing a `.goal.md` in steps from a free goal's run: free words, then
  steps, then a script, each firmer.
