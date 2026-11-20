#!/usr/bin/env bash
# Proves every credential source and both schemes against the real services.
#   1. Makes credentials for this run only: for the garden and for basketball,
#      one Bearer token and one Basic user, each admitted by its service.
#   2. Starts the garden (8080) and basketball (8181) with those credentials, and a
#      Vault dev server (127.0.0.1:8209) holding the garden's token and basketball's
#      user. Vault runs with its own home folder, so ~/.vault-token is not touched.
#   3. Runs the @auth-live scenarios (authLiveTest in client/): each source alone,
#      each scheme, and the refusals. The credentials reach the tests in ARTIE_CHECK_*
#      variables; ~/.artie is not read.
#   4. Stops what it started, resets both databases, and removes the run's files.
# Refuses to start if 8080, 8181 or 8209 is taken. Never prints a credential.
# Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
# Gradle 8.13 cannot run on JDK 25, which may be the shell default.
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
vault_port=8209

for port in 8080 8181 $vault_port; do
  if lsof -nP -iTCP:$port -sTCP:LISTEN > /dev/null; then
    echo "Port $port is in use. This script starts and stops its own servers; stop that first." >&2
    exit 1
  fi
done
for tool in vault htpasswd openssl; do
  command -v $tool > /dev/null || { echo "$tool is needed" >&2; exit 1; }
done

# shellcheck source=lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
# docker compose reads the garden's .env, even to start Postgres alone.
"$root/scripts/dev-credentials.sh" > /dev/null

work=$(mktemp -d)
chmod 700 "$work"
pids=()
stop() {
  for port in 8080 8181; do lsof -ti "tcp:$port" -sTCP:LISTEN | xargs kill 2> /dev/null || true; done
  for pid in "${pids[@]}"; do kill "$pid" 2> /dev/null || true; done
  sleep 2
  "$root/server-spring/reset-db.sh" > /dev/null 2>&1 || true
  "$root/domains/basketball/reset-db.sh" > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap stop EXIT

random() { openssl rand -hex 24; }
sha256() { printf %s "$1" | shasum -a 256 | cut -d' ' -f1; }
# The password goes to htpasswd on stdin, never on its command line.
bcrypt() { printf %s "$2" | htpasswd -niBC 10 "$1" | cut -d: -f2; }

export ARTIE_CHECK_GARDEN_TOKEN ARTIE_CHECK_GARDEN_USER ARTIE_CHECK_GARDEN_PASSWORD
export ARTIE_CHECK_BASKETBALL_TOKEN ARTIE_CHECK_BASKETBALL_USER ARTIE_CHECK_BASKETBALL_PASSWORD
ARTIE_CHECK_GARDEN_TOKEN=$(random)
ARTIE_CHECK_GARDEN_USER=garden-check
ARTIE_CHECK_GARDEN_PASSWORD=$(random)
ARTIE_CHECK_BASKETBALL_TOKEN=$(random)
ARTIE_CHECK_BASKETBALL_USER=basketball-check
ARTIE_CHECK_BASKETBALL_PASSWORD=$(random)

(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done
# Creates the basketball database if this Postgres has none yet; the server migrates it.
"$root/domains/basketball/reset-db.sh" > /dev/null 2>&1 || true

start() {
  local dir=$1 token=$2 user=$3 password=$4 log=$5
  (cd "$root/$dir" \
    && HEART_AUTH_BEARER_TOKENS=$(sha256 "$token") \
       HEART_AUTH_BASIC_USERS="$user:$(bcrypt "$user" "$password")" \
       ./gradlew bootRun --quiet > "$log" 2>&1) &
}
start server-spring "$ARTIE_CHECK_GARDEN_TOKEN" "$ARTIE_CHECK_GARDEN_USER" "$ARTIE_CHECK_GARDEN_PASSWORD" \
  "$root/server-spring/build/auth-check-server.log"
start domains/basketball "$ARTIE_CHECK_BASKETBALL_TOKEN" "$ARTIE_CHECK_BASKETBALL_USER" \
  "$ARTIE_CHECK_BASKETBALL_PASSWORD" "$root/domains/basketball/build/auth-check-server.log"

# Vault in dev mode: in memory, its own root token, written to its log in the run's folder.
HOME=$work vault server -dev -dev-listen-address="127.0.0.1:$vault_port" > "$work/vault.log" 2>&1 &
pids+=($!)
export ARTIE_CHECK_VAULT_ADDR=http://127.0.0.1:$vault_port ARTIE_CHECK_VAULT_TOKEN
for _ in $(seq 1 30); do grep -q '^Root Token:' "$work/vault.log" && answering "$ARTIE_CHECK_VAULT_ADDR/v1/sys/health" && break; sleep 1; done
ARTIE_CHECK_VAULT_TOKEN=$(sed -n 's/^Root Token: //p' "$work/vault.log")
[ -n "$ARTIE_CHECK_VAULT_TOKEN" ] || { echo "Vault did not start; see $work/vault.log" >&2; exit 1; }

# The garden's token and basketball's user, in Vault's key/value store (v2), at artie/<host>_<port>.
python3 -I - <<'EOF'
import json, os, urllib.request
env = os.environ
def put(path, data):
    request = urllib.request.Request(
        f"{env['ARTIE_CHECK_VAULT_ADDR']}/v1/secret/data/artie/{path}",
        data=json.dumps({"data": data}).encode(), method="POST",
        headers={"X-Vault-Token": env["ARTIE_CHECK_VAULT_TOKEN"], "Content-Type": "application/json"})
    urllib.request.urlopen(request).read()
put("localhost_8080", {"bearer": env["ARTIE_CHECK_GARDEN_TOKEN"]})
put("localhost_8181", {"user": env["ARTIE_CHECK_BASKETBALL_USER"], "password": env["ARTIE_CHECK_BASKETBALL_PASSWORD"]})
print("Vault holds the garden's token and basketball's user")
EOF

for base in http://localhost:8080 http://localhost:8181; do
  for _ in $(seq 1 90); do answering "$base/" && break; sleep 2; done
  answering "$base/" || { echo "no service answered at $base; see its build/auth-check-server.log" >&2; exit 1; }
done
"$root/server-spring/reset-db.sh" > /dev/null

(cd "$root/client" && ./gradlew authLiveTest --rerun --console=plain)
