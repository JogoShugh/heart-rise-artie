---
start: "{deployed}"
inputs:
  deployed: { question: "Which deployment of the library?" }
---

Check that the library works as a member would use it. Each scenario starts at the library's entry.

- Find a loan: search the loans for "Matilda"; it should list "Matilda". `S1`
- Read it: open that loan; it should say who wrote it and when it is due, and offer renewing. `S2`
- Renew: renew it; its due date should move three weeks later. `S3`
- A refusal is explained: renew it again with the version read before the renewal; it should be refused as stale,
  and the refusal should link the loan to read again. `S4`
- Report a problem: report "missing pages" on the loan, choosing from the kinds the library lists, with a short
  comment; the loan should show the problem reported. `S5`
