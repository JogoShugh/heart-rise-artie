#!/usr/bin/env bash
# Early binding against late binding, run for real. One garden server, three
# dashboards, one regression suite, three releases:
#
#   Monday           server 1.0.0   bespoke 1.0, ARTIE           Monday's scenarios
#   Tuesday morning  server 1.1.0   bespoke 1.0, ARTIE, as is    the whole suite
#   Tuesday, later   server 1.1.0   bespoke 1.1 (the rework)     the whole suite
#
# The dashboards keep running from Monday into Tuesday: only the garden restarts,
# with GARDEN_FROST=true, as a server-side release would. The suite drives each
# dashboard over HTTP (suite/), and writes its reports and a summary to results/.
#
# Needs Docker (Postgres), Node 20 and npm, and a JDK 22 or later for the ARTIE
# dashboard (ARTIE_DASHBOARD_JAVA_HOME). Refuses to start if 8080, 3001, 3002 or 3003
# is taken. Never prints a credential. Opens nothing on screen.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
results=$here/results
garden=http://localhost:8080
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
dashboard_java=${ARTIE_DASHBOARD_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}

for port in 8080 3001 3002 3003; do
  if lsof -nP -iTCP:$port -sTCP:LISTEN > /dev/null; then
    echo "Port $port is in use; this script starts and stops its own servers." >&2
    exit 1
  fi
done

# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null
# The bespoke dashboards take the garden's token in GARDEN_TOKEN; the suite uses it to report the frost.
GARDEN_TOKEN=$(auth_header "$garden" | sed 's/^Authorization: Bearer //')
export GARDEN_TOKEN GARDEN_URL=$garden GARDEN_RESET=$root/server-spring/reset-db.sh

pids=()
stop_all() {
  for pid in "${pids[@]}"; do kill "$pid" 2> /dev/null || true; done
  for port in 8080 3001 3002 3003; do lsof -ti "tcp:$port" -sTCP:LISTEN | xargs kill 2> /dev/null || true; done
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
  (cd "$root/server-spring" && service_env . && GARDEN_FROST=$1 ./gradlew bootRun --quiet \
    > "$results/garden-frost-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$garden/"
}

start_bespoke() {
  local version=$1 port=$2
  (cd "$here/bespoke-$version" && PORT=$port node dist/dashboard.js > "$results/bespoke-$version.log" 2>&1) &
  pids+=($!)
  wait_for "http://localhost:$port/_calls"
}

suite() {
  local release=$1 client=$2 version=$3 port=$4 tags=$5
  local name="$release--$client-$version"
  echo "== $release: $client $version =="
  (cd "$here/suite" && CLIENT=$client DASHBOARD_URL=http://localhost:$port npx cucumber-js \
     --tags "$tags" --format summary --format "json:$results/$name.json" \
     > "$results/$name.txt" 2>&1) || true
  tail -4 "$results/$name.txt" | sed 's/^/   /'
}

mkdir -p "$results"
echo "Building the dashboards"
for version in 1.0 1.1; do (cd "$here/bespoke-$version" && npm ci --no-audit --no-fund --silent && npx tsc); done
(cd "$here/suite" && npm ci --no-audit --no-fund --silent)
(cd "$here/artie-dashboard" && ./gradlew installDist -q)

(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done

echo "Monday: the garden serves 1.0.0"
start_garden false
"$root/server-spring/reset-db.sh" > /dev/null
start_bespoke 1.0 3001
(cd "$here/artie-dashboard" && JAVA_HOME=$dashboard_java build/install/artie-dashboard/bin/artie-dashboard \
  > "$results/artie-dashboard.log" 2>&1) &
pids+=($!)
wait_for http://localhost:3002/_calls
suite monday bespoke 1.0 3001 "not @tuesday"
suite monday artie 1.0 3002 "not @tuesday"

echo "Tuesday: the garden team ships the frost; only the server restarts"
start_garden true
suite tuesday-morning bespoke 1.0 3001 "@tuesday or not @tuesday"
suite tuesday-morning artie 1.0 3002 "@tuesday or not @tuesday"

echo "Tuesday, after the bespoke team's rework: dashboard 1.1 released"
start_bespoke 1.1 3003
suite tuesday-after-rework bespoke 1.1 3003 "@tuesday or not @tuesday"

node "$here/summarize.mjs" "$results" > "$results/matrix.json"
echo "Summary in $results/matrix.json"
