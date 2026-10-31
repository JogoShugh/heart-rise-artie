# The gateway against live GitHub

spec/PLAN-GITHUB-PULLS.md, phases 9 and 10. A scratch repository, seeded
with a pull request in each state, read through the gateway as its owner.

## Seeding

`seed.py OWNER/REPO` shows what it would do; `--write` does it. It reads
the token from `~/.artie/credentials` (`[https://api.github.com]`,
`bearer`), never prints it, writes only to the repository it is given, and
leaves alone a pull request whose title is already there. It needs a
fine-grained token for that repository alone: Contents and Pull requests
read and write, and Administration read and write for the protected bases.

## Running the gateway against GitHub

    UPSTREAM_URL=https://api.github.com \
    UPSTREAM_SPEC=<GitHub's api.github.com.json, from github/rest-api-description> \
    GATEWAY_CONNECTOR=build/install/heart-gateway/connectors/github \
    UPSTREAM_AUTH=caller PORT=8392 \
    build/install/heart-gateway/bin/heart-gateway

Each caller sends their own GitHub token as a Bearer credential; the
gateway forwards it.

## Phase 9, read-only: 2026-10-16, JogoShugh/heart-github-test

Every pull request read through the gateway, GET only, as the owner:

| # | Pull request | GitHub says | Gateway offers | Absent, and why |
|---|---|---|---|---|
| 1 | Mulch the beds | open, mergeable, clean | close, merge | reopen: The pull request is already open. |
| 2 | Plan the autumn planting | open, draft, clean | close | merge: A draft can't be merged; mark it ready for review first. |
| 3 | Water the tomatoes more | open, not mergeable, dirty | close | merge: The branch has conflicts with its base; resolve them first. |
| 4 | Prune the roses | closed, merged, unknown | nothing | reopen: A merged pull request can't be reopened. close: already closed. merge: closed. |
| 5 | Pave the lawn | closed, unknown | reopen | close: already closed. merge: A closed pull request can't be merged; reopen it first. |
| 6 | Build a greenhouse | open, mergeable, blocked (review required) | close | merge: Branch protection blocks the merge: a required check or review is missing. |
| 7 | Add a compost bin | open, mergeable, clean | close, merge | reopen: already open. |

Each matches what GitHub's own page says of it: #1 and #7 can be merged,
#2 is a draft, #3 has conflicts, #4 is merged, #5 can be reopened, #6
waits for a review. #7 was meant to be behind its base, but GitHub says
behind only when the base's protection names a required check; with none,
it is clean, and so is the gateway's answer.

The bookmark offered `github:pulls-list` and `github:pulls-get`; the search
listed all seven at their own paths, titled. A request with no credential
was refused 401 before GitHub was asked.

Five reads of #1 in a row cost nothing of GitHub's rate limit: each was a
conditional request, answered 304 from the gateway's copy for that caller.

Seen live, and now how the tests' fake GitHub answers: an open pull
request's first read is `mergeable: null, unknown` until GitHub has worked
it out, seconds later; a draft's mergeable state is `clean`; a closed or
merged one stays `null, unknown`.

## Issues: 2026-10-17, JogoShugh/heart-github-test

`issues.py OWNER/REPO GATEWAY_URL OUT_FILE` acts on issues through the
gateway's forms, writing only to the repository it is given: it opens two
issues from the repository's `open-issue` form and comments on one (each
201 with Location), follows the issue's comments and sub-issues links,
and sends every issue act, each after reading until GitHub settles. The
token needs Issues read and write. `capture-issues.py OWNER/REPO
GATEWAY_URL OUT_DIR NAME=PATH...` reads each path both ways, GET only.
The captures are in `docs/reports/github-gateway-status/captures`
(`issue-*.json`, `issues-acts.json`).
