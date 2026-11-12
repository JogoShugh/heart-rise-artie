---
start: "{deployed}"
inputs:
  deployed: { question: "Which deployment of the library?" }
  book: { default: "Matilda" }
  author: { default: "Roald Dahl" }
  renewable: { default: "Matilda" }
  problem: { default: "missing-pages" }
  comment: { default: "Pages 12 to 14." }
---

Check that the library works as a member would use it. Each scenario starts at the library's entry.

- Search the loans for "{book}", and open {book} `as found` `scenario S1`
- Search the loans for "{book}", and open {book} `as loan` `scenario S2`
  - Verify: author is {author}
- Search the loans for "{renewable}", and open {renewable} `as before` `scenario S3`
  - Renew it
  - Verify: due is not {before.due}
- Search the loans for "{renewable}", and open {renewable} `as read` `scenario S4`
  - Renew it
  - Renew it again as of {read}: it should be refused as stale
- Search the loans for "{book}", and open {book} `as reported` `scenario S5`
  - Report {problem} on it, saying "{comment}"
  - Verify: reported.problem is {problem}
