# The GitHub task, from a plain goal to YAML

Take HEART, Part 1, slides 30 to 34, with nothing written by hand in Kotlin or YAML. The task: open an issue,
branch, commit a file, open a pull request, wait until merge is offered, merge it, then comment on the issue and
close it. Live on `JogoShugh/heart-github-test`, through the GitHub gateway on `https://localhost:8393/`,
5 November 2026. Reasoner: Claude Sonnet 5.5.

## The chain

1. **The goal, in plain words:** `github-task.goal.md`. Claude runs it once, planning each line into moves and
   choosing among what the gateway offers.
2. **`artie generate --format yaml`** reads that run and writes `github-task.heart.yaml`: every act pinned, each
   value read again as a reference (`${issue.number}`, `${pr.number}`, `${stamp}`), and its lock file.
3. **The replay:** the kept YAML, with no model, held strictly to the lock file.
4. **The hybrid:** `github-task.hybrid.goal.md` is the same goal with its close marked `reasoned`; generated from
   its run, `github-task.hybrid.heart.yaml` keeps that one step in words and pins the rest.
5. **The case named (the slide after the hybrid):** `github-task.directed.goal.md` says the close as its case,
   "Close it as completed, since the fix shipped in 1.2", so only one of the three closes fits. `artie generate`
   pins `github:close` with no reason step, and the replay needs no model.

## The runs

| Run | Script | Model calls | Seconds | Steps | Issue, PR |
|---|---|---|---|---|---|
| `goal-2` | `github-task.goal.md` | 23 | 99 | 25 | #2187, #2188 |
| `goal-kept` | `github-task.heart.yaml`, guard strict | 0 | 28 | 28 | #2189, #2190 |
| `goal-1` | `github-task.hybrid.goal.md` | 23 | 106 | 25 | #2183, #2184 |
| `goal-hybrid` | `github-task.hybrid.heart.yaml`, guard strict | 1 | 33 | 27 | #2185, #2186 |
| `goal-directed` | `github-task.directed.goal.md` | 23 | 96 | 25 | #2191, #2192 |
| `directed-kept` | `github-task.directed.heart.yaml`, guard strict | 0 | 28 | 28 | #2193, #2194 |

- Every run merged its pull request and closed its issue as completed. No act was refused.
- The kept replay has two more checks than the goal run: `artie generate` checks what each act changed.
- In the hybrid, Claude chose `github:close` of the three ways to close; that step took 5.8 s.
- Times include walking the pages of open pull requests and branches the scratch repository now holds.
- **`goal-wandered`:** the goal as first written ("From the main branch, start a new branch…") sent the planner
  paging through branches until it gave up. Said as "Open the branch main of {owner}/{repo}", it goes straight
  there. Its issue, #2182, was closed as not planned.

## Not yet

- **A run of a YAML script can't be generated from.** It doesn't record its steps' nesting or where each value came
  from, so a script generated from it nests flat and keeps run values (`#2170`) as literals.
- **`--intents` from a goal run** still writes some values as literals ("get issue 2187"). The goal itself is the
  words, so it isn't needed here.

## `from-kotlin/`: the first try, kept for the record

Before the goal chain, the YAML scripts were generated from a run of the October Kotlin script
(`github-task-reasoned.kts`): intent, kept and hybrid, each run live (`reasoned-kts`, `intent-*`, `kept-*`,
`hybrid-1`, and `broken-1`, which stopped where an unquoted `#` began a YAML comment; its issue, #2167, was closed).
Those runs turned up four bugs, fixed test first in commit b46a61d:

- `--intents` wrote the value an earlier answer gave (`open issue 113`); it now reads `${issue.number}`.
- A script's own check froze its value (`#114`); it now reads `#${pull.number}`.
- A hash an act changed (`merge_commit_sha`) was checked by its value; it is now left out, like a time.
- A YAML until didn't say what it waited for, so `artie generate` stopped at it; it now records
  `offers("github:merge")`.

## Run again

```
./run.sh NAME github-task.goal.md --entry https://localhost:8393/ --reasoner claude
./run.sh NAME github-task.heart.yaml --guard strict
```

`run.sh` names the lock file beside the script outright, since artie looks for it in the working folder. The
gateway on 8393 is a second one, set as the one on 8392 but with the current token.

## The deck

`deck/edit.mjs` changes slides 30 to 34 and 37, and adds the slide after the hybrid, of the unpacked `Take_HEART_1_1.pptx`; the result is
`docs/decks/take-heart/Take_HEART_1_2.pptx`. Slide 32 keeps October's Kotlin timings, labelled as such.
