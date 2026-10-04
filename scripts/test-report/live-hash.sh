#!/usr/bin/env bash
# Re-fetches every content-addressed URL that server-spring/capture.sh saw
# and checks that the body hashes to the address (SPEC §6.5). Writes one
# line per URL to live-hash.txt next to this script.
#   Run after capture.sh, with the server still up.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
run=$here/../../server-spring/run
base=${ARTIE_BASE_URL:-http://localhost:8080}
out=$here/live-hash.txt

urls=$( (cat "$run"/*-url.txt; jq -r '.. | objects | select(has("$id")) | ."$id"' "$run"/*set*.json) \
  | grep -E '^/(affordance-sets|schemas)/sha256-' | sort -u)

: > "$out"
for u in $urls; do
  served=$(curl -s "$base$u" | shasum -a 256 | cut -c1-16)
  [ "$served" = "${u##*sha256-}" ] && verdict=MATCH || verdict=MISMATCH
  echo "$u served=$served $verdict" >> "$out"
done
cat "$out"
