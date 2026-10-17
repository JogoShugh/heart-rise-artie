# Plan: GitHub pull requests as HEART, through the gateway

**Status:** draft, 2026-10-16. Part 2 of "Take HEART"
(docs/reports/take-heart). Builds on spec/PLAN-OPENAPI-GATEWAY.md.

## Goal

Put the OpenAPI-to-HEART gateway in front of GitHub's REST API, for pull
requests, so a person or an agent can ask "what's the status of my PRs?",
"why can't I merge #482?", "merge the one that's ready", and be offered only
what GitHub would accept now, with the reason for the rest. This is the first
time the gateway meets an API nobody here controls; what it needs to get there
is the real price of the approach.

## What GitHub gives us (checked 2026-10-16)

- **The description:** github/rest-api-description, MIT licence, OpenAPI
  3.0.3, 13.5 MB, one file per REST API version (`2022-11-28`,
  `2026-03-10`) plus a dereferenced copy.
- **Pull requests:** 34 operations under `/repos/{owner}/{repo}/pulls`. The
  core: `pulls/list`, `pulls/get`, `pulls/update` (PATCH: title, body, state,
  base), `pulls/merge` (PUT), `pulls/check-if-merged`, `pulls/update-branch`
  (PUT), `pulls/request-reviewers` and `pulls/remove-requested-reviewers`,
  `pulls/list-reviews` and `pulls/create-review`, `pulls/list-commits`,
  `pulls/list-files`.
- **The state:** `state` (`open`, `closed`), `draft`, `merged`, `mergeable`,
  `rebaseable`, `locked`, `mergeable_state` (a string with **no enum in the
  description**), `base` and `head` (each with `ref` and `sha`), and
  `_links` (HAL-style links to self, html, comments, commits, statuses).
- **Concurrency in the body:** `pulls/merge` takes `sha`, "SHA that pull
  request head must match to allow merge": GitHub's own If-Match.
- **Merge refusals:** 405, 409, 422, 403, 404.

## What it takes

### In HEART itself

1. **Resource identity beyond one number.** A resource type declares its item
   path template, with several variables
   (`/repos/{owner}/{repo}/pulls/{number}`), each filled from the state by a
   JSON Pointer. Today it's one `Long` under `/{collection}/{id}`. Clients
   never build URLs, so clients are unaffected; bindings route by the
   template, and the self link and template pointers fill every variable.
2. **Parameterized and nested collections.** A collection's search form takes
   its fields from the list operation's query parameters, not only name and
   id; a resource links its sub-collections (a repository its pull requests,
   a pull request its reviews and commits); the bookmark offers lookups with
   several fields (owner and repo).

### In the gateway

3. **Acts derived from operations.** A connector declares named acts as
   projections of an operation: a fixed part of the body, the fields exposed,
   and the upstream method. `close` and `reopen` are `pulls/update` with
   `{state: closed}` and `{state: open}`; `merge` is `pulls/merge`, a PUT.
   The gateway offers each as a HEART form and calls the operation. This
   turns CRUD into acts; Rally needs the same.
4. **Rules over several fields, with reasons.** Ordered rules, each a
   condition on several JSON Pointers, giving what's allowed and why the rest
   isn't (a draft can't merge; a branch behind its base must be updated
   first; a merged PR can't reopen).
5. **Facts from related resources.** A reader may fetch a related resource and
   use its fields in rules: whether the caller may merge is
   `permissions.push` on `GET /repos/{owner}/{repo}`. Cached per repository
   and caller, revalidated by ETag.
6. **Body fields bound from state.** A connector binds an operation's body
   field to the checked state and hides it from the form: `sha` from
   `/head/sha`. Merge through the gateway then has GitHub's own optimistic
   concurrency, not only check-then-act.
7. **Lists as they come.** A list that is a bare JSON array, paged by
   `Link: rel="next"`; item ids and titles by pointer (`#482 Fix the login
   bug`); turned into HEART's search pages and paging links.
8. **Slicing and normalizing the description.** Include by tag or operation
   (`pulls`, and `repos/get`); translate OpenAPI 3.0 schemas to JSON Schema
   2020-12 (`nullable`); relation names from operation ids with slashes
   (`pulls/merge` as `github:pulls-merge`, or as the connector names them);
   fixed headers on every upstream call (`X-GitHub-Api-Version`).
9. **Each caller's own credential, and caching.** What a caller sees and may
   do depends on their token, so the gateway calls GitHub as each caller.
   heart-core's resource calls carry no request today, so this needs a
   request context through the bindings. Upstream GETs revalidate with
   `If-None-Match`, to stay inside rate limits.
10. **"Not known yet."** GitHub computes mergeability in the background; the
   first read can say `mergeable: null`, `mergeable_state: unknown`. A rule
   makes merge absent with "GitHub is still checking; read it again shortly",
   so it never flickers on.

### What can't be configured

- **Which protection rule blocks a merge.** `blocked` means a required check
  or review is missing; naming which needs more calls (checks, reviews,
  branch protection). First version: "blocked by branch protection".
- **GitHub's full rules.** The gateway's rules are early binding; GitHub's own
  refusals stay the final word, passed through as problem details.

## Phases

Each phase is feature first, with a red run at the test level before any
production code, then green across every suite. The gateway's feature runner
must be read by the gate's log task first (today it reads only the TCK
runner), or its reds go unlogged; that's the author's change.

0. **Ground rules and access** (the author's).
   - A GitHub token the author provides, read from ARTIE's credentials, never
     printed. Read-only scopes for phases 1 to 9.
   - A scratch repository the author creates for any write. Nothing merged,
     closed or commented on a real repository without the author's explicit
     say-so, per run.
   - Low request rates; conditional requests throughout.
1. **heart-core: resource identity by path template.** Features in heart-core,
   then heart-tck and the binding features green on heart-spring,
   heart-http4k and the gateway, with the lamp declared at
   `/lamps/{id}` as before and a new fixture with two variables.
   *Done 2026-10-16:* `ItemPath`, `HeartResource.loadAt`; heart-spring and
   heart-http4k route by it (item-paths.feature in heart-core and heart-tck).
2. **heart-core: parameterized and nested collections.** Search forms from
   declared parameters; sub-collection links; multi-field lookups at the
   bookmark.
   *Done 2026-10-16, but for sub-collection links:* `Finding.collectionPath`
   and `SearchField`s; search and lookup forms, home document, OpenAPI and
   search pages follow them; both bindings serve the search
   (declared-searches.feature in heart-core and heart-tck). HAL-FORMS leaves
   these forms out, since its targets can't be templated. Still open: an item
   linking its collection and sub-collections, and typed OpenAPI item-path
   parameters.
3. **Gateway: slicing and normalizing.** Load the real GitHub description,
   include only pulls and `repos/get`, translate 3.0 schemas, name relations,
   add headers. Acceptance: the gateway starts on the real 13.5 MB file in
   a reasonable time and serves a bookmark and profile for the slice.
   *Done 2026-10-16, but for the 3.0 schema translation, which moves to
   phase 5 with the act schemas it applies to:* connector `include`,
   `headers` and `<name>.resource.yaml` files; the description is sliced
   before parsing; declared resources read and list (bare arrays, Link
   paging) through heart-core's item paths and declared searches
   (github.feature). On the real file: ready in 1.1 s, about 300 MB
   resident, bookmark and profile served, no call to GitHub.
4. **Gateway: item paths and lists.** Multi-variable items, bare-array lists,
   Link-header paging, titles.
5. **Gateway: derived acts and bound body fields.** Projections of
   operations; `sha` bound from `/head/sha`.
   *Done 2026-10-16:* a resource file's `acts`, each an operation with a
   fixed body, the fields a client fills, fields bound from the checked
   state, and what the upstream's statuses mean (GitHub's merge: 409 stale,
   405 refused). close and reopen are pulls/update; merge is pulls/merge
   with `sha` from `/head/sha`, so a merge after the head moved is refused
   by GitHub itself and answered 412 (github-acts.feature). OpenAPI 3.0
   `nullable` translation: still open; no field these acts expose uses it.
6. **Gateway: rules, related facts, "not known yet".** Multi-field rules with
   reasons; the repository's permissions as a fact; the unknown mergeability
   rule.
   *Done 2026-10-16, but for caching facts (phase 7):* a reader's `rules`,
   in order, each conditions on JSON Pointers into the answer or into a
   fact (`repository:/permissions/push`), making acts absent with reasons;
   a resource file's `facts`, each an operation whose path is filled from
   the state. Draft, merged, mergeability unknown, behind, dirty, blocked,
   and no push permission (github-rules.feature). The repository is read
   on every read of a pull request for now.
7. **Gateway: caller credentials and upstream caching.** Request context
   through the bindings; forward each caller's token; `If-None-Match`
   upstream.
   *Done 2026-10-16:* heart-core's `Caller`, set by heart-http4k (a filter)
   and heart-spring (coRouter's coroutine context); 401 and 403 refusals in
   both bindings (callers.feature). `UPSTREAM_AUTH=caller` forwards each
   caller's credential and admits any for GitHub to judge; GETs kept per
   caller and path and revalidated with `If-None-Match`, facts included
   (github-callers.feature).
8. **The GitHub connector, against a fake GitHub.** connectors/github, and a
   small in-memory GitHub that serves the real description's slice and
   recorded pull-request JSON in each state (open, draft, behind, blocked,
   clean, unknown, merged, closed). Features for every state's offers and
   reasons; heart-tck through the gateway in front of it.
   *Done 2026-10-16:* `heart-gateway/connectors/github`, shipped in the
   distribution. The "recorded" JSON is GitHub's own examples from its
   description (pull-request, full-repository), set to each state
   (github-states.feature). heart-tck's conformance kit runs through the
   gateway (RunGitHubTckTest, 19 scenarios, no bookmark act); the kit's
   fixture now names item paths, search and lookup targets.
9. **Live, read-only.** The author's token against a repository the author
   chooses: list pull requests, show each one's state, offers and reasons.
   Compare the gateway's reasons with GitHub's web page for the same PRs.
   Verify `mergeable_state`'s real values.
10. **Live writes, on the scratch repository only.** Update branch, close and
    reopen, merge; a stale merge refused by GitHub's `sha` check, as a 412.
11. **The demo and Part 2's report.** ARTIE and heart-mcp, asked in words:
    "what's the status of my PRs?", "why can't I merge #N?", "merge the one
    that's ready". Then the same question captured as a script and replayed
    without a model (spec/ideas/captured-scripts.md), stopping on drift.

## Open questions

- `mergeable_state` values: undocumented in the description; settle in phase 9.
- "My pull requests" across repositories is GitHub's search API
  (`/search/issues?q=is:pr+author:@me`), which returns issues, not pull
  requests; in scope, or one repository at a time first?
- Rate limits per token, and how far conditional requests stretch them.
- Whether the async merge (`pulls/merge-async`) should be the act instead,
  for large repositories.

## Risks

- **Scope.** Two HEART changes and seven gateway capabilities; each is
  general, but together they are a lot. Phases 1 to 8 need no network.
- **Early binding in the rules.** Mitigated by passing GitHub's refusals
  through, and by naming rules plainly so they're easy to review.
- **Credentials.** Forwarding callers' tokens raises the stakes on logging and
  caching: tokens never logged, cache keys never shared across callers for
  anything caller-specific.
- **GitHub changes.** The description is versioned; pin one
  (`2022-11-28`) and treat moving as a deliberate change.
