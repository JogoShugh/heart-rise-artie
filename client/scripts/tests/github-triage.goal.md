---
start: http://localhost:8392/
inputs:
  owner:
    question: Whose repository?
  repo:
    default: heart-github-test
    question: Which repository?
  label:
    default: bug
    question: Which label marks a bug?
  stamp:
    default: { now: MMdd-HHmmss }
limits:
  moves: 90
  step: 12
---

Triage and fix the blank-bed-name crash, start to finish. The crash gets an issue, labelled and assigned, with a
sub-issue for its test; a second report of the same crash is closed as a duplicate of it. The fix and its test go
on a branch of their own, into one pull request, which is merged. Then every issue is closed the right way, and
the crash's conversation is locked.

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name ({stamp})", saying a bed with a blank name crashes the garden `as issue`
    - Label it {label}
    - Assign it to {owner}
    - Verify: it is open
  - Open an issue titled "Test blank bed names ({stamp})", saying it is part of #{issue.number} `as test`
  - Open an issue titled "Garden crashes on an empty bed name ({stamp})", saying it was seen on the demo garden `as dupe`
  - From the main branch, start a new branch named fix-blank-names-{stamp} `moves 8`
    - Commit a file named fix-{stamp}.md saying "Bed names may not be blank." with the message "Refuse a bed with no name"
    - Commit a file named test-{stamp}.md saying "A bed with a blank name is refused." with the message "Test that a blank bed name is refused"
    - Open a pull request into main titled "Refuse a bed with no name ({stamp})", saying it fixes #{issue.number} `as pr`
      - Change its description to say it fixes #{issue.number} and is tested by #{test.number}
      - Wait: the pull request can be merged
      - Merge it by squashing its commits, with the commit title "Refuse a bed with no name (#{pr.number})"
      - Verify: it merged
- On {issue}
  - Add the test issue as a sub-issue, by its id, {test.id}
  - Comment "Fixed in #{pr.number}, tested in #{test.number}."
  - Close it the right way: the fix is merged `reasoned`
  - Lock its conversation as resolved
  - Verify:
    - it is closed
    - its reason is completed
    - it is locked
- On {test}
  - Comment "The test went in with #{pr.number}."
  - Close it as completed
  - Verify: it is closed
- On {dupe}
  - Comment "Same crash as #{issue.number}."
  - Close it as a duplicate of #{issue.number}, whose id is {issue.id}
  - Verify:
    - it is closed
    - its reason is duplicate
