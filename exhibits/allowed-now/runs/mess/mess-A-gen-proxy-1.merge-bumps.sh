#!/usr/bin/env bash
# Usage: merge-bumps.sh OWNER REPO LABEL
# Merges open labelled PRs whose "allowed-now" link permits pulls/merge.
# Requires only curl and jq. API base can be overridden with API_BASE.
set -euo pipefail

if [ $# -ne 3 ]; then
  echo "usage: $0 OWNER REPO LABEL" >&2
  exit 2
fi
owner=$1 repo=$2 label=$3
base=${API_BASE:-http://localhost:8400}
rel="https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now"

numbers=$(curl -fsS -G "$base/repos/$owner/$repo/issues" \
  --data-urlencode "labels=$label" --data-urlencode "state=open" \
  | jq -r '.[] | select(.pull_request != null) | .number')

for n in $numbers; do
  headers=$(curl -fsS -i "$base/repos/$owner/$repo/pulls/$n" | tr -d '\r')
  # Extract the target of the allowed-now link from the Link header(s).
  target=$(printf '%s\n' "$headers" | grep -i '^link:' \
    | grep -o "<[^>]*>; *rel=\"$rel\"" | head -n1 | sed -E 's/^<([^>]*)>.*/\1/' || true)
  if [ -z "$target" ]; then
    echo "#$n: not merged - no allowed-now link"
    continue
  fi
  case $target in
    http*) url=$target ;;
    *) url=$base$target ;;
  esac
  state=$(curl -fsS "$url")
  if [ "$(jq -r '.allowed | index("pulls/merge") != null' <<<"$state")" = true ]; then
    res=$(curl -fsS -X PUT -H 'Content-Type: application/json' \
      -d '{"merge_method":"squash"}' "$base/repos/$owner/$repo/pulls/$n/merge")
    if [ "$(jq -r '.merged' <<<"$res")" = true ]; then
      echo "#$n: merged"
    else
      echo "#$n: not merged - $(jq -r '.message' <<<"$res")"
    fi
  else
    echo "#$n: not merged - $(jq -r '.notAllowed["pulls/merge"] // "pulls/merge not allowed"' <<<"$state")"
  fi
done
