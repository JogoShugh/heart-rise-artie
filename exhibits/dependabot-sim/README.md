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
| `checks.kts` | A required Actions check and the rival (`gh pr merge --auto` on `dependabot-actions`) | GitHub, bare; **not yet run** |

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

- **Without workflow or commit-status write access the token gets 403,** so `checks.kts` (the required check and
  the rival) can't run yet. With no required check, "once checks pass" means only that GitHub says the pull
  request can be merged.
- **`note()` in heart-script is kept in `Run.notes` but never written to the trace or printed.** `watch.kts` works
  around it with `tell()`.
- **The trace keeps every representation in full:** 3.6 MB for six minutes. To serve as a long watch's journal, it
  needs to keep only what changed. Traces stay out of git here (`.gitignore`).
- **The gateway's list pages carry no ETag,** so each look reads the list in full from the gateway (but not from
  GitHub).
- **Pull requests can't be listed by label through the gateway,** so the watch reads each changed pull request to
  see its labels.
- **No reviews resource exists,** so a person objects with the `hold` label rather than with "changes requested".
