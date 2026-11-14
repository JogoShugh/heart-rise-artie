#!/usr/bin/env bash
# Merge open dependency-bump PRs carrying a label, under the team's rules.
# Usage: merge-bumps.sh OWNER REPO LABEL
# Auth: curl config file holding the token; defaults to auth.curlrc next to this
# script, override with AUTH_CURLRC=/path/to/file.
set -euo pipefail

if [ $# -ne 3 ]; then
  echo "usage: $0 OWNER REPO LABEL" >&2
  exit 2
fi
owner=$1 repo=$2 label=$3
auth=${AUTH_CURLRC:-$(dirname "$0")/auth.curlrc}
api=https://api.github.com/repos/$owner/$repo

gh() { curl -sS -K "$auth" -H 'Accept: application/vnd.github+json' "$@"; }

# 1. open PRs with the label (the issues endpoint also returns plain issues)
numbers=$(gh -G --data-urlencode "labels=$label" --data-urlencode state=open \
  --data-urlencode per_page=100 "$api/issues" |
  jq -r 'if type=="array" then .[] | select(.pull_request != null) | .number else error(.message // "unexpected response") end')

for n in $numbers; do
  # 2. read the PR; mergeable is null while GitHub computes it, so retry briefly
  for _ in 1 2 3 4 5; do
    pr=$(gh "$api/pulls/$n")
    [ "$(jq -r '.mergeable' <<<"$pr")" != null ] && break
    sleep 2
  done
  title=$(jq -r '.title' <<<"$pr")

  # 3. rules, in order
  reason=
  if [ "$(jq -r '.state' <<<"$pr")" != open ]; then
    reason="The pull request is closed."
  elif [ "$(jq -r '.draft' <<<"$pr")" = true ]; then
    reason="A draft can't be merged; mark it ready for review first."
  elif [ "$(jq -r '.mergeable' <<<"$pr")" = false ]; then
    reason="The branch has conflicts; resolve them first."
  else
    base=$(jq -r '.base.ref' <<<"$pr")
    sha=$(jq -r '.head.sha' <<<"$pr")
    behind=$(gh "$api/compare/$base...$sha" | jq -r '.behind_by // empty')
    if [ -z "$behind" ] || [ "$behind" -gt 0 ]; then
      reason="The branch is behind its base; update it first."
    fi
  fi

  if [ -n "$reason" ]; then
    echo "#$n $title: not merged. $reason"
    continue
  fi

  # 4. merge
  resp=$(gh -X PUT -d '{"merge_method":"squash"}' "$api/pulls/$n/merge")
  if [ "$(jq -r '.merged // false' <<<"$resp")" = true ]; then
    echo "#$n $title: merged"
  else
    echo "#$n $title: not merged. $(jq -r '.message // "merge failed"' <<<"$resp")"
  fi
done
