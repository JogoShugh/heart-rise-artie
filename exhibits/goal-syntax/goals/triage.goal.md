---
start: http://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { default: heart-github-test, question: "Which repository?" }
  label: { default: bug, question: "Which label marks a bug?" }
  stamp: { default: { now: MMdd-HHmmss } }
manners: alongside-others
---

Triage and fix the blank-bed-name crash, start to finish. The crash gets an issue, labelled and assigned, with a
sub-issue for its test; a second report of the same crash is closed as a duplicate of it. The fix and its test go
on a branch of their own, into one pull request, which is merged. Then every issue is closed the right way, and
the crash's conversation is locked.

## Working alongside others

Other people work in this repository while you do. What they decide stands.

- Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now.
- When a step no longer makes sense because of what someone did, leave it, and say why in the run's notes.
- Nothing you write may claim something that isn't so: "fixed" only once the fix is merged.

## The work

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name ({stamp})", saying a bed with a blank name crashes the garden `as issue`
    - Label it {label}
    - Assign it to {owner}
  - Open an issue titled "Test blank bed names ({stamp})", saying it is part of #{issue.number} `as test`
  - Open an issue titled "Garden crashes on an empty bed name ({stamp})", saying it was seen on the demo garden `as dupe`
  - From the main branch, start a new branch named fix-blank-names-{stamp}
    - Commit a file named fix-{stamp}.md saying "Bed names may not be blank." with the message "Refuse a bed with no name"
    - Commit a file named test-{stamp}.md saying "A bed with a blank name is refused." with the message "Test that a blank bed name is refused"
    - Open a pull request into main titled "Refuse a bed with no name ({stamp})", saying it fixes #{issue.number} `as pr`
      - Add to its description that it is tested by #{test.number}
      - Once it can be merged, merge it by squashing, titled "Refuse a bed with no name (#{pr.number})".
        Otherwise, say why it wasn't merged.
        Or give up after 10 minutes.
- On {issue}
  - Add the test issue as a sub-issue
  - Say it's fixed in #{pr.number} and tested in #{test.number}.
    Otherwise, say it waits on #{pr.number}.
  - Lock its conversation as resolved
- On {test}
  - Say the test went in with #{pr.number}, and close it as completed
- On {dupe}
  - Close it as a duplicate of #{issue.number}, and say it's the same crash

## Done when

- The crash issue is closed and the pull request is merged; or the pull request was held back, and the crash issue says it waits on it
- Nothing anyone else decided was undone
