---
start: http://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { question: "Which repository?" }
  stamp: { default: { now: MMdd-HHmm } }
---

A first run: one issue opened, commented on and closed, so each act can be seen on GitHub.

- In {owner}/{repo}
  - Open an issue titled "Hello from ARTIE ({stamp})", saying "Opened by ARTIE, a generic client that reads what GitHub offers through a HEART gateway." `as issue`
- On {issue}
  - Comment "Each act here was one this issue offered at that moment."
  - Close it as completed
  - Verify:
    - it is closed
    - its reason is completed
