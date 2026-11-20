#!/usr/bin/env bash
# Merge open dependency-bump PRs carrying a label, applying the team's rules.
# Usage: merge-bumps.sh OWNER REPO LABEL
# Needs curl and jq. Token is read from auth.curlrc (curl -K) in the current directory.
set -euo pipefail

[ $# -eq 3 ] || { echo "usage: $0 owner repo label" >&2; exit 2; }
owner=$1 repo=$2 label=$3
api=https://api.github.com/repos/$owner/$repo
gh() { curl -sfK "${AUTH_CURLRC:-auth.curlrc}" "$@"; }

numbers=$(gh -G --data-urlencode "labels=$label" --data-urlencode "state=open" \
  --data-urlencode "per_page=100" "$api/issues" | jq -r '.[] | select(.pull_request != null) | .number')

for n in $numbers; do
  pr=$(gh "$api/pulls/$n")
  state=$(jq -r .state <<<"$pr")
  draft=$(jq -r .draft <<<"$pr")
  mergeable=$(jq -r .mergeable <<<"$pr")
  base=$(jq -r .base.ref <<<"$pr")
  sha=$(jq -r .head.sha <<<"$pr")

  if [ "$state" != open ]; then
    echo "#$n not merged: The pull request is closed."; continue
  fi
  if [ "$draft" = true ]; then
    echo "#$n not merged: A draft can't be merged; mark it ready for review first."; continue
  fi
  if [ "$mergeable" = false ]; then
    echo "#$n not merged: The branch has conflicts; resolve them first."; continue
  fi
  behind=$(gh "$api/compare/$base...$sha" | jq -r .behind_by)
  if [ "$behind" -gt 0 ]; then
    echo "#$n not merged: The branch is behind its base; update it first."; continue
  fi

  if gh -X PUT -d '{"merge_method":"squash"}' "$api/pulls/$n/merge" | jq -e .merged >/dev/null; then
    echo "#$n merged"
  else
    echo "#$n not merged: merge request failed"
  fi
done
