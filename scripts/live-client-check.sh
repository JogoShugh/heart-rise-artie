#!/usr/bin/env bash
# Runs ARTIE's @live scenarios against a real server-spring + Postgres.
#   1. Starts the stack if it is not answering (docker compose up --build -d).
#   2. Resets the seed data.
#   3. Runs `./gradlew liveTest` in client/.
# Leaves the stack running; stop it with `docker compose down` in server-spring/.
# Set HEART_MAX_RUNG to the rung the server was started at (L2 by default).
# Writes ARTIE's event log to server-spring/build/artie-events.jsonl (ARTIE_EVENTS).
# Opens nothing on screen.
# To prove ARTIE against Buster, run scripts/buster-campaign.sh instead: it starts its own
# Buster server and calls this script once per round.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
base=${ARTIE_BASE_URL:-http://localhost:8080}
# Gradle 8.13 cannot run on JDK 25, which may be the shell default.
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
# shellcheck source=lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
# Every service requires a credential: the garden's .env, and ARTIE's in ~/.artie.
"$root/scripts/dev-credentials.sh" > /dev/null

if ! answering "$base/"; then
  (cd "$root/server-spring" && docker compose up --build -d)
  for _ in $(seq 1 60); do answering "$base/" && break; sleep 2; done
fi
answering "$base/" || { echo "server not answering at $base" >&2; exit 1; }

"$root/server-spring/reset-db.sh" > /dev/null
# ARTIE's event log (hack log, 2026-10-10). A Buster server started with the buster
# profile reads this same file, so its verdicts can use what ARTIE says it decided.
export ARTIE_EVENTS=${ARTIE_EVENTS:-$root/server-spring/build/artie-events.jsonl}
mkdir -p "$(dirname "$ARTIE_EVENTS")"
: > "$ARTIE_EVENTS"
(cd "$root/client" && ARTIE_BASE_URL="$base" ./gradlew liveTest --rerun --console=plain)
