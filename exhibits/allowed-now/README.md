# Allowed now: your own guardrail in front of GitHub

GitHub's REST API doesn't say what may be done to a pull request now. It has the facts (`mergeable_state`, and how
far a branch is behind its base), and the repository's settings decide the policy. Changing those settings needs
admin rights many teams don't have.

This exhibit adds the missing piece in front of GitHub, in two small files:

- `proxy.ts` (Express and http-proxy-middleware): every request goes through to GitHub unchanged. A pull request's
  answer gets one more `Link` header, to what may be done to it now. The rules are plain `if` statements, and one
  is our own: a branch behind its base isn't merged until it's updated.
- `merge.ts` (Octokit): merges a pull request, unless the answer links to what's allowed now and merge isn't among it.

The relation is [`allowed-now`](https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now/).

```
npm install
GITHUB_TOKEN=... npm run proxy
GITHUB_TOKEN=... API_URL=http://localhost:8400 npm run merge -- OWNER REPO NUMBER   # through the proxy
GITHUB_TOKEN=... npm run merge -- OWNER REPO NUMBER                                # straight to GitHub
```

## The run of 2026-10-31 (`runs/run-1031-1258.txt`)

On the scratch repository JogoShugh/heart-github-test, into `deps-main`. That branch requires the check
`deps-check` but not "Require branches to be up to date before merging", so GitHub calls a branch that's behind
its base clean.

| Pull request | GitHub says | Through the proxy | Straight to GitHub |
|---|---|---|---|
| #1874, one commit behind its base | clean, mergeable | Not merged: "The branch is behind its base; update it first." | Merged |
| #1876, fresh | clean, mergeable | Merged | |

The guardrail holds for every client that goes through the proxy. It doesn't change GitHub: a client that goes
straight to GitHub merges as before.
