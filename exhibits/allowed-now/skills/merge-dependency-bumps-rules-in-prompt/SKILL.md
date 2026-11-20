---
name: merge-dependency-bumps
description: Merge open dependency bump pull requests on GitHub, only where the team's rules allow it. Use when asked to merge, land or tidy up dependency bumps.
---

# Merge dependency bumps

Use GitHub's REST API at https://api.github.com with `curl -K auth.curlrc`, which holds the token.

1. List the open pull requests with the label you were given:
   `GET /repos/{owner}/{repo}/issues?labels={label}&state=open`
2. Read each one: `GET /repos/{owner}/{repo}/pulls/{number}`
3. Apply the team's rules, in this order. Don't merge it if:
   - it isn't open: "The pull request is closed."
   - it's a draft: "A draft can't be merged; mark it ready for review first."
   - `mergeable` is false: "The branch has conflicts; resolve them first."
   - its branch is behind its base: "The branch is behind its base; update it first." Check with
     `GET /repos/{owner}/{repo}/compare/{base.ref}...{head.sha}`; `behind_by` above 0 means behind.
4. Otherwise merge it: `PUT /repos/{owner}/{repo}/pulls/{number}/merge` with `{"merge_method": "squash"}`

End with one line per pull request: merged, or not merged and the rule's reason, word for word.
