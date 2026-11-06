---
start: https://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { default: heart-github-test, question: "Which repository?" }
  label: { default: dependabot, question: "Which label marks a pull request to merge?" }
  hold:  { default: hold, question: "Which label means a person objects?" }
manners: alongside-others
---

Merge the pull requests labelled {label} once they can be merged, unless a person labels them {hold}. Keep
watching until the watch's own issue is closed.

## Working alongside others

Other people work in this repository while you do. What they decide stands.

- Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now.
- When a step no longer makes sense because of what someone did, leave it, and say why.
- Nothing you write may claim something that isn't so: "merged" only once it is.
- Don't say again what has already been said.

## The work

- In {owner}/{repo}
  - Open an issue titled "Auto-merge watch", saying "Close this issue to stop the watch." `as watch`
  - Whenever a pull request labelled {label} changes `as pr`
    - If it's labelled {hold}, leave it, and say so once.
    - Once it can be merged, merge it by squashing.
      Or give up after 10 minutes.
      Otherwise, say on {pr} why it wasn't merged.
- Until someone closes {watch}

## Done when

- Every pull request labelled {label} during the watch is merged, or says why not
- Nothing anyone else decided was undone: no pull request labelled {hold} was merged
