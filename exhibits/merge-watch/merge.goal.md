---
start: https://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { default: heart-github-test, question: "Which repository?" }
  label: { default: dependencies, question: "Which label marks a dependency bump?" }
  hold:  { default: hold, question: "Which label means a person objects?" }
  told:  { default: watch-told, question: "Which label marks a pull request the watch has told why?" }
---

Merge dependency bumps once they can be merged. Keep watching until the watch's issue is closed.

- In {owner}/{repo}
  - Open an issue titled "Merge watch", saying "Close this issue to stop the watch." `as watch`
  - Whenever a pull request labelled {label} changes `as pr`
    - If it's labelled {hold}, leave it, and say so once.
    - If it's labelled {told}, leave it.
    - Once it can be merged, merge it by squashing.
      Or give up after 3 minutes.
      Otherwise, label it {told}, then tell its author in one friendly sentence why it wasn't merged and what to do next. `reasoned`
- Until someone closes {watch}
