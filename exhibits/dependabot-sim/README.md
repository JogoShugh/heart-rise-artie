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

## The same watch in heart-script's own words (`watch-dsl.kts`, `runs/watch-dsl-1023-dsl2.out`)

Since then heart-script has `watch`, `whenever` and `once` (`client/heart-script/.../Watching.kt`). `watch-dsl.kts`
is the same goal written with them, with no loop, maps or clock of its own.

- **The first run was wrong, and only a note said so.** It checked Until from the pull request list, which doesn't
  offer `github:issues-get`. Each check was refused and noted, nobody saw the notes, and the watch kept going after
  its issue was closed. It was stopped by hand. Even so, before it was stopped, it merged #694, gave #695 (a draft)
  and #697 (a failed check) their reasons, left #696 (held) alone, and said nothing again on the old pull requests.
- **Two fixes followed.** `artie run` now prints each note as it is made. The watch now stands at the entry, where
  both the list and the watch's issue are reached.
- **The second run merged #699 once its check passed, and stopped at the next look after #698 was closed.** It
  finished in 165 steps.
- **A look reads every open pull request, about 30 here, one at a time,** so it takes 30 to 40 seconds.

## The goal file itself, run by ARTIE (`runs/goal-1023-g1.out` to `g3.out`)

Since slice 4a of spec/PLAN-WATCH-GOALS.md, `artie run dependabot-sim.goal.md --reasoner claude` runs the goal
itself. Each step is decided once by Claude, and replayed after that.

- **g1** stopped while deciding the Whenever's list. The planner sent an act with no words, and the refusal stopped
  the run. Fixed: a refusal while deciding is told to the planner, which decides again.
- **g2** stopped because the repository links no list of pull requests (the list is a search form at the start),
  and going back was refused. Fixed: while the list is decided, the planner may go back to the start, and the watch
  stands there.
- **g3** watched, but the list was the wrong one. The reasoner chose the issue search with `labels=dependabot`,
  which the gateway serves with pull requests left out, so every look found nothing. It was stopped by hand. The
  gateway's issue search doesn't say that it leaves pull requests out. GitHub can't list pull requests by label, so
  the right decision is the pull request list, with the label as the match check.

Each of the next live runs found something, and each was fixed with a test first:

- **g4.** Claude chose the open pull requests as the list, using the new label search. It stopped at the
  `If … hold` step, which was a plain step, and it was shown no steps inside the Whenever. **Fixed:** If became a
  word, with a check decided once, where "leave it" leaves the pull request. The step tree now looks inside a
  Whenever. A planner that stops while a watch decides now ends the run, where before the watch looked again
  forever.
- **g5.** The match and the If check held for every pull request. Claude couldn't see the labels, which are
  embedded resources rather than members of the state. **Fixed:** the planner is shown `<embedded>`, and a check
  may name an embedded relation.
- **g6.** The match was right, but the planner's tool turned the list `["dependabot"]` into text. The Until was
  decided as what held then ("open"), and Done when couldn't be checked. **Fixed:** lists stay lists, the Until is
  told it is the check that ends the watch, and a Done when fact may be left unchecked, with a note.
- **g7.** It merged #700, and left #696 and #689 (held) alone with a note each. It stopped at the watch's closing,
  when Done when checked `merged: true` on the start. **Fixed:** the planner is told what a Done when fact is.
- **g8 (`runs/goal-1023-g8.out`)** is the goal file run end to end, with exit 0:
  - It merged #708 once its check passed, and left #696 and #689 (held) alone with a note each.
  - It stopped at the next look after its issue was closed.
  - It noted both Done when facts as unchecked, saying why: no resource at the start holds them.
  - It made 19 model calls in all over 6 looks and about 30 open pull requests. They were all decisions; no event
    called the model.

- **g9** was told to narrow its list by a filter, and chose the issue search with `labels`, the old trap. It was
  stopped by hand. **Fixed:** the issue search's description in the profile now says that pull requests are left
  out, and that `github:pulls-list` lists them, by labels too.
- **g10 (`runs/goal-1023-g10.out`)** ran with exit 0:
  - It watched `github:pulls-list` with `labels=dependabot`.
  - It merged #710, and left #696 and #689 alone.
  - It finished in **54 steps, where g8 took 474**.
  - It made 19 model calls.
  - With `--remember`, it kept what it did in `runs/watch.memory.yaml`, so a watch run again doesn't merge, leave
    or say any of it again.

- **g11 (`runs/goal-1024-g11.out`)** merged #714, then stopped at the first give-up (#697). The planner's Otherwise
  was a read, a check and a comment, but only its first act was kept, so the planner was asked again and again until
  the step's limit. **Fixed:** a step under a Whenever is decided as a chain of acts, kept in order until the
  planner says done. A check in the chain leaves the rest undone where it holds.

## Two Whenevers at once: Dependabot, and a security scan (`runs/goal-1024-g12.out` to `g18.out`)

`dependabot-scan.goal.md` puts a second Whenever beside the Dependabot one: each issue a security scan opens,
labelled `scan`, is answered once. `finding.kts` opens such an issue, as a veracode-bot would. Whenevers side by side
are one watch, each a listener on a thread of its own with a client of its own (spec/PLAN-LISTENERS.md, slice 1).
Each one's first look is made in turn, in the goal's order, and every look after replays.

- **g12** stopped before its first look. The planner reached each list from the start, and Whenevers side by side
  were refused that. **Fixed:** each listener looks from where its own list is reached.
- **g13** commented on scan issue #718 at every look. Its own comment changed the issue, and the next look took that
  as a change. It was stopped by hand after two. **Fixed:** once a Whenever's steps are done at a member, the member
  is read again and remembered as it is, so only someone else's change is answered.
- **g14** merged #723, answered each scan issue once, and left the held pull requests alone. It stopped at the first
  give-up, because a pull request offered no comment through the gateway. **Fixed:** a pull request offers
  `github:comment`, open or closed, posted on the issue it also is.
- **g15** decided the If "labelled hold" as a check that held on every pull request, and left them all. The capture
  didn't say which check it was. **Fixed:** each check a watch decides is kept in the capture with where it was
  decided, and the planner is told an If's check holds exactly when its condition is true.
- **g16** decided the If right and merged #726. The Otherwise found no comment, because the restarted gateway
  couldn't bind its port and the old one kept answering.
- **g17** commented once on each of eight stuck pull requests, and no more after. But the reason was made up from
  `mergeable_state`, and each comment said it would be merged once it could be. **Fixed:** an Otherwise is told its
  wait gave up, and the server's own reason, to say that reason in the server's words and promise nothing the watch
  won't do.
- **g18 (`runs/goal-1024-g18.out`)** commented once on each of nine pull requests, each in the gateway's own words:
  a draft (#728, #715, #695, #682), conflicts with its base (#685, #683), or branch protection (#716, #697, #688).
  The Otherwise was decided once, at the first, and replayed for the rest with no model.

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
