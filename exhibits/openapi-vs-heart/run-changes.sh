#!/usr/bin/env bash
# Six more kinds of change, scored on every client (fairness item 1).
#
# For each Wednesday change (garden.change), the garden restarts with it, the
# data is reset, and four clients, none changed since Monday, try to water bed 2:
#   bespoke   the bespoke dashboard 1.0, generated from Monday's spec
#   artie     the ARTIE dashboard, driven as a person would (changes/drive.mjs)
#   script    ARTIE driven by code that names the act and its field (ArtieScript)
#   claude    the ARTIE dashboard's Ask, with Claude choosing (one run per change)
# Under tighter-limit the person also types 800 ml on the bespoke dashboard,
# whose field still allows 1000.
#
# The score is the garden's own history: did bed 2 get a new CellWatered event?
# Where the bespoke dashboard fails, it is regenerated from the change's spec and
# tsc's errors are kept. Results go to results/changes/. Refuses busy ports,
# stops only what it started, resets the data, and never prints a credential.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=$here/results/changes
garden=http://localhost:8080
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
dashboard_java=${ARTIE_DASHBOARD_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}
changes=(move-url rename-field rename-act retitle tighter-limit new-required-input)

for port in 8080 3001 3002; do
  if lsof -nP -iTCP:$port -sTCP:LISTEN > /dev/null; then echo "Port $port is in use." >&2; exit 1; fi
done
# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null
GARDEN_TOKEN=$(auth_header "$garden" | sed 's/^Authorization: Bearer //')
export GARDEN_TOKEN GARDEN_URL=$garden

pids=()
stop_all() {
  for pid in "${pids[@]}"; do kill "$pid" 2> /dev/null || true; done
  for port in 8080 3001 3002; do lsof -ti "tcp:$port" -sTCP:LISTEN | xargs kill 2> /dev/null || true; done
  sleep 2
  "$root/server-spring/reset-db.sh" > /dev/null 2>&1 || true
}
trap stop_all EXIT
wait_for() { for _ in $(seq 1 120); do answering "$1" && return; sleep 1; done; echo "nothing at $1" >&2; exit 1; }
history() { curl -s -H @<(auth_header "$garden") "$garden/beds/2/history"; }
watered() { history | python3 -c 'import json,sys; h=json.load(sys.stdin); print(sum(1 for e in h if e["type"]=="CellWatered"))'; }

rm -rf "$out"; mkdir -p "$out"
(cd "$here/bespoke-1.0" && npm ci --no-audit --no-fund --silent && npx tsc)
(cd "$here/artie-dashboard" && ./gradlew installDist -q)
(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done

start_garden() {
  lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill 2> /dev/null || true
  sleep 2
  (cd "$root/server-spring" && service_env . && GARDEN_CHANGE=$1 ./gradlew bootRun --quiet > "$out/garden-$1.log" 2>&1) &
  pids+=($!)
  wait_for "$garden/"
}

attempt() {
  local change=$1 client=$2 typed=${3:-}
  "$root/server-spring/reset-db.sh" > /dev/null
  local before after line
  before=$(watered)
  if [ "$client" = script ]; then
    line=$( (cd "$here/artie-dashboard" && JAVA_HOME=$dashboard_java "$dashboard_java/bin/java" \
      -cp "build/install/artie-dashboard/lib/*" ArtieScriptKt) || true)
    line=$(printf '%s\n' "$line" | python3 -c '
import json,sys
lines=sys.stdin.read().splitlines()
calls=[dict(zip(("method","path","status"),l.split()[1:4])) for l in lines if l.startswith("CALL ")]
for c in calls: c["status"]=int(c["status"])
said=[l[7:] for l in lines if l.startswith("RESULT ")]
print(json.dumps({"client":"script","typed":None,"shown":None,"pressed":"act garden:water-cell {milliliters: 250}","said":said[0] if said else None,"calls":calls}))')
  else
    line=$(cd "$here/changes" && node drive.mjs "$client" $typed)
  fi
  after=$(watered)
  printf '%s\n' "$line" | python3 -c '
import json,sys
r=json.loads(sys.stdin.read()); r["change"]=sys.argv[1]; r["watered"]=int(sys.argv[3])>int(sys.argv[2])
r["refused"]=[c for c in r["calls"] if c["status"]>=400]
print(json.dumps(r))' "$change" "$before" "$after" >> "$out/attempts.jsonl"
  tail -1 "$out/attempts.jsonl" | jq -r '"   \(.client) \(if .typed then "(typed \(.typed) ml) " else "" end)watered=\(.watered) pressed=\(.pressed) refused=\([.refused[].status])"'
}

start_garden ""
(cd "$here/bespoke-1.0" && PORT=3001 node dist/dashboard.js > "$out/bespoke.log" 2>&1) &
pids+=($!)
(cd "$here/artie-dashboard" && JAVA_HOME=$dashboard_java build/install/artie-dashboard/bin/artie-dashboard > "$out/artie.log" 2>&1) &
pids+=($!)
wait_for http://localhost:3001/_calls
wait_for http://localhost:3002/_calls

for change in "${changes[@]}"; do
  echo "== $change =="
  start_garden "$change"
  curl -s "$garden/openapi.json" > "$out/openapi-$change.json"
  for client in bespoke artie script claude; do attempt "$change" "$client"; done
  [ "$change" = tighter-limit ] && attempt "$change" bespoke 800
  # What the bespoke team's compiler says after regenerating from this change's spec.
  work=$(mktemp -d)
  rsync -a --exclude node_modules --exclude dist "$here/bespoke-1.0/" "$work/bespoke/"
  mkdir -p "$work/openapi" && cp "$out/openapi-$change.json" "$work/openapi/garden.json"
  "$work/bespoke/generate.sh" garden.json
  ln -s "$here/bespoke-1.0/node_modules" "$work/bespoke/node_modules"
  (cd "$work/bespoke" && npx tsc > "$out/tsc-$change.txt" 2>&1) || true
  rm -rf "$work"
  echo "   tsc after regenerating: $(grep -c 'error TS' "$out/tsc-$change.txt" || true) error(s)"
done
echo "Attempts in $out/attempts.jsonl"
