---
start: "{github}"
inputs:
  pull:      { question: "Which pull request?" }
  owner:     { default: acme }
  repo:      { default: web }
  github:    { default: "http://192.168.1.18:8098/" }
  watcher:   { default: "http://192.168.1.18:8099/" }
  jenkins:   { default: "http://192.168.1.18:8170/" }
  dev:       { default: "http://192.168.1.18:8190/" }
  book:      { default: "The Hobbit" }
  author:    { default: "J. R. R. Tolkien" }
  renewable: { default: "Matilda" }
  problem:   { default: "missing-pages" }
  comment:   { default: "Pages 12 to 14." }
---

Release a pull request to Dev once it can be merged and its build passes its smoke test.

- Open pull request {pull} of {owner}/{repo} `as pr`
- At {watcher}, ask to be told when {pr} offers github:merge `as watch`
- Once {watch} is answered, follow its in-reply-to link to the watch, then the watch's about link to the pull request, and merge it by squashing `as merged`
- At {jenkins}, build commit {merged.merge_commit_sha}, asking to be told how it goes `as build`
- Once {build} is answered, follow its in-reply-to link to the build `as built`
  - Run the smoke script it links as ci:smoke-test, on the deployment it links as ci:deployment, with book = {book}, author = {author}, renewable = {renewable}, problem = {problem}, comment = {comment} `as smoke`
- If {smoke.passed}, at {dev}, open Dev and promote commit {merged.merge_commit_sha}, citing {smoke.passing} of {smoke.of} scenarios passed `as promotion`
  Otherwise, at {pr}, act github:comment with body = "Not promoted to Dev: smoke scenarios {smoke.failed} failed."
