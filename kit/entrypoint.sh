#!/bin/sh
# Starts the HEART gateway for GitHub on http://localhost:8392, then runs the command given, in /work.
# The gateway calls GitHub with each caller's own token: artie's, which is GITHUB_TOKEN. The token is never written.
set -eu

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "Set GITHUB_TOKEN: a fine-grained token for one scratch repository, with Contents, Issues and" >&2
  echo "Pull requests read and write." >&2
  exit 2
fi
export ARTIE_TOKEN_LOCALHOST_8392="$GITHUB_TOKEN"

mkdir -p /work/runs
for file in /kit/goals/*.goal.md /kit/scripts/*; do
  [ -e "/work/$(basename "$file")" ] || cp "$file" /work/
done

(
  cd /opt/heart-gateway
  UPSTREAM_URL=https://api.github.com \
  UPSTREAM_SPEC=/kit/github/api.github.com.json \
  UPSTREAM_AUTH=caller \
  GATEWAY_CONNECTOR=/opt/heart-gateway/connectors/github \
  PORT=8392 \
    exec bin/heart-gateway > /work/runs/gateway.log 2>&1
) &

# Two more systems, simulated, each a HEART service of its own: a narrow Jenkins and a narrow cloud deploy service.
# They hold their state in memory, so each run of the kit starts them afresh. Their tokens are test tokens.
for service in "8401 build.test build" "8402 deploy.test deploy"; do
  set -- $service "$@"
  (cd /opt/heart-sims && exec java -cp 'lib/*' com.artie.heart.sims.MainKt "$1" "$2" > "/work/runs/$3.log" 2>&1) &
  shift 3
done
export ARTIE_TOKEN_LOCALHOST_8401=build-token ARTIE_TOKEN_LOCALHOST_8402=deploy-token

# The workspace: one resource linking each system's entry point, so one goal can start from one address.
(exec java -m jdk.httpserver -b 127.0.0.1 -p 8400 -d /kit/workspace > /work/runs/workspace.log 2>&1) &

up() {
  tries=0
  until curl -s -o /dev/null "$1"; do
    tries=$((tries + 1))
    if [ "$tries" -gt 90 ]; then
      echo "$2 didn't start; its log is work/runs/$3.log." >&2
      exit 1
    fi
    sleep 1
  done
}
up http://localhost:8392/ "The gateway" gateway
up http://localhost:8401/ "The build service" build
up http://localhost:8402/ "The deploy service" deploy
up http://localhost:8400/workspace.json "The workspace" workspace

# The deploy service's app "web": where staging and production may deploy, and its releases, one of them failed.
sim=http://localhost:8402/_sim/deploy/apps/web
curl -s -X PUT "$sim/regions" -d '{"staging": [{"name": "us-east", "label": "US East", "available": true},
  {"name": "eu-west", "label": "EU West", "available": false}],
  "production": [{"name": "us-east", "label": "US East", "available": true}]}'
for release in '1.0 succeeded' '1.1 succeeded' '1.2 failed'; do
  set -- $release "$@"
  curl -s -X POST "$sim/releases" -d "{\"version\": \"$1\", \"status\": \"$2\"}"
  shift 2
done

cd /work
exec "$@"
