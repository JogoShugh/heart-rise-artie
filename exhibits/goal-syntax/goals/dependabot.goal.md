---
start: http://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { default: heart-github-test, question: "Which repository?" }
manners: alongside-others
---

Merge Dependabot's pull requests once their checks pass, unless a person objects. Keep watching until the watch
issue is closed.

## Working alongside others

Other people work in this repository while you do. What they decide stands.

- Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now.
- When a step no longer makes sense because of what someone did, leave it, and say why in the run's notes.
- Nothing you write may claim something that isn't so: "merged" only once it is.

## The work

- In {owner}/{repo}
  - Whenever a pull request from dependabot opens `as pr`
    - Once its checks pass, merge it by squashing.
      Otherwise, say which check failed.
    - Whenever someone requests changes, leave it, and say so in the run's notes.
- Until {owner} closes the issue "Auto-merge watch"

## Done when

- Every pull request from dependabot opened during the watch is merged, or its notes say why not
- Nothing anyone else decided was undone: no pull request with changes requested was merged
