---
start: http://localhost:8400/workspace.json
inputs:
  owner:   { question: "Whose repository?" }
  repo:    { question: "Which repository?" }
  version: { default: "1.1", question: "Which release ships?" }
---

Ship a release across three systems that know nothing of each other: a build queued on the build service, the
release deployed to staging on the deploy service, and the release recorded on GitHub.

- Follow the link to the release job, on the build service
  - Queue a build of it
- Follow the link to the web app, on the deploy service
  - Deploy release {version} to an available region of staging
- Follow the link to GitHub
  - In {owner}/{repo}
    - Open an issue titled "Release {version}", saying "Built on the build service and deployed to staging on the deploy service, by one goal across three systems."
