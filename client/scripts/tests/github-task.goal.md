---
start: http://localhost:8392/
inputs:
  owner:
    question: Whose repository?
  repo:
    default: heart-github-test
    question: Which repository?
  stamp:
    default: { now: MMdd-HHmmss }
---

Fix the blank-bed-name crash, start to finish: an issue, a fix on its own branch, a pull request merged, and the
issue closed.

- In {owner}/{repo}
  - Open an issue titled "Crash when a bed has no name (steps)", saying it was opened by a HEART script `as issue`
  - From the main branch, start a new branch named fix-blank-names-steps-{stamp}
    - Commit a file named fix-steps-{stamp}.md saying "Bed names may not be blank." with the message "Refuse a bed with no name"
    - Open a pull request into main titled "Refuse a bed with no name (steps)", saying it relates to #{issue.number} `as pr`
      - Wait: the pull request can be merged
      - Merge it by squashing its commits
      - Verify: it merged
- On {issue}
  - Comment "Fixed in 1.2 by #{pr.number}."
  - Close it the right way: the fix shipped in 1.2 `reasoned`
  - Verify:
    - it is closed
    - its reason is completed
