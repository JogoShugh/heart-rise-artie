# The GitHub task, from a plain goal to YAML

Take HEART, Part 1, slides 30 to 35, with nothing written by hand in Kotlin or YAML. Live on
`JogoShugh/heart-github-test`, through the GitHub gateway on `https://localhost:8393/`, 5 November 2026.
Reasoner: Claude Sonnet 5.5.

Nothing that varies is written into a script. Each value is an input (`{owner}`, `{repo}`, `{stamp}`, `{version}`,
the labels) or found on the walk and kept (`as issue`, `as pr`), and the generated YAML reads it again as a
reference: `${version}`, `${issue.number}`, `${pr.number}`.

## The chain

1. **The goal, in plain words:** `github-task.goal.md`. Claude runs it once, planning each line into moves and
   choosing among what the gateway offers.
2. **`artie generate --format yaml`** reads that run and writes `github-task.heart.yaml`, every act pinned, and its
   lock file.
3. **The replay:** the kept YAML, with no model, held strictly to the lock file, given another release.
4. **The hybrid:** `github-task.hybrid.goal.md` marks its close `reasoned`; generated from its run,
   `github-task.hybrid.heart.yaml` keeps that one step in words for the model, and pins the rest.
5. **Every case named:** `closeout.goal.md`, a watch, closes each issue labelled `{label}` the way its labels say
   it ended: `{shipped}` as completed, `{wontfix}` as not planned, `{duplicate}` as a duplicate. Three issues are
   staged, one per label, so the walk meets every case; `artie generate` keeps each If as a `given:` on the
   issue's labels, its close pinned. The replay needs no model.

## The runs

| Run | Script | Given | Model calls | Seconds | Result |
|---|---|---|---|---|---|
| `goal-v` | `github-task.goal.md` | version=1.2 | 23 | 90 | #2195, PR #2196 |
| `goal-v-kept` | `github-task.heart.yaml`, strict | version=1.3 | 0 | 27 | #2197, PR #2198: "Fixed in 1.3 by #2198." |
| `goal-hv` | `github-task.hybrid.goal.md` | version=1.2 | 23 | 92 | #2215, PR #2216 |
| `goal-hv-kept` | `github-task.hybrid.heart.yaml`, strict | version=1.3 | 1 | 34 | #2217, PR #2218; Claude chose `github:close` |
| `closeout-goal` | `closeout.goal.md` | version=1.3 | 22 | 132 | #2207 completed, #2208 not planned, #2209 duplicate |
| `closeout-kept` | `closeout.heart.yaml`, strict | version=1.4 | 0 | 52 | #2211 completed, #2212 not planned, #2213 duplicate |

- Every pull request merged; every issue closed the way its case called for. No act was refused.
- A kept replay has more steps than its goal run: `artie generate` checks what each act changed.
- Times include walking the pages of open pull requests and branches the scratch repository now holds.
- `closeout.sh` stages the three issues, runs the watch, and closes the watch's issue once all three are closed.

## Tries that didn't count

| Run | What happened | Left on GitHub |
|---|---|---|
| `goal-wandered` | "From the main branch, start a new branch…" sent the planner paging through branches; "Open the branch main of {owner}/{repo}" goes straight there | #2182, closed as not planned |
| `closeout-stopped` | The API ran out of credit after 16 calls | #2199 and watch #2202, closed as not planned |
| `closeout-stuck` | One If did two acts ("comment … and close it"); after the comment it couldn't get back to the issue. Now two Ifs | #2203 and watch #2206, closed as not planned |
| `goal-1`, `goal-2`, `goal-kept`, `goal-hybrid`, `goal-directed`, `directed-kept` | Earlier runs with the release written into the words as "1.2"; superseded | — |

## Not yet

- **A run of a YAML script can't be generated from.** It doesn't record its steps' nesting or where each value came
  from, so a script generated from it nests flat and keeps run values as literals.
- **A case the walk never met** stays in words for the model in the generated script: stage every case you mean to
  keep.

## `from-kotlin/`: the first try, kept for the record

The YAML generated from a run of the October Kotlin script (`github-task-reasoned.kts`), and its runs. They turned
up four bugs, fixed test first in commit b46a61d:

- `--intents` wrote the value an earlier answer gave (`open issue 113`); it now reads `${issue.number}`.
- A script's own check froze its value (`#114`); it now reads `#${pull.number}`.
- A hash an act changed (`merge_commit_sha`) was checked by its value; it is now left out, like a time.
- A YAML until didn't say what it waited for, so `artie generate` stopped at it; it now records
  `offers("github:merge")`.

## Run again

```
./run.sh NAME github-task.goal.md version=1.5 --reasoner claude
./run.sh NAME github-task.heart.yaml version=1.5 --guard strict
VERSION=1.5 ./closeout.sh NAME closeout.heart.yaml --guard strict
```

`run.sh` names the lock file beside the script outright, since artie looks for it in the working folder. The
gateway on 8393 is a second one, set as the one on 8392 but with the current token.

## The deck

`deck/edit.mjs` changes slides 30 to 34 and 37 of the unpacked `Take_HEART_1_1.pptx` and adds the close-out slide
after the hybrid; the result is `docs/decks/take-heart/Take_HEART_1_2.pptx`. Slide 32 keeps October's Kotlin
timings, labelled as such.
