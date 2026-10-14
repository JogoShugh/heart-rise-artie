#!/usr/bin/env bash
# Runs a Buster campaign on one HEART server and exits non-zero if the gate fails.
#   scripts/buster-campaign.sh [domain]     garden (default), basketball, ...
# The domain's campaign.env (server-spring/ for the garden, domains/<name>/ for
# the others) says where its server is, its ports, its reset command, its
# explore samples, and which client drives it.
#   1. Starts a fresh server with Buster in front (-Pbuster=on, buster profile).
#   2. Record: a dry run of the client against the unmutated server.
#   3. Plan: Buster lists the (client, request, plugin) combinations the clients exercise,
#      in rounds.
#   4. For each round: arm it, and run the same client again.
#   5. Reads Buster's report. The gate is campaign.ok: no FAIL, and every plugin exercised.
# The client is "live" (the garden's own @live scenarios) or "explore" (ARTIE's
# explorer, the same for every domain); CAMPAIGN_CLIENT overrides campaign.env.
# See Buster's README, "Campaigns: aiming each mutation". Every verdict is pass,
# pass (attested) or FAIL; nothing is left undecided.
# Writes campaign-plan.json, campaign-report.json, campaign-round-<k>.log and
# campaign-server.log under the server's build/. Stops the server at the end unless
# BUSTER_KEEP=1. Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
domain=${1:-garden}
if [ "$domain" = garden ]; then config="$root/server-spring/campaign.env"; else config="$root/domains/$domain/campaign.env"; fi
[ -f "$config" ] || { echo "no campaign.env for $domain ($config)" >&2; exit 1; }
# shellcheck source=/dev/null
. "$config"
client=${CAMPAIGN_CLIENT:-$CLIENT}
server="$root/$SERVER_DIR"
base="http://localhost:$PORT"
build="$server/build"
events="$build/artie-events.jsonl"
# Gradle 8.13 cannot run on JDK 25, which may be the shell default.
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
mkdir -p "$build"
# shellcheck source=lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null

# A fresh server, so verdicts from another run cannot mix into this one.
if lsof -ti "tcp:$PORT" > /dev/null; then
  echo "port $PORT is busy; stop that server first (lsof -ti tcp:$PORT | xargs kill)" >&2
  exit 1
fi
(cd "$root/server-spring" && docker compose up -d postgres > /dev/null)

stop_server() {
  if [ "${BUSTER_KEEP:-0}" != 1 ]; then
    lsof -ti "tcp:$PORT" | xargs kill 2> /dev/null || true
  fi
}
trap stop_server EXIT

(cd "$server" && service_env "$server" && SPRING_PROFILES_ACTIVE=buster BUSTER_PACE=0s \
  BUSTER_PLUGINS="$root/buster/plugins" BUSTER_LOG="$build/buster.jsonl" BUSTER_CLIENT_EVENTS="$events" \
  ./gradlew bootRun -Pbuster=on > "$build/campaign-server.log" 2>&1) &
buster() { curl -sf -H @<(auth_header "$base") "$@"; }
for _ in $(seq 1 90); do buster -o /dev/null "$base/_buster/report" && break; sleep 2; done
buster -o /dev/null "$base/_buster/report" || {
  echo "Buster server did not start; see $build/campaign-server.log" >&2
  exit 1
}

post() { buster -X POST "$base/_buster/campaign/$1"; echo; }

# One run of the client: from the seed data, with ARTIE's event log fresh.
run_client() {
  case "$client" in
    live)
      ARTIE_BASE_URL="$base" ARTIE_EVENTS="$events" "$root/scripts/live-client-check.sh"
      ;;
    explore)
      "$root/$RESET" > /dev/null
      : > "$events"
      (cd "$root/client" && ARTIE_BASE_URL="$base" ARTIE_COAP_PORT="$COAP_PORT" ARTIE_RESET="$root/$RESET" \
        ARTIE_SAMPLES="$root/$SAMPLES" ARTIE_EVENTS="$events" ./gradlew exploreTest --console=plain)
      ;;
    *)
      echo "unknown client $client" >&2
      return 1
      ;;
  esac
}

# Record. A broken dry run would make a wrong plan, so it must pass.
post record
if ! run_client > "$build/campaign-round-0.log" 2>&1; then
  echo "dry run failed; see $build/campaign-round-0.log" >&2
  exit 1
fi

# Plan.
buster "$base/_buster/campaign/plan" > "$build/campaign-plan.json"
rounds=$(python3 - "$build/campaign-plan.json" <<'EOF'
import json, sys
plan = json.load(open(sys.argv[1]))
combos = sum(len(r["combinations"]) for r in plan["rounds"])
print(f"plan: {len(plan['rounds'])} rounds, {combos} combinations", file=sys.stderr)
for plugin, n in sorted(plan["coverage"].items()):
    print(f"  {plugin}: {n}", file=sys.stderr)
if plan["holes"]:
    print(f"  holes: {', '.join(plan['holes'])}", file=sys.stderr)
print(len(plan["rounds"]))
EOF
)

# Rounds. Client tests fail by design under mutation; the verdicts are what count.
for k in $(seq 1 "$rounds"); do
  echo "round $k/$rounds: $(post "arm?round=$k")"
  run_client > "$build/campaign-round-$k.log" 2>&1 || true
done

# Close the last round's windows, then read the gate.
post off > /dev/null
buster "$base/_buster/report" > "$build/campaign-report.json"
python3 - "$build/campaign-report.json" <<'EOF'
import collections, json, sys
report = json.load(open(sys.argv[1]))
campaign = report["campaign"]
counts = collections.Counter(v["result"] for v in report["verdicts"])
print("verdicts: " + ", ".join(f"{n} {result}" for result, n in sorted(counts.items())))
for plugin, n in sorted(campaign["coverage"].items()):
    print(f"  {plugin}: {n} combinations")
for v in report["verdicts"]:
    if v["result"] not in ("pass", "pass (attested)"):
        print(f"  {v['result']} #{v['seq']} {v['plugin']} {v['client']}: {v['reason']}")
if campaign["holes"]:
    print("holes: " + ", ".join(campaign["holes"]))
print("gate: " + ("ok" if campaign["ok"] else "FAILED"))
sys.exit(0 if campaign["ok"] else 1)
EOF
