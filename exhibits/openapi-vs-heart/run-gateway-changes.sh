#!/usr/bin/env bash
# The six Wednesday changes (run-changes.sh), with ARTIE through the
# OpenAPI-to-HEART gateway (spec/PLAN-OPENAPI-GATEWAY.md, Phase 2).
#
# For each change, the garden restarts with it and HEART_AFFORDANCE_LINKS=true,
# the gateway restarts in front of its plain JSON twin and reads the changed
# description, the data is reset, and two clients, unchanged since Monday and
# pointed only at the gateway, try to water bed 2:
#   artie   the ARTIE dashboard, driven as a person would (changes/drive.mjs)
#   script  ARTIE driven by code that names the act and its field (ArtieScript)
# The score is the garden's own history, read from the garden: did bed 2 get a
# new CellWatered event? Compare with the native runs in results/changes/.
#
# RUN names the results folder (results/$RUN, default "changes-gateway");
# CHANGES and CLIENTS, space-separated, choose which to run. Refuses busy
# ports, stops only what it started, resets the data, never prints a credential.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=$here/results/${RUN:-changes-gateway}
garden=http://localhost:8080
gateway=http://localhost:8383
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
dashboard_java=${ARTIE_DASHBOARD_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}
read -r -a changes <<< "${CHANGES:-move-url rename-field rename-act retitle tighter-limit new-required-input}"
read -r -a clients <<< "${CLIENTS:-artie script}"

for port in 8080 8383 3002; do
  if lsof -nP -iTCP:$port -sTCP:LISTEN > /dev/null; then echo "Port $port is in use." >&2; exit 1; fi
done
# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null
GARDEN_TOKEN=$(auth_header "$garden" | sed 's/^Authorization: Bearer //')
export GARDEN_TOKEN GARDEN_URL=$garden

# ARTIE's credential for this run is for the gateway's origin only.
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
wait_for() { for _ in $(seq 1 120); do answering "$1" && return; sleep 1; done; echo "nothing at $1" >&2; exit 1; }
history() { curl -s -H @<(auth_header "$garden") "$garden/beds/2/history"; }
watered() { history | python3 -c 'import json,sys; h=json.load(sys.stdin); print(sum(1 for e in h if e["type"]=="CellWatered"))'; }

rm -rf "$out"; mkdir -p "$out"
(cd "$root/heart" && ./gradlew :heart-gateway:installDist -q)
(cd "$here/artie-dashboard" && ./gradlew installDist -q)
(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done

start_garden() {
  lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill 2> /dev/null || true
  sleep 2
  (cd "$root/server-spring" && service_env . && GARDEN_CHANGE=$1 HEART_AFFORDANCE_LINKS=true ./gradlew bootRun --quiet \
    > "$out/garden-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$garden/"
}

# The gateway reads the description when it starts, so it restarts with each release of the garden.
start_gateway() {
  lsof -ti tcp:8383 -sTCP:LISTEN | xargs kill 2> /dev/null || true
  sleep 1
  (cd "$root/server-spring" && service_env . && UPSTREAM_URL=$garden UPSTREAM_AUTH=bearer \
     UPSTREAM_TOKEN=$GARDEN_TOKEN GATEWAY_PROFILE=garden PORT=8383 \
     "$root/heart/heart-gateway/build/install/heart-gateway/bin/heart-gateway" > "$out/gateway-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$gateway/"
}

attempt() {
  local change=$1 client=$2
  "$root/server-spring/reset-db.sh" > /dev/null
  local before after line
  before=$(watered)
  if [ "$client" = script ]; then
    line=$( (cd "$here/artie-dashboard" && ARTIE_HOME=$artie_home HEART_BOOKMARK=$gateway/ JAVA_HOME=$dashboard_java \
      "$dashboard_java/bin/java" -cp "build/install/artie-dashboard/lib/*" ArtieScriptKt) || true)
    line=$(printf '%s\n' "$line" | python3 -c '
import json,sys
lines=sys.stdin.read().splitlines()
calls=[dict(zip(("method","path","status"),l.split()[1:4])) for l in lines if l.startswith("CALL ")]
for c in calls: c["status"]=int(c["status"])
said=[l[7:] for l in lines if l.startswith("RESULT ")]
print(json.dumps({"client":"script","typed":None,"shown":None,"pressed":"act garden:water-cell {milliliters: 250}","said":said[0] if said else None,"calls":calls}))')
  else
    line=$(cd "$here/changes" && node drive.mjs "$client")
  fi
  after=$(watered)
  printf '%s\n' "$line" | python3 -c '
import json,sys
r=json.loads(sys.stdin.read()); r["change"]=sys.argv[1]; r["watered"]=int(sys.argv[3])>int(sys.argv[2])
r["refused"]=[c for c in r["calls"] if c["status"]>=400]
print(json.dumps(r))' "$change" "$before" "$after" >> "$out/attempts.jsonl"
  tail -1 "$out/attempts.jsonl" | jq -r '"   \(.client) watered=\(.watered) pressed=\(.pressed) refused=\([.refused[].status])"'
}

start_garden ""
start_gateway ""
(cd "$here/artie-dashboard" && ARTIE_HOME=$artie_home HEART_BOOKMARK=$gateway/ JAVA_HOME=$dashboard_java \
  build/install/artie-dashboard/bin/artie-dashboard > "$out/artie.log" 2>&1) &
pids+=($!)
wait_for http://localhost:3002/_calls

for change in "${changes[@]}"; do
  echo "== $change =="
  start_garden "$change"
  start_gateway "$change"
  for client in "${clients[@]}"; do attempt "$change" "$client"; done
done
echo "Attempts in $out/attempts.jsonl"
