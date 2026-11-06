#!/usr/bin/env bash
# Usage: merge-bumps.sh OWNER REPO LABEL
# Squash-merges open PRs with LABEL, only where the allowed-now link says merge is allowed.
set -euo pipefail

[ $# -eq 3 ] || { echo "usage: $0 owner repo label" >&2; exit 2; }
owner=$1 repo=$2 label=$3
base=${API_BASE:-http://localhost:8400}
rel="https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now"

numbers=$(curl -fsS -G "$base/repos/$owner/$repo/issues" \
  --data-urlencode "labels=$label" --data-urlencode "state=open" --data-urlencode "per_page=100" |
  jq -r '.[] | select(.pull_request != null) | .number')

for n in $numbers; do
  # Find the allowed-now link in the Link header of the PR response.
  link=$(curl -fsSi "$base/repos/$owner/$repo/pulls/$n" | tr -d '\r' |
    awk -v rel="$rel" 'tolower($1)=="link:" { sub(/^[^:]*:[ ]*/, ""); print }' |
    tr ',' '\n' | grep -F "rel=\"$rel\"" | sed -n 's/^[^<]*<\([^>]*\)>.*/\1/p' | head -n1 || true)
  if [ -z "$link" ]; then
    echo "#$n: not merged: no allowed-now link"
    continue
  fi
  case $link in http*) url=$link ;; *) url=$base$link ;; esac

  state=$(curl -fsS "$url")
  if jq -e '.allowed | index("pulls/merge")' >/dev/null <<<"$state"; then
    res=$(curl -sS -X PUT -H 'Content-Type: application/json' \
      -d '{"merge_method":"squash"}' "$base/repos/$owner/$repo/pulls/$n/merge")
    if jq -e '.merged == true' >/dev/null <<<"$res"; then
      echo "#$n: merged"
    else
      echo "#$n: not merged: $(jq -r '.message // "merge failed"' <<<"$res")"
    fi
  else
    echo "#$n: not merged: $(jq -r '.notAllowed["pulls/merge"] // "pulls/merge not allowed"' <<<"$state")"
  fi
done
