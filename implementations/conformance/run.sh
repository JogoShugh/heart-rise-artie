#!/usr/bin/env bash
# Runs the client conformance features against the live garden, with heart-ts.
# Starts the garden itself (refusing if 8080 is taken), passes its credential to
# the steps without printing it, and stops and resets the garden afterwards.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null; then echo "Port 8080 is in use." >&2; exit 1; fi
# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null
GARDEN_TOKEN=$(auth_header http://localhost:8080 | sed 's/^Authorization: Bearer //')
export GARDEN_TOKEN GARDEN_URL=http://localhost:8080 GARDEN_RESET=$root/server-spring/reset-db.sh
stop() { lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill 2> /dev/null || true; sleep 2; "$root/server-spring/reset-db.sh" > /dev/null 2>&1 || true; }
trap stop EXIT
(cd "$root/implementations/heart-ts" && npm ci --no-audit --no-fund --silent && npx tsc)
(cd "$here" && npm ci --no-audit --no-fund --silent)
(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
(cd "$root/server-spring" && service_env . && ./gradlew bootRun --quiet > build/conformance-server.log 2>&1) &
for _ in $(seq 1 90); do answering http://localhost:8080/ && break; sleep 2; done
(cd "$here" && npx cucumber-js --format summary --format "json:$here/results.json")
