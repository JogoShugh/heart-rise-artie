---
start: http://localhost:8098/
inputs:
  owner:   { default: acme }
  repo:    { default: web }
  pull:    { question: "Which pull request?" }
  jenkins: { default: http://localhost:8170/ }
  dev:     { default: http://localhost:8190/environments/dev }
---

Release a pull request to Dev once it can be merged and its build passes its smoke test.

- In {owner}/{repo}, open pull request {pull} `as pr`
  - Ask to be told when it can be merged `as watch`
  - Once {watch} says it can be merged, merge it by squashing `as merged`
    Or give up after 30 minutes, and comment on {pr} why.
- At {jenkins}, open job app, and build commit {merged.merge_commit_sha}, asking to be told how it goes `as build`
- Once {build} has succeeded:
  - Follow its `ci:deployment` link `as preview`
  - Run the smoke script {build} links as `ci:smoke-test`, with deployed = {preview} `as smoke`
  Or, once {build} has failed, comment on {pr} with the stage that failed, linking the log {build} links as `ci:log`.
- At {dev}, if {smoke.passed}, promote commit {merged.merge_commit_sha}, citing {smoke} `as promotion`
  Otherwise, comment on {pr} with the scenarios that failed and why.
