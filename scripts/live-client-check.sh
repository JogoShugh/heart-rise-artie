#!/usr/bin/env bash
# Runs ARTIE's @live scenarios against a real server-spring + Postgres.
#   1. Starts the stack if it is not answering (docker compose up --build -d).
#   2. Resets the seed data.
#   3. Runs `./gradlew liveTest` in client/.
# Leaves the stack running; stop it with `docker compose down` in server-spring/.
# Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
base=${ARTIE_BASE_URL:-http://localhost:8080}
# Gradle 8.13 cannot run on JDK 25, which may be the shell default.
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}

if ! curl -sf -o /dev/null "$base/beds/1"; then
  (cd "$root/server-spring" && docker compose up --build -d)
  for _ in $(seq 1 60); do curl -sf -o /dev/null "$base/beds/1" && break; sleep 2; done
fi
curl -sf -o /dev/null "$base/beds/1" || { echo "server not answering at $base" >&2; exit 1; }

"$root/server-spring/reset-db.sh" > /dev/null
(cd "$root/client" && ARTIE_BASE_URL="$base" ./gradlew liveTest --console=plain)
