---
start: http://localhost:8140/
inputs:
  company:
limits: { moves: 200, step: 120 }
---

Renew every book the members of a company group have out that can be renewed, and say why for any that can't.

- Find the members of the company group {company}
  - For each member, on every page of the results: open the member, and renew each book they have out if renewing is offered; otherwise say why it can't be renewed
