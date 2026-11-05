---
start: http://localhost:8140/
inputs:
  member:
limits: { moves: 60, step: 30 }
---

Renew each book a member has out that can be renewed, and say why for any that can't.

- Find the account of the member named {member}
  - For each book they have out: renew it if renewing is offered; otherwise say why it can't be renewed
