#!/usr/bin/env bash
# ARTIE through the OpenAPI-to-HEART gateway (spec/PLAN-OPENAPI-GATEWAY.md,
# Phase 2). The garden runs with HEART_AFFORDANCE_LINKS=true, and the gateway
# on 8383 serves it to HEART clients from its plain JSON twin alone: the
# OpenAPI description, and the affordance link on each answer. The ARTIE
# dashboard is pointed at the gateway, not the garden, and the regression
# suite runs Monday (no frost) and Tuesday morning (the frost), as in
# run-matrix.sh. The results sit beside the native ARTIE runs, to compare.
#
# Needs Docker (Postgres), Node 20 and npm, JDK 21 for the garden and the
# gateway, JDK 22 or later for the ARTIE dashboard. Refuses to start if 8080,
# 8383 or 3002 is taken. Never prints a credential. Opens nothing on screen.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
results=$here/results/gateway
garden=http://localhost:8080
gateway=http://localhost:8383
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
dashboard_java=${ARTIE_DASHBOARD_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}

for port in 8080 8383 3002; do
  if lsof -nP -iTCP:$port -sTCP:LISTEN > /dev/null; then
    echo "Port $port is in use; this script starts and stops its own servers." >&2
    exit 1
  fi
done

# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null
GARDEN_TOKEN=$(auth_header "$garden" | sed 's/^Authorization: Bearer //')
export GARDEN_TOKEN GARDEN_URL=$garden GARDEN_RESET=$root/server-spring/reset-db.sh

# ARTIE finds its credential for the gateway's origin in a credentials file of
# its own for this run: the garden's token, which the gateway also admits.
artie_home=$(mktemp -d)
(umask 077 && printf '[%s]\nbearer = %s\n' "$gateway" "$GARDEN_TOKEN" > "$artie_home/credentials")

pids=()
stop_all() {
  for pid in "${pids[@]}"; do kill "$pid" 2> /dev/null || true; done
  for port in 8080 8383 3002; do lsof -ti "tcp:$port" -sTCP:LISTEN | xargs kill 2> /dev/null || true; done
  rm -rf "$artie_home"
  sleep 2
  "$root/server-spring/reset-db.sh" > /dev/null 2>&1 || true
}
trap stop_all EXIT

wait_for() {
  for _ in $(seq 1 120); do answering "$1" && return; sleep 1; done
  echo "nothing answered at $1" >&2
  exit 1
}

start_garden() {
  lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill 2> /dev/null || true
  sleep 2
  (cd "$root/server-spring" && service_env . && GARDEN_FROST=$1 HEART_AFFORDANCE_LINKS=true ./gradlew bootRun --quiet \
    > "$results/garden-frost-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$garden/"
}

# The gateway reads the garden's description when it starts, so it restarts with each garden release.
start_gateway() {
  lsof -ti tcp:8383 -sTCP:LISTEN | xargs kill 2> /dev/null || true
  sleep 1
  (cd "$root/server-spring" && service_env . && UPSTREAM_URL=$garden UPSTREAM_AUTH=bearer \
     UPSTREAM_TOKEN=$GARDEN_TOKEN GATEWAY_PROFILE=garden PORT=8383 \
     "$root/heart/heart-gateway/build/install/heart-gateway/bin/heart-gateway" > "$results/gateway-frost-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$gateway/"
}

suite() {
  local release=$1 tags=$2
  local name="$release--artie-gateway"
  echo "== $release: ARTIE through the gateway =="
  (cd "$here/suite" && CLIENT=artie DASHBOARD_URL=http://localhost:3002 npx cucumber-js \
     --tags "$tags" --format summary --format "json:$results/$name.json" \
     > "$results/$name.txt" 2>&1) || true
  tail -4 "$results/$name.txt" | sed 's/^/   /'
}

mkdir -p "$results"
echo "Building the gateway and the ARTIE dashboard"
(cd "$root/heart" && ./gradlew :heart-gateway:installDist -q)
(cd "$here/suite" && npm ci --no-audit --no-fund --silent)
(cd "$here/artie-dashboard" && ./gradlew installDist -q)

(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done

echo "Monday: the garden serves 1.0.0, through the gateway"
start_garden false
"$root/server-spring/reset-db.sh" > /dev/null
start_gateway false
(cd "$here/artie-dashboard" && ARTIE_HOME=$artie_home HEART_BOOKMARK=$gateway/ JAVA_HOME=$dashboard_java \
  build/install/artie-dashboard/bin/artie-dashboard > "$results/artie-dashboard.log" 2>&1) &
pids+=($!)
wait_for http://localhost:3002/_calls
suite monday "not @tuesday"

echo "Tuesday: the garden ships the frost; the garden and the gateway restart, ARTIE does not"
start_garden true
start_gateway true
suite tuesday-morning "@tuesday or not @tuesday"
