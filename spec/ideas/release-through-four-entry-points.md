# Idea: a release through four entry points on one gateway

**Status:** plan only, raised by the author on 2026-10-31. Nothing here is built yet. It builds on
spec/ideas/orchestration-across-systems.md (the workspace bookmark, links and values across systems) and
spec/ideas/jenkins-gateway.md (the Jenkins slice and its phases). It replaces Vault from the earlier idea with AWS
and a test-run log of our own.

## The goal

Every remote looks the same to a client: GitHub, Jenkins, AWS, and a small domain of our own. They share one
media type, one way to search, one way to act, one way to say why not, and one way to wait. A client that can
use one of them can use all of them, and nothing about any vendor leaks into the script but its words.

## The scenario

1. **GitHub.** A plain-text goal, as today: a pull request labelled "release" is merged.
2. **Jenkins.** The build job for that commit is started and watched until it finishes. Its last stage deploys a
   new Lambda version and points the `staging` alias at it.
3. **AWS.** The gateway watches the function until the update has finished: `LastUpdateStatus` is `Successful`,
   and the alias names the new version.
4. **Tests.** A small battery of end-to-end checks runs against the staging URL, for example status, version
   header, one read, one write and one refusal.
5. **Test-run log.** Each result is recorded as an event in a small HEART domain of our own. When every check has
   passed, the run offers **promote**, with reasons whenever it doesn't.
6. **Promote.** Promoting finds the Jenkins promote job by search, not by a configured address, and starts it with
   the tested version. The run links to that build, so the trail can be followed from either end.

## One gateway, four entry points

One process, on one port. Each system is mounted under its own prefix, with its own home resource:

| Entry point | What it is | Its home offers |
|---|---|---|
| `/github/` | the GitHub connector, as today | search pulls, issues, repositories |
| `/jenkins/` | the Jenkins connector | search jobs and builds; look up a job by name |
| `/aws/` | the AWS connector, Lambda first | search functions; look up a function, its versions and aliases |
| `/testruns/` | a native HEART domain, event-sourced | search runs; open a new run (a domain act, not a create) |
| `/` | the workspace | one link to each home, by relation |

A script can start at any of the four homes, or at the workspace. That shows that the entry points are just
bookmarks, and that the systems are links in a representation, not configuration in the script. Moving Jenkins
onto its own gateway later changes one link in the workspace and nothing in the script.

**What changes in the gateway.** This is the one piece of core work. Today `GatewayConfig.kt` reads exactly one
`UPSTREAM_URL`, `GATEWAY_CONNECTOR` and `UPSTREAM_AUTH`, and `Main.kt` builds one `Gateway`.

- **Connectors:** read a list of connectors, each with its own prefix, connector folder, upstream, auth and profile:
  `GATEWAY_CONNECTORS=github,jenkins,aws`, and settings per connector such as `JENKINS_UPSTREAM_URL`. The native
  domain is mounted beside them, in the same process.
- **Indexes and workspace:** each prefix serves its own `ServiceIndex`, and `/` serves the workspace.
- **Credentials:** each connector keeps its own credential. The client never holds any upstream secret; it signs in
  once, to the gateway.

## The four, made the same

| | GitHub | Jenkins | AWS (Lambda) | Test runs |
|---|---|---|---|---|
| Upstream style | REST, OpenAPI published | REST-ish JSON, no OpenAPI | restJson1, Smithy model, no OpenAPI | native |
| Description we write | `connector.yaml` + resources | small OpenAPI slice (per jenkins-gateway.md) + resources | small OpenAPI slice for 4 operations + resources | Kotlin domain |
| Upstream auth | bearer | basic with API token | SigV4, a new `UPSTREAM_AUTH` mode, from the AWS credential chain | none |
| Main acts | merge, update branch, comment | start build, cancel | none in phase one (deploys go through Jenkins) | record result, finish, promote |
| Reasons for "not now" | behind, draft, conflicts | job disabled, already running, no permission | update in progress, failed | checks still open, a check failed, already promoted, superseded by a newer deploy |
| Waiting | webhook into the hub, or recheck | recheck the queue item, then the build | recheck `GetFunction`; push through EventBridge later | native: the store knows when it changes |

Every one of them answers with the same shapes:
- the representation, ETag and forms;
- `hr:unavailable` with a reason;
- 412 when stale, 428 without `If-Match`, 409 when not offered, 422 for bad input;
- `Prefer: wait=N` for waiting.

That is the normalization. The HEART TCK runs against each prefix, and passing it is what "normalized" means.

## Waiting on AWS: polling now, push later

AWS has no server-sent events for this. The two ways it offers are:

- **Polling.** `GetFunction` returns `Configuration.LastUpdateStatus` (`InProgress`, `Successful`, `Failed`). The
  gateway serves the function with `Prefer: wait=N` and rechecks upstream on a timer, the way the hub already does
  without a webhook. This works on day one.
- **Push.** An EventBridge rule on Lambda's update events, with an API destination that POSTs to the gateway's hub.
  It works the same as GitHub's signed webhook today, and wakes waiting clients at once. This comes second; it needs
  a public address or a tunnel to the gateway.

Server-sent events could still appear on the client side: the gateway could offer a stream of "this resource
changed" to watchers, whatever the upstream does. That would be a new HEART feature, for every entry point at once,
and worth deciding on its own.

## The test-run log

A native domain on `heart-eventsourcing`, like basketball.

- **Events:** `RunOpened{version, alias, url}`, `CheckRecorded{name, passed, detail}`, `RunFinished`,
  `PromotionRequested`, and `PromotionStarted{build}`.
- **Resource `/testruns/{id}`:** the version, the checks so far, and the state (`open`, `passed`, `failed`,
  `promoting`, `promoted`).
- **Acts and their reasons:**
  - `record` while open.
  - `finish` once every check is in.
  - `promote` only when passed. Otherwise the reason is one of: "2 of 5 checks failed", "already promoted", or
    "version 14 is no longer on staging". The last one is read from the AWS entry point when the act is offered.

**Promote starts Jenkins.** The promote act records `PromotionRequested` and answers at once. A small reactor in the
domain is itself a HEART client:

1. It searches the Jenkins entry point for the promote job by name.
2. It starts it with the version, and follows the new queue item to its build.
3. It records `PromotionStarted` with that build's address, which becomes a link on the run.

The reactor works from the event log, so a retry after a crash finds the request already handled and doesn't start
a second build.

**The other choice:** the client starts the Jenkins job itself after promoting. That's simpler, but then the domain
can't promise the promotion happens. The reactor keeps the promise in one place.

## Where it runs

- **Jenkins:** the scratch Jenkins in Docker from `exhibits/jenkins-scratch/`, with three jobs: build-and-deploy,
  promote and a failing one.
- **AWS:** a choice for the author. Both keep secrets out of every model.
  - **A small real sandbox account:** one Lambda with a function URL and two aliases. It costs cents, and is fully
    real, including EventBridge.
  - **LocalStack:** free and local. Lambda, aliases and function URLs work, but EventBridge API destinations are
    partial.
- **GitHub:** the scratch repository, as now.
- **Tests:** a kept script, so no model runs them.

## In steps, each small, with the author picking the next

1. **Mounts.** The gateway serves GitHub under `/github/`, with the workspace at `/`. All suites stay green, and the
   GitHub goals run unchanged from the workspace.
2. **Jenkins, read only.** Phase one of jenkins-gateway.md, mounted at `/jenkins/`. The TCK passes on it.
3. **Jenkins, start and wait.** A plain-text goal: "Build the commit of the merged release PR and wait for it."
4. **AWS, read and wait.** SigV4, functions, versions and aliases, and waiting by recheck. The TCK passes on it.
5. **Test runs.** The domain, its events, and its acts with reasons, features first.
6. **Tests and recording.** The battery as a kept script, recording into a run.
7. **Promote.** The reactor, search into Jenkins, and the link back.
8. **The whole goal**, in plain text, starting at the workspace. Reasoned once, kept as a script, replayed with
   pins. Measured the way the allowed-now runs were: turns, tokens, seconds, and what each held back.
9. **Push from AWS.** EventBridge into the hub. Optional, and only if the author wants it.

## Open questions for the author

- A real AWS sandbox account, or LocalStack?
- Which deploy target: Lambda (smallest), or ECS? ECS is a JSON-RPC API (`X-Amz-Target`) and makes a harder, more
  convincing normalization, but it costs more to run.
- Does promote belong to the test-run domain, through the reactor, or to the client?
- Is server-sent events from the gateway to watchers wanted now, or later?
