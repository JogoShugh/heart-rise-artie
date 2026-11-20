#!/bin/sh
# The smoke test's one inference run: ARTIE walks smoke.goal.md with Claude against a known-good preview, captured,
# then artie generate keeps the walk as smoke.heart.yaml, the script every build archives and every release replays
# with its own inputs and no model. Needs a model key: ANTHROPIC_API_KEY, or an entry for https://api.anthropic.com
# in ~/.artie/credentials. The preview's card and PIN are the example's test card.
#   capture.sh http://localhost:8185/
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
preview=${1:-http://localhost:8185/}
artie="$root/client/artie-cli/build/install/artie/bin/artie"
origin=$(printf '%s' "$preview" | sed -E 's#^https?://##; s#/.*$##; s#[.:]#_#g' | tr 'a-z' 'A-Z')
export "ARTIE_USER_$origin=21000451" "ARTIE_PASSWORD_$origin=1234"
cd "$here"
"$artie" run smoke.goal.md "deployed=$preview" --reasoner claude --trace smoke.capture.yaml
"$artie" generate smoke.capture.yaml --out smoke.heart.yaml --script smoke.goal.md --format yaml
