# Plan: GitHub issues through the gateway

After pull requests (spec/PLAN-GITHUB-PULLS.md), issues, the author's choice,
2026-10-17. Every step feature first, red at the test level, then green on
every suite; features give lists and matrices as data tables. Live writes on
JogoShugh/heart-github-test only, authorized for this plan.

## Decisions (the author's, 2026-10-17)

- Scope: I1 to I6, all of it.
- Who may do what is said with rule extensions in the engine, not JSONata.
- Act names close, reopen and edit are shared with pull requests in the
  `github` profile; their descriptions say what they do to either.
- Live writes on the scratch repository when tests are green.

## What GitHub's Issues API has (61 operations)

Read (get, list-for-repo with state, labels, assignee, creator, mentioned,
milestone, sort, direction, since; lists include pull requests), create,
update (state with state_reason completed, not_planned, duplicate;
title, body, milestone, type), assignees, labels (remove-label names the
label in the path), lock (lock_reason) and unlock, comments (a
sub-collection), sub-issues, dependencies, events and timeline. Writes take
no version: the gateway's re-read before acting is the only stale guard.

## Steps

- **I1. Read.** `issue.resource.yaml`: issues/get, issues/list-for-repo and
  its filters; a reader by `/state`. Engine: a resource file may `skip`
  list items by condition (pull requests are issues too).
- **I2. State acts, YAML only.** close (completed), close-not-planned,
  close-duplicate (duplicate_issue_id), reopen, edit (title, body), lock
  (lock_reason), unlock.
- **I3. Labels and people.** label, unlabel, assign, unassign, set the
  milestone. Engine: a path variable an item's state doesn't fill is filled
  from the client's field of that name (remove-label's `{name}`).
- **I4. Who may do what.** Facts: the repository's permissions, the caller
  (`GET /user`). Engine: rule conditions `present`, `not`, `same-as` and
  `not-same-as` another field or fact, beside plain equality. Author or
  triage may close and reopen; triage labels, assigns, sets milestones;
  push locks; a locked issue takes comments from push only.
- **I5. Create.** A repository resource offers "open an issue"; an act may
  bring another resource into being (heart-core: 201 with Location in
  both bindings).
- **I6. Comments and sub-issues.** heart-core: a resource links its
  sub-collections. Engine: a resource may name other list operations,
  each served at its own path and linked from the item; an item's path
  variables may come from its own `url`. Comments as a resource; comment
  as an act; sub-issues listed, added, removed.
- **I7. Tests**: a fake GitHub's issues on GitHub's own example JSON; state
  by act matrices; the conformance kit through the gateway.
- **I8. Live and reports**: seed issues on the scratch repository, capture,
  act; the status, connector and walkthrough reports updated.
