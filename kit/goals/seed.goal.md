---
start: http://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { question: "Which repository?" }
---

Seed this repository with issues about its own code, for the kit's other goals to work on: three waiting for triage,
one of them also labelled for its documentation, and three ready to close, one of each kind.

- In {owner}/{repo}
  - Open an issue titled "artie needs Java 22, but client/README.md says 21", saying "Built and run as the README says, artie stops: it needs Java 22 or newer.", labelled needs-triage and documentation
  - Open an issue titled "How do I point artie at a gateway on another port?", saying "The goals start at http://localhost:8392/. Mine runs elsewhere.", labelled needs-triage
  - Open an issue titled "A gateway connector for Jira", saying "The gateway reads any OpenAPI description. A connector for Jira Cloud would let one goal work across GitHub and Jira.", labelled needs-triage
  - Open an issue titled "The kit's hello goal", saying "A first goal that opens, comments on and closes one issue.", labelled ready-to-close and shipped
  - Open an issue titled "Build ARTIE with Gradle 7", saying "The build needs Gradle 8.", labelled ready-to-close and wontfix
  - Open an issue titled "artie says it needs Java 22", saying "Same as the README issue about Java 22.", labelled ready-to-close and duplicate
