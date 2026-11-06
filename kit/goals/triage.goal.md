---
start: http://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { question: "Which repository?" }
  inbox: { default: needs-triage, question: "Which label marks an issue waiting for triage?" }
  done:  { default: triaged, question: "Which label marks an issue as triaged?" }
---

Triage each issue waiting for it: one label for what it is, chosen from its own words, then marked triaged. Keep
watching until the watch's issue is closed.

- In {owner}/{repo}
  - Open an issue titled "Triage watch", saying "Close this issue to stop the triage watch." `as watch`
  - Whenever an issue labelled {inbox} changes `as issue`
    - If it's labelled {done}, leave it.
    - Label it bug, question or enhancement, whichever its words say it is. `reasoned`
    - Label it {done}.
- Until someone closes {watch}
