#!/usr/bin/env bash
# Proves heart-mcp against the real garden server.
#   scripts/mcp-check.sh           runs every heart-mcp scenario (liveTest in mcp/),
#                                  through the official MCP client SDK over stdio.
#   scripts/mcp-check.sh --docker  builds the heart-mcp image and drives it as a host
#                                  would, over `docker run -i`: initialize, list the
#                                  tools, find bed 2 from the bookmark, water it.
# Starts its own garden server on 8080 (so it refuses if 8080 is taken), resets the
# seed data, and on exit stops that server and resets the data again.
# Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
base=http://localhost:8080
# Gradle 8.14 runs on JDK 21; the code itself builds for 22 through Gradle's toolchains.
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
mode=${1:-sdk}

if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null; then
  echo "Port 8080 is in use. This script starts and stops its own garden server;" >&2
  echo "stop whatever is on 8080 first (lsof -nP -iTCP:8080 -sTCP:LISTEN)." >&2
  exit 1
fi

(cd "$root/server-spring" && docker compose up -d postgres > /dev/null)
"$root/server-spring/reset-db.sh" > /dev/null

log=$root/server-spring/build/mcp-server.log
mkdir -p "$(dirname "$log")"
(cd "$root/server-spring" && ./gradlew bootRun --quiet > "$log" 2>&1) &
stop() {
  pkill -f 'server-spring.*bootRun' || true
  pkill -f 'com.artie.gardenbed' || true
  sleep 2
  "$root/server-spring/reset-db.sh" > /dev/null || true
}
trap stop EXIT

for _ in $(seq 1 90); do curl -sf -o /dev/null "$base/" && break; sleep 2; done
curl -sf -o /dev/null "$base/" || { echo "the garden server did not answer at $base; see $log" >&2; exit 1; }

if [ "$mode" != "--docker" ]; then
  (cd "$root/mcp" && ARTIE_BASE_URL="$base" ./gradlew :heart-mcp:liveTest --console=plain)
  exit
fi

docker build --quiet -f "$root/mcp/Dockerfile" -t heart-mcp "$root" > /dev/null
python3 - <<'EOF'
import json, subprocess, sys

host = subprocess.Popen(
    ["docker", "run", "-i", "--rm", "-e", "HEART_BOOKMARKS=garden=http://host.docker.internal:8080/", "heart-mcp"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
ids = iter(range(1, 100))

def send(method, params=None, notify=False):
    message = {"jsonrpc": "2.0", "method": method}
    if params is not None: message["params"] = params
    if not notify: message["id"] = next(ids)
    host.stdin.write(json.dumps(message) + "\n"); host.stdin.flush()
    if notify: return None
    while True:
        answer = json.loads(host.stdout.readline())
        if answer.get("id") == message["id"]: return answer["result"]

def call(tool, arguments):
    result = send("tools/call", {"name": tool, "arguments": arguments})
    if result.get("isError"): sys.exit(f"{tool} failed: {result['content']}")
    return result["structuredContent"]

def expect(what, actual, wanted):
    print(f"{'ok  ' if actual == wanted else 'FAIL'} {what}: {actual}")
    if actual != wanted: sys.exit(1)

send("initialize", {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "mcp-check", "version": "0"}})
send("notifications/initialized", notify=True)
tools = [t["name"] for t in send("tools/list")["tools"]]
expect("tools", tools, ["heart_look", "heart_act", "heart_follow", "heart_describe", "heart_explain", "heart_back"])
call("heart_look", {"service": "garden"})
call("heart_act", {"act": "garden:search-beds", "fields": {"name": "bed-2"}})
bed = call("heart_follow", {"rel": "item", "title": "bed-2"})
expect("bed 2 offers", [a["name"] for a in bed["acts"]], ["garden:water-cell"])
watered = call("heart_act", {"act": "garden:water-cell", "fields": {"milliliters": 250}})
expect("outcome", watered["outcome"]["kind"], "acted")
expect("moisture after 250 ml", watered["resource"]["state"]["moistureLevel"], 65)
host.stdin.close(); host.wait(timeout=30)
EOF
