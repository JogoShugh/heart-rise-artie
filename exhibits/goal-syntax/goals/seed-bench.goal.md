---
start: https://localhost:8392/
inputs:
  owner:  { question: "Whose repository?" }
  repo:   { default: heart-github-test, question: "Which repository?" }
  week:   { default: "43", question: "Which sowing week?" }
  grower: { question: "Who is on duty at the bench?" }
  hold:   { default: hold, question: "Which label means a person objects?" }
manners: alongside-others
---

Keep the seed bench moving for week {week}: sow each plan once it passes the bench check, answer
every alert once and hand it to the grower on duty, and close each tray once it is transplanted.
Keep watching until the bench watch's own issue is closed.

## Working alongside others

Growers move trays, relabel them and write on them by hand. What they decide stands.

- Don't undo or write over anything someone else changed since you looked.
- Leave anything labelled {hold} alone, and say so once.
- Nothing you write may claim something that isn't so: "sown" only once the plan is merged.
- Don't say again what has already been said.

## The work

- In {owner}/{repo}
  - Open an issue titled "Bench watch, week {week}", saying "Close this issue to stop the watch." `as watch`
  - Whenever an issue labelled alert changes `as alert`
    - If it's labelled {hold}, leave it, and say so once.
    - Say on {alert} "Seen by the bench watch; {grower} will check the tray today.", unless it's said already.
    - Assign it to {grower}.
  - Whenever a pull request labelled sowing changes `as plan`
    - If it's labelled {hold}, leave it, and say so once.
    - Once it can be merged, merge it by squashing.
      Or give up after 5 minutes.
      Otherwise, say on {plan} why it wasn't merged, unless it's said already.
  - Whenever an issue labelled tray changes `as tray`
    - If it's labelled transplanted, close it.
- Until someone closes {watch}

## Done when

- Every alert during the watch says it was seen, once, and is assigned to {grower}
- Every sowing plan is merged, or says why not
- Nothing labelled {hold} was changed
