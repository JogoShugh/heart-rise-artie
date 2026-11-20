---
start: http://localhost:8392/
inputs:
  owner:     { question: "Whose repository?" }
  repo:      { question: "Which repository?" }
  version:   { question: "Which release ships the fixes?" }
  label:     { default: ready-to-close, question: "Which label marks an issue ready to close?" }
  shipped:   { default: shipped, question: "Which label means its fix shipped?" }
  wontfix:   { default: wontfix, question: "Which label means it won't be done?" }
  duplicate: { default: duplicate, question: "Which label means it repeats another issue?" }
---

Close each issue that is ready, the way its labels say it ended. Keep watching until the watch's issue is closed.

- In {owner}/{repo}
  - Open an issue titled "Close-out watch", saying "Close this issue to stop the watch." `as watch`
  - Whenever an issue labelled {label} changes `as issue`
    - If it's closed, leave it.
    - If it's labelled {shipped}, comment "Fixed in {version}."
    - If it's labelled {shipped}, close it as completed.
    - If it's labelled {wontfix}, close it as not planned.
    - If it's labelled {duplicate}, close it as a duplicate. The label is enough: name no other issue.
- Until someone closes {watch}
