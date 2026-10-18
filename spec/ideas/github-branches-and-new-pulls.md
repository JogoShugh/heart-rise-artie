# GitHub through the gateway: branches, commits and new pull requests

An idea, drafted 2026-10-18 and parked while the author looks at an earlier problem. Not started.

## Why

The GitHub task for step 3 of `spec/PLAN-ARTIE-SCRIPTS.md` should do real writes on both pull
requests and issues: open an issue, make a pull request, merge it, comment on the issue, close it.
The gateway can't make a pull request today. `pulls/create` is in GitHub's pull request API, but
the connector's `include` list was chosen on 2026-10-16 as a working slice (list, get, update,
merge, update the branch, request reviewers), and creating one was never picked. A pull request
also needs a branch with a commit on it, and those operations live in other parts of GitHub's API:
`git/create-ref` (the low-level git API) and `repos/create-or-update-file-contents` (the
repository API).

For now `client/scripts/tests/github-task-setup.sh` makes the branch, the commit and the pull
request with GitHub's own API, outside HEART. Its first use made pull request #20 in the scratch
repository, still open and unused.

## Goal

The whole task through HEART, from the bookmark: branch, commit, open a pull request, merge, close
the issue.

## 1. A new kind of resource: the branch

- Read by `repos/get-branch`; listed by `repos/list-branches`, linked from the repository.
- Its state already holds the name, the head commit's sha, and whether it is protected.
- New files: `branch.resource.yaml`, `branch.reader.yaml`.

## 2. Three acts

| Offered by | Act | GitHub operation | Fields | Taken from the branch's state | Makes |
|---|---|---|---|---|---|
| A branch | `branch-from-here` | `git/create-ref` | the new branch's name | the head sha | a branch |
| A branch | `commit-file` | `repos/create-or-update-file-contents` | path, message, content | the branch's name | nothing new; the branch moves on |
| A branch | `open-pull-request` | `pulls/create` | title, body, base, draft | head, from the branch's name | a pull request |

Opening a pull request from the branch means the client never types the head branch: the form
fills it from state, as merge already fills its `sha`.

## 3. Why an act is absent (reader rules)

| When | Absent | Reason |
|---|---|---|
| The caller can't push to the repository | all three | the repository's permissions say so |
| The branch is protected | `commit-file` | the branch is protected |
| The branch is the repository's default branch | `open-pull-request` | open it from another branch |

## 4. The bookmark

The bookmark doesn't offer `github:repos-get` today (the first scripted run stopped there, refused
by ARTIE before anything was sent). Find out why, and make the repository reachable from the
bookmark, or the task can't start there.

## 5. Unknowns to check in the gateway's code first

1. **The `refs/heads/` prefix.** `git/create-ref` wants `refs/heads/<name>` and a sha. Either the
   form asks for a bare name and the gateway adds the prefix (new: a body template), or the form
   asks for the full ref (clumsy, no new code).
2. **Base64 content.** `create-or-update-file-contents` wants the content base64-encoded. Either
   the schema says so and the client encodes, or the gateway encodes (new code).
3. **The new branch's address.** GitHub answers `create-ref` with a ref, not a branch, so the
   gateway must derive the branch's address from the name for its Location. Possibly a small
   pointer rule.

Each may fit the configuration the gateway already has. Any that needs code is written test-first
under the gate rules.

## 6. Order

1. Features first: gateway scenarios against the fake GitHub, a red run, then the YAML, then green.
2. A live smoke test in the scratch repository (`JogoShugh/heart-github-test`) only.
3. Remove `github-task-setup.sh`; the three task scripts start from the bookmark.
4. The scripted, reasoned and hybrid runs, three each, with run reports and the comparison page.

Estimate: half a day if the unknowns fit the configuration; about a day if one needs gateway code.
