# The GitHub task, as YAML

Take HEART, Part 1, slides 30 to 34, with the scripts held as data instead of Kotlin. The task: open an issue,
branch, commit a file, open a pull request, wait until merge is offered, merge it, then comment on the issue and
close it. Live on `JogoShugh/heart-github-test`, through the GitHub gateway on `https://localhost:8393/`,
5 November 2026. Reasoner: Claude Sonnet 5.5.

## The scripts

| Script | Rung | Made by |
|---|---|---|
| `github-task.intent.heart.yaml` | intent: every act in words | `artie generate --format yaml --intents` |
| `github-task.heart.yaml` | kept: every act pinned, no model | `artie generate --format yaml` |
| `github-task.hybrid.heart.yaml` | hybrid: kept, with the close left in words | the kept script, one step changed by hand |
| `*.pins.yaml` | the lock file of each | `artie generate` |

- **Where they came from:** one reasoned run of `github-task-reasoned.kts`, the October script (`runs/reasoned-kts`).
- **Why a Kotlin run:** a run of a YAML script doesn't yet record its steps' nesting or where each value came
  from, so a script generated from it nests flat and keeps run values (`#2170`) as literals. That is the next
  slice, if wanted.
- **`hand/`:** the intent script written by hand from the October run's words, before it was generated. Runs
  `intent-1` and `intent-2` used it.

## The runs

| Run | Script | Model calls | Seconds | Steps | Issue, PR |
|---|---|---|---|---|---|
| `reasoned-kts` | `github-task-reasoned.kts` | 10 | 54 | 23 | #2172, #2173 |
| `intent-yaml` | `github-task.intent.heart.yaml` | 10 | 53 | 24 | #2174, #2175 |
| `intent-1`, `intent-2` | `hand/github-task.intent.heart.yaml` | 10, 10 | 51, 54 | 24 | #2168, #2169 · #2170, #2171 |
| `kept-1` | `github-task.heart.yaml`, guard lenient | 0 | 32 | 28 | #2176, #2177 |
| `kept-strict` | `github-task.heart.yaml`, guard strict | 0 | 32 | 28 | #2178, #2179 |
| `hybrid-1` | `github-task.hybrid.heart.yaml`, guard strict | 1 | 38 | 26 | #2180, #2181 |

- Every run merged its pull request and closed its issue as completed. No act was refused.
- The kept replay has two more checks than the reasoned run: `artie generate` checks what each act changed.
- In the hybrid, Claude chose `github:close` of the three ways to close; that step took 9.1 s.
- Times include walking three pages of open pull requests the scratch repository now holds.
- **`broken-1`:** the first try of the hand script, stopped at the pull request: an unquoted `#` in YAML began a
  comment, so the words ended at "saying it relates to". Its issue, #2167, was closed as not planned.

## What the runs turned up, fixed test first (commit 2da62ab)

- **`--intents` lost the numbers.** A step in words wrote the value an earlier answer gave (`open issue 113`), not
  where it came from. It now keeps the resource and reads `${issue.number}`.
- **A script's own check froze its value.** `is: "Fixed in 1.2 by #114."` now reads `#${pull.number}`.
- **A hash was checked.** After a merge, `merge_commit_sha` was checked by its value; a hash is now left out, like
  a time.
- **A YAML until said nothing.** A run of a script as data didn't record what its until waited for, so
  `artie generate` stopped at it. It now records `offers("github:merge")`, as a Kotlin run does.

## Run again

```
./run.sh NAME SCRIPT repo=heart-github-test [--guard strict] [--reasoner claude]
```

`run.sh` names the lock file beside the script outright, since artie looks for it in the working folder. The
gateway on 8393 is a second one, set as the one on 8392 but with the current token.

## The deck

`deck/edit.mjs` changes slides 30 to 34 and 37 of the unpacked `Take_HEART_1_1.pptx`; the result is
`docs/decks/take-heart/Take_HEART_1_2.pptx`. Slide 32 keeps October's Kotlin timings, labelled as such.
