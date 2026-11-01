# Plan: three simulated services behind the shared conformance features

Status: agreed 2026-11-01. The build and deploy services are event-sourced, with CQRS. Docker packaging of the simulator comes later.

## Why

The shared client conformance features (`implementations/conformance/features/`,
175 scenarios counting example rows) are the one set of features every HEART
client is held to. Each client brings only its own step code: heart-ts has
`features/support/steps.mjs`, ARTIE has `client/artie/src/test/kotlin/conformance/`.
No feature is copied per client.

Today 150 of the 175 run against "a small service" that heart-ts's steps build
per scenario out of hand-written JSON. It has no domain, it only exists in
JavaScript, and ARTIE would have to copy it. It is replaced by three narrow,
simulated HEART services, written once in Kotlin:

- **GitHub**: pull requests, issues, comments, users. The existing
  `GitHubGateway` over `GitHubUpstream` (heart-gateway test fixtures), as is.
- **Build**: a narrow Jenkins. Jobs, their parameters, builds, the queue.
- **Deploy**: a narrow cloud deploy service. Apps, environments, regions,
  releases, deployments, scaling.

The 25 scenarios that use the recorded garden keep it: it is the reference
service, and its recordings are real.

## What the scenarios need

From the inventory of the 150 small-service scenarios:

| Kind | Scenarios | Served by |
|---|---|---|
| Normal domain behaviour: links, acts, templates, choices, layering, embedded resources | 55 | the services as they are |
| Client side only: any service will do | 10 | the build service |
| A misbehaving server: odd refusal bodies, no profile, 503 reads, no validator, a set with no integrity, malformed links or embedded resources | 28 | the fault layer |
| Injected schemas and state: `$ref` to nowhere, unknown keywords, pointer edge cases, odd `hr:options` | 29 | the build service's job parameters, and the fault layer |
| HAL-FORMS shapes heart-core never renders: nameless properties, `options: "text"`, a HEAD template | 28 | the build service in HAL-FORMS, and the fault layer |

A correct HEART server never sends the 85 odd answers, yet a client must
handle them. So each service runs behind a fault layer a step can set.

## Design

### Module

`heart/heart-sims`, in the heart build. The services are test doubles, as
`GitHubUpstream` is, so they live in its `src/testFixtures`; the conformance
features are their tests. A small `main` and a jar task let a process serve
them on a port.

- ARTIE's steps call the handler in memory, through `InProcessTransport`.
- heart-ts's steps start the jar once per run on a free port, and reset it
  before each scenario. They set `JAVA_HOME` themselves (the default JDK 25
  breaks Gradle).
- Later: a Docker image of the same jar.

### Addresses

One handler serves all three, by host:

| Service | Base | Notes |
|---|---|---|
| GitHub | `http://github.test/` | the gateway, forwarding each caller's credential |
| Build | `http://build.test/` | native heart-core |
| Deploy | `http://deploy.test/` | native heart-core |
| Another origin | `http://elsewhere.test/` | answers any act, for the credential scenario |

Over a port, heart-ts sends the service's host in a header the simulator reads
(`X-Sim-Host`), so the URLs in the features stay the same in both clients.

### Event-sourced, with commands and queries apart

The build and deploy services are event-sourced, with CQRS, laid out as the
tickets domain is (`domains/tickets`):

- **Write side.** Each act is a command. A pure `decide(state, command)` returns
  the events, or a refusal; the events are appended to heart-eventsourcing's
  `InMemoryEventStore` at the next sequence number. A resource's state is the
  fold of its events (`EventSourcing.apply`), and its version is the number of
  the last one, so a write that lost a race is `ConcurrentWriteException`, which
  heart-core answers 412. Nothing on the write side is read by a query.
- **Read side.** Projections, fed by the same events, hold what queries need:
  the job directory and the queue (build), the app directory, each app's
  releases and deployments (deploy). The bookmark's finding, search pages and
  the plain lists (`/labels`, `/apps/{name}/releases`) read only these.
- **History.** Each resource's `/history` is its event stream (heart-core's
  `past`).
- **Seed and setup.** A reset forgets every stream and appends the seed's
  events. The control routes that set up a job or an app append events too
  (`JobConfigured`, `AppImported`); no state is written directly.

Events, by service:

| Build | Deploy |
|---|---|
| `JobConfigured(name, parameters, labels)`, `JobDisabled`, `JobEnabled`, `JobDeleted`, `BuildQueued(id, parameters)`, `BuildStarted(number)`, `BuildFinished(number, result)`, `BuildStopped(number)`, `QueueItemCancelled(id)` | `AppImported(name, environment, region, replicas, release, regions, quota)`, `ReleasePublished(version, status)`, `DeploymentStarted(id, release, environment, region)`, `DeploymentFinished(id)`, `DeploymentCancelled(id)`, `Scaled(replicas)`, `AppRemoved` |

The GitHub service stays the gateway over `GitHubUpstream`: it stands for an
API we do not own, so its shape is GitHub's, not ours.

### The build service

| Resource | State | Acts |
|---|---|---|
| `/` bookmark | | find a job by name |
| `/jobs/{name}` | `name`, `enabled`, `lastBuild {number, result}`, `labels` | `build` (POST; its schema is the job's parameters, as configured), `disable` / `enable` (PUT), `delete` (DELETE), `queue` (GET, templated) |
| `/jobs/{name}/builds/{number}` | `number`, `result`, `parameters` | `stop` (POST), `rebuild` (POST) |
| `/queue/{id}` | `id`, `job`, `why` | `cancel` (DELETE) |
| `/labels` | plain JSON list of agent labels | |

Seed: job `nightly` (parameters `{"type": "object"}`), job `release` with one
finished build. A job's parameters are any JSON Schema a step gives it: that
is what Jenkins users write, so injected schemas read naturally. The build
service also speaks HAL-FORMS, and is the home of Jenkins-like faults: 503
while starting, an HTML 500 body, no ETag, acts answered 201 with no body.

### The deploy service

| Resource | State | Acts, offered by status |
|---|---|---|
| `/` bookmark | | find an app by name |
| `/apps/{name}` | `name`, `environment`, `region`, `replicas`, `release`, `status`, `regions {env: [...]}` | idle: `deploy` (POST; `environment`, then `region` from `/regions/{environment}` of this state, `release` from the linked releases `where status = succeeded`), `scale` (PUT; `replicas` up to a quota, else 422 rule-refused), `remove` (DELETE). deploying: `cancel` |
| `/apps/{name}/releases{?status}` | plain JSON list, with an ETag, `no-cache` | |
| `/apps/{name}/deployments/{id}` | `id`, `release`, `status` | |

Seed: app `web`, idle, release `1.4.0`; releases `1.4.0` and `1.5.0` succeeded,
`1.6.0` failed. It serves L0, L1 and L2, with affordance sets that change as a
deployment starts and ends. It is the home of the natural refusals: 409 while
deploying, 412 when someone else deployed first, 428 with no validator, 422
over the quota; and of layering faults: a set with no integrity, no set,
integrity that does not match.

### The GitHub service

The gateway fixture as it is: pull requests (`comment` POST, `merge` PUT,
`request-review`), issues (`assign`, `lock`, `unlabel` DELETE), comments, and
the authenticated user. Seed: one repository, pull 482 by `ada`.

### Fault layer

A filter around each service's handler, set by steps, cleared by a reset. It
can make a service:

- answer an act with any status and body (`application/problem+json` unless the
  body is not JSON);
- answer every read with a status;
- leave out the profile link, or the ETag;
- send another media type, or another `Preference-Applied`;
- replace a representation's `_links`, `_embedded` or state members;
- replace a form's schema, target or method, or a HAL-FORMS template;
- link its affordance set without integrity, with wrong integrity, or not at all;
- answer acts with no body, or with a `Content-Location`;
- serve a plain resource at a path, with a status, validator and body.

### Control routes

On the same handler, under `/_sim/`, never on a service's own host:

| Route | Does |
|---|---|
| `POST /_sim/reset` | every service back to its seed, faults cleared, log emptied |
| `POST /_sim/{service}/faults` | sets one fault |
| `PUT /_sim/build/jobs/{name}` | creates or replaces a job, with its parameters |
| `PUT /_sim/deploy/apps/{name}` | replaces an app's state |
| `GET /_sim/log` | every request each service received, in order |

Both clients' steps use only these routes and their own client. Step code stays
thin in both languages, and assertions on what was sent read the same log.

## Feature wording

Every phrase names its service. The generic "that service ..." phrases take the
service as a word: `the {word} service ...`, so one step definition serves all
three. Setup phrases name the domain:

| Today | Becomes |
|---|---|
| a service offering "note" with the schema {} | the build service has a job "nightly" whose parameters are {} |
| it enters that service | it enters the job "nightly" / the app "web" / pull 482 |
| that service answers "note" with status 409 and the body {} | the deploy service answers "scale" with status 409 and the body {} |
| a HAL-FORMS service offering "note" with the properties [] | the build service has a job "nightly" whose HAL-FORMS parameters are [] |
| that service declares the schema {}, with the state {} | the build service's job "nightly" has the parameters {} and the state {} |
| a service whose game takes its players and its agents from state | the deploy service's app "web" (Background) |
| a service whose resource embeds its author ... | pull 482 on the GitHub service |
| the last request was for "/actions/note" | the last request was for "/jobs/nightly/actions/build" |

Each feature's final wording is written Gherkin-first, in its own commit, and
both clients' steps follow it.

## Scenarios by service

| Feature | Scenarios | Service |
|---|---|---|
| schema | 14 | build: job parameters are the schema; the service's own 422 |
| choices | 7 | deploy: region by environment from state; releases from the linked list, revalidated with 304; values outside refused |
| rungs, layering (:69-:162, :195) | 19 | deploy: L0/L1/L2, curies, look-alike tokens, shared and own caches, a set that changes at the same address after a deployment, DELETE `remove` |
| rungs, HAL-FORMS (:166-:188) | 4 | build in HAL-FORMS: HEAD `queue`, media type with a parameter, odd options, a never-templated target |
| hal-forms | 26 | build in HAL-FORMS: job parameters as properties; templates; inline options; options from `/labels` |
| service: refusals (:10, :247, :253) | 14 | deploy: `scale` answered with each status and body |
| service: reads (:30, :35, :226, :236) | 4 | build: no profile, 503 while starting, no ETag |
| service: links, follow (:50, :62, :330, :313, :318, :306) | 8 | GitHub: pull 482's links and embedded user; malformed ones through the fault layer |
| service: act shaping (:68, :76, :90, :97, :208, :214, :354, :361) | 10 | GitHub (`comment` POST, `merge` PUT, `unlabel` DELETE, credential not sent to `elsewhere.test`) and build (201 or 204 with no body, Content-Location of the queue item) |
| service: safe forms and templates (:103, :123, :323, :336) | 16 | build: `queue` GET form, pointers into the job's state |
| service: choices (:135-:188, :266-:298, :346) | 19 | build: job parameters with `hr:options` over the job's state, `/labels` as the other resource |
| service: layering faults (:198, :203, :230) | 3 | deploy |
| service: changed schema, client only (:41, :45, :219, :242) | 4 | build |
| client: integrity mismatch (:85), embedded author (:101) | 2 | deploy; GitHub |
| garden scenarios (client, rungs, hal-forms, dialects) | 25 | the recorded garden, unchanged |

## Phases

1. This plan. One commit.
2. `heart-sims`: build and deploy services (event-sourced, CQRS), GitHub wired in, fault layer,
   control routes, main and jar. Its first test is `schema.feature` rewritten.
3. One feature at a time: schema, choices, rungs, hal-forms, service (in three
   or four commits), then client's two hand-built services. For each:
   1. Rewrite the feature in domain wording.
   2. heart-ts steps onto the control routes; red, then green, through the wrapper.
   3. ARTIE steps; red, then ARTIE fixed in `src/main` until green.
   4. ARTIE's own scenarios the shared feature now covers are deleted; ARTIE
      keeps only what is its own (CoAP, Vault, scripts, its event log).
   5. Local commits.
4. heart-ts's hand-built service removed from `steps.mjs`; ARTIE's
   `conformanceTest` runs all seven features; the conformance README updated.

Done when both clients pass all 175 against the same features and the same
three services, `./gradlew :artie:check` runs all seven features, and ARTIE's
own features hold only ARTIE-specific behaviour.

## Risks

- heart-ts's suite needs Java and the simulator jar, and starts a few seconds
  slower.
- Gradle needs `JAVA_HOME` set (JDK 25 fails with "25.0.4.1"); the lint hook
  hits this today.
- About 20 scenarios test malformed representations. In any domain they read
  as "the service sends a broken answer"; that is honest, not natural.
- Rewriting the shared features changes what heart-ts is held to; its 175 stay
  green at every commit.
- The GitHub rows depend on what the gateway already serves; where it does not
  (an embedded user, a titled link), the fault layer supplies it.

## Later

- A Docker image of the simulator, for clients in other languages.
- One real-world story across the three (asked for 2026-11-01; start simple first):
  - a commit pushed to a repository on the GitHub service triggers a build of
    the job named after that repository;
  - a successful build feeds a deployment of the same name on the deploy service;
  - the deploy target is local: the build takes time, the files are copied, the
    service starts, as a real one would;
  - the deployed service answers an actuator `/info` with the full and the short
    commit SHA, and the committer's username and full name, all embedded at build
    time.
