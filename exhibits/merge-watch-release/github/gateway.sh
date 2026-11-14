#!/bin/sh
# Serves real GitHub in scm's words on 8093: the HEART gateway with the github-scm connector, calling api.github.com
# with the token in ~/.artie/credentials ([https://api.github.com], bearer), which is never printed. The release's
# parties present GitHub's test token to it, as to the simulated GitHub on 8098. Every call it makes to GitHub is
# kept in GATEWAY_RECORD (default: work/github-upstream.yaml).
#   github/gateway.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
connector="$root/heart/heart-gateway/connectors/github-scm"
spec=${GITHUB_SPEC:-$HOME/.cache/artie/ghdesc/api.github.com.json}
[ -f "$spec" ] || { echo "no GitHub description at $spec (github/rest-api-description, api.github.com.json)"; exit 1; }
UPSTREAM_TOKEN=$(awk '/\[https:\/\/api.github.com\]/{f=1} f&&/bearer/{print $3; exit}' "$HOME/.artie/credentials")
[ -n "$UPSTREAM_TOKEN" ] || { echo "no GitHub token in ~/.artie/credentials"; exit 1; }
HEART_AUTH_BEARER_TOKENS=$(printf '%s' github-token | shasum -a 256 | cut -d' ' -f1)
export UPSTREAM_TOKEN HEART_AUTH_BEARER_TOKENS PORT=8093
export GATEWAY_CONNECTOR="$connector" UPSTREAM_SPEC="$spec" UPSTREAM_URL=https://api.github.com UPSTREAM_AUTH=bearer
export GATEWAY_RECORD=${GATEWAY_RECORD:-$here/../work/github-upstream.yaml}
export JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}
exec "$root/heart/heart-gateway/build/install/heart-gateway/bin/heart-gateway"
