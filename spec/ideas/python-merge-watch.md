# Idea: a native Python merge watch, beside the bare one

**Status:** idea only. Raised by the author on 2026-10-27. Nothing is built.

## The gap

The bare merge watch (`exhibits/merge-watch/merge.bare.kts`, spec/ideas/bare-runs.md, "Rails") calls GitHub at
fixed addresses, the way hand-written Python would. However, there is no Python merge watch to compare it with:
- The native trial's Python (`exhibits/native-vs-heart/runs/native-script*.py`) does a different task. It is a
  straight line: issues, a branch, a commit and one pull request, merged after polling `mergeable` every 2 s.
- It watches nothing. It has no loop over labelled pull requests, no hold label, no "say so once", and no give-up
  with a comment.
- On any surprise it stops the whole run (`sys.exit`).

So "the bare script fails the way the Python does" is a guess, not a finding. The one measured comparison is on the
triage task: artie-bare 21.0 s, py-direct 21.9 s, medians over three rounds, a tie.

## The idea

Make the merge watch's own native arm. It is made the way the native trial made its Python: a Claude session, given
`merge.goal.md` and a GitHub token and no HEART, writes a script from its own run of the goal. It is a subject of
the experiment, not tooling, and it is kept as it was written. Whether that falls under the rule of no new Python
is the author's call (see Open).

Then run three arms on the scratch repository, each on the same seeded set of labelled pull requests:

| Arm | What runs |
|---|---|
| `py-watch` | the native Python merge watch |
| `artie-bare` | `merge.bare.kts`: the goal's words over fixed addresses, GitHub's refusal as the only gate |
| `artie-goal` or `merge.kts` | the kept script over the gateway: forms, reasons for absence, validators |

## What to look at

Not speed first: a watch spends most of its time waiting. Look at what each arm does when the repository is not
tidy:

| Case | Expected for bare (from the live run) | Python: to find out |
|---|---|---|
| A bump that becomes mergeable later | refused, then merged at the next look | polls `mergeable`? stops? |
| A draft or a conflicted bump | commented on after 3 minutes, with GitHub's reason | skipped silently? stops? |
| A held bump | left, said once | said every look? |
| A labelled plain issue, not a pull request | that look ends at its member read | stops the watch? |
| A teammate closes, comments or relabels mid-watch (the meddler) | not yet run | not yet run |
| The watch's issue closed | the watch ends at the next look | ? |

Also: how many calls each makes per look, and what each says on the scratch repository.

## Fair setup

- The same seeded pull requests for each arm, made by `bump.kts`, with the same drafts, conflicts and holds.
- Interleave the arms, as the ladder does, and give each arm a fresh watch issue.
- Kept untouched: no fixes to the Python after it is written, as in the native trial.

## Open

- **Who writes the Python, and does it count as new Python?** The native trial's scripts were written by a separate
  Claude run, as the thing being compared. The author decides whether that is allowed here.
- The meddler for a watch: which acts a teammate makes, and when.
- Whether the three arms belong in the "Wayfinding to Rails" piece, or in a report of their own under
  `docs/reports/`.
