---
name: merge-dependency-bumps
description: Merge open dependency bump pull requests on GitHub, through the team's proxy, only where merging is allowed now. Use when asked to merge, land or tidy up dependency bumps.
---

# Merge dependency bumps

The team's GitHub API is at http://localhost:8400. Use it with curl for every request, and never call
api.github.com. It needs no token.

1. List the open pull requests with the label you were given:
   `GET /repos/{owner}/{repo}/issues?labels={label}&state=open`
2. Read each one with `curl -i`, to keep its headers: `GET /repos/{owner}/{repo}/pulls/{number}`
3. Its `Link` header has a link with `rel="https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now"`.
   Follow it with a GET.
4. If `pulls/merge` is in `allowed`, merge it:
   `PUT /repos/{owner}/{repo}/pulls/{number}/merge` with `{"merge_method": "squash"}`
5. Otherwise leave it, and report the reason from `notAllowed`, word for word.

Don't decide for yourself whether a pull request can be merged: the link says. End with one line per pull
request: merged, or not merged and why.
