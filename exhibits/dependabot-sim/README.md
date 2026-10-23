# A Dependabot stand-in, and a watch that merges its pull requests

This is the Dependabot goal (`exhibits/goal-syntax/goals/dependabot.goal.md`), simulated on the scratch repository
JogoShugh/heart-github-test with the author's own account. No Dependabot is involved: `bump.kts` opens pull requests
the way Dependabot would, and labels them `dependabot`. `watch.kts` is the goal written by hand in heart-script,
because the goal words Whenever, Until, Once, Otherwise and Give up don't exist yet. Each word is the loop or branch
it will come down to (see the comment at the top of `watch.kts`).

| Script | What it does | Through |
|---|---|---|
| `setup.kts` | Makes the base branch `deps-main` holding `deps.txt`, and the labels `dependabot`, `dependabot-actions` and `hold` | GitHub, bare |
| `bump.kts` | A Dependabot stand-in: a branch that bumps left-pad, and a labelled pull request into `deps-main` | GitHub, bare |
| `person.kts` | A person: adds or removes a label, or closes an issue (closing the watch's issue ends the watch) | GitHub, bare |
| `watch.kts` | The watch: merges each labelled pull request once it can, leaves held ones alone, and gives up with the reason | the HEART gateway |
| `checks.kts` | A required Actions check and the rival (`gh pr merge --auto` on `dependabot-actions`) | GitHub, bare |

All of them run under `artie run`, for example:

```
artie run bump.kts owner=JogoShugh from=1.0.2 to=1.0.3 draft=true
artie run watch.kts owner=JogoShugh gateway=https://localhost:8392/ every=10 giveUp=2 --rung L2
```

## The run of 2026-10-23 (`runs/watch-1023-watch2.out`)

The watch ran for about six minutes, looking every 10 seconds and giving up after 2 minutes. It ran 307 steps over
23 looks.

| Pull request | What happened to it | What the watch did |
|---|---|---|
| #678 bump to 1.0.1 | Open and labelled before the watch started | Merged it on the first look |
| #681 bump to 1.0.2 | Opened while the watch ran | Merged it at the next look |
| #682 bump to 1.0.3 | Opened as a draft | Waited 2 minutes, then commented: "A draft can't be merged; mark it ready for review first." |
| #683 bump to 1.0.4 | Opened already labelled `hold`; a person removed `hold` later | Left it alone while held. Once released, it conflicted, so the watch gave up and said so |
| #684 bump to 1.0.5 | Opened together with #685, from the same base | Merged it |
| #685 bump to 1.0.6 | Conflicted once #684 was merged | Gave up and commented: "The branch has conflicts with its base; resolve them first." |
| #680 the watch's issue | A person closed it | The watch stopped at its next look (Until) |

The reasons in the comments are the gateway's own (`hr:unavailable`), not written in the script.

## The run with checks, and the rival (`runs/watch-1023-watch3.out`)

`checks.kts` added the required check `deps-check`, which fails when a `.txt` file says "broken", and protected
`deps-main` with it, for admins too. It also added the rival: an Actions workflow that runs `gh pr merge --auto`
when a pull request is labelled `dependabot-actions`. Each bump wrote its own file, so the bumps didn't conflict.

| Pull request | Labelled | What happened | Who merged it |
|---|---|---|---|
| #687 | `dependabot` | The check passed at 16:52:09; the watch merged it at 16:52:45 | the watch |
| #688, a broken bump | `dependabot` | The check failed. After 4 minutes the watch commented: "Branch protection blocks the merge: a required check or review is missing." | nobody |
| #689 | `dependabot`, then `hold` | A person added `hold` within seconds; the watch left it alone and said so once | nobody |
| #690 | `dependabot-actions` | Auto-merge was turned on; GitHub merged it once the check passed | github-actions[bot] |
| #691 | `dependabot-actions`, then `hold` | Auto-merge was on 10 seconds after the label. A person added `hold` at 16:52:36, but GitHub merged it anyway at 16:53:02 | github-actions[bot] |

**The rival can't be told "no".** Once auto-merge is on, GitHub merges as soon as the check passes, and the
person's `hold` counts for nothing. The watch reads the pull request again on each look, and acts only on what it
holds now.

**The watch had a fault, now fixed (`runs/watch-1023-watch4.out`).** When restarted, it took up the old pull
requests again and commented on #682, #683 and #685 a second time. Now, before it comments, it reads the pull
request's comments and says nothing that any watch has already said. It also no longer gives up on a passing
reason ("GitHub is still checking… read it again shortly"), which once told #683 something untrue. A fourth watch,
with a give-up time of 1 minute, said nothing new on any of them.

## What it showed

- **The five words come down to small, plain code.** Whenever is a loop over open pull requests, most recently
  updated first, read down to the first one older than the last look. Once is `offers("github:merge")`. Otherwise is
  a comment carrying `why("github:merge")`. Give up is a time limit. Until is reading the watch's own issue.
- **Watching is cheap on GitHub.** Each look at an unchanged pull request is a conditional request from the
  gateway, answered 304.
- **The first watch (`runs/watch-1023-watch1.out`) died on one bad look.** The gateway answered 500 on a list read
  while GitHub was slow. A watch must survive that, so each round now catches the failure, says so, and looks again
  next time.

## Gaps found

- **There is no checks resource.** A failed check and a pending one give the same reason ("a required check or
  review is missing"), so the watch can't tell which it is.
- **`hr:unavailable` doesn't say whether a reason is temporary.** In place of such a mark, the watch looks for the
  words "read it again".
- **The watch remembers nothing between runs.** It learns what it already said by reading the comments; resuming
  from the trace would do better.
- **`checks.kts` needs a token that can write workflows and commit statuses.** A protected `deps-main` takes no
  direct commits, so the script lifts the protection, writes, and protects the branch again.
- **`note()` in heart-script is kept in `Run.notes` but never written to the trace or printed.** `watch.kts` works
  around it with `tell()`.
- **The trace keeps every representation in full:** 3.6 MB for six minutes. To serve as a long watch's journal, it
  needs to keep only what changed. Traces stay out of git here (`.gitignore`).
- **The gateway's list pages carry no ETag,** so each look reads the list in full from the gateway (but not from
  GitHub).
- **Pull requests can't be listed by label through the gateway,** so the watch reads each changed pull request to
  see its labels.
- **No reviews resource exists,** so a person objects with the `hold` label rather than with "changes requested".
