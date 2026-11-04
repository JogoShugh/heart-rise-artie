---
start: http://localhost:8140/
inputs:
  card:
limits: { moves: 60, step: 30 }
---

Renew each of Ana's books that can be renewed, and say why for any that can't.

- Look up Ana's account by her library card number {card}
  - For each book she has out: renew it if renewing is offered; otherwise say why it can't be renewed
