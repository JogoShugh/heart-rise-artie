#!/usr/bin/env bash
# From OpenAPI to HEART, run for real: Monday, the frost, and Tuesday, against
# one garden server, seen side by side as plain JSON (what an OpenAPI client
# gets) and as HEART (HAL with forms). See README.md.
#
#   exhibits/openapi-vs-heart/scenario.sh
#
# Starts Postgres (docker compose), seeds the garden (server-spring/reset-db.sh:
# bed 2 is planted with tomato, moisture 40), and starts the garden twice:
# Monday without frost, then Tuesday with GARDEN_FROST=true. Every response is
# also saved under run/, so a demo can show the files.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
base=http://localhost:8080
out=$here/run
mkdir -p "$out"

for tool in docker curl jq; do command -v $tool > /dev/null || { echo "$tool is needed" >&2; exit 1; }; done
if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null 2>&1; then
  echo "Port 8080 is in use. This script starts and stops its own garden; stop that first." >&2
  exit 1
fi

# shellcheck source=../../scripts/lib/credentials.sh
. "$root/scripts/lib/credentials.sh"
"$root/scripts/dev-credentials.sh" > /dev/null

server=
stop_server() {
  [ -n "$server" ] && kill "$server" 2> /dev/null || true
  lsof -ti tcp:8080 -sTCP:LISTEN 2> /dev/null | xargs kill 2> /dev/null || true
  server=
  sleep 2
}
trap stop_server EXIT

start_garden() {
  local frost=$1
  (cd "$root/server-spring" && service_env . && GARDEN_FROST=$frost ./gradlew bootRun --quiet \
    > "$out/server-frost-$frost.log" 2>&1) &
  server=$!
  for _ in $(seq 1 120); do answering "$base/" && return; sleep 1; done
  echo "The garden did not start; see $out/server-frost-$frost.log" >&2
  exit 1
}

# curl with the garden's credential, read from ARTIE's credentials file, never on the command line.
call() { curl -s -H @<(auth_header "$base") "$@"; }
plain() { call -H 'Accept: application/json' "$@"; }
heart() { call -H 'Accept: application/hal+json' "$@"; }

title() { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
side_by_side() {
  local name=$1
  plain "$base/beds/2" | jq . > "$out/$name-plain.json"
  heart "$base/beds/2" | jq . > "$out/$name-heart.json"
  echo "-- plain JSON (the OpenAPI client's view) --"
  cat "$out/$name-plain.json"
  echo "-- HEART (the same bed, with its controls) --"
  jq '{status, cropName, moistureLevel, forms: (._forms | keys), unavailable: ._links["hr:unavailable"].href}' \
    "$out/$name-heart.json"
}

(cd "$root/server-spring" && docker compose up -d postgres > /dev/null 2>&1)
for _ in $(seq 1 30); do (cd "$root/server-spring" && docker compose exec -T postgres pg_isready -q) && break; sleep 1; done

title "Monday: the garden, without frost"
start_garden false
"$root/server-spring/reset-db.sh" > /dev/null

title "One address to start from: / links its OpenAPI description (service-desc)"
heart "$base/" | jq '._links["service-desc"]'
curl -s "$base/openapi.json" | jq . > "$out/monday-openapi.json"
jq '{version: .info.version, operations: [.paths | to_entries[] | .value | to_entries[] | .value.operationId],
     status: .components.schemas.Bed.properties.status.enum,
     waterRule: (.paths["/beds/{id}/actions/water-cell"].post.description | split("\n\n") | last)}' \
  "$out/monday-openapi.json"

title "Monday: bed 2, two ways"
side_by_side monday

stop_server
title "Overnight the garden team ships the frost (GARDEN_FROST=true). Only the server changes."
start_garden true
curl -s "$base/openapi.json" | jq . > "$out/tuesday-openapi.json"
echo "-- the description's own diff, Monday to Tuesday --"
diff <(jq -S . "$out/monday-openapi.json") <(jq -S . "$out/tuesday-openapi.json") | grep '^[<>]' | head -20 || true

title "The frost hits (the operator's POST /actuator/frost)"
call -X POST "$base/actuator/frost" | jq .

title "Tuesday: bed 2, two ways"
side_by_side tuesday
echo "-- why can't I water bed 2? (HEART's hr:unavailable, fetched only when asked) --"
call "$base$(jq -r '._links["hr:unavailable"].href' "$out/tuesday-heart.json")" | jq '.unavailable[] | {title, reason}'

title "The Monday dashboard still shows Water, and presses it (plain JSON, no ETag)"
plain -o "$out/tuesday-plain-water.json" -w 'HTTP %{http_code}\n' -X POST -H 'Content-Type: application/json' \
  -d '{"milliliters": 250}' "$base/beds/2/actions/water-cell"
jq '{status, title, detail}' "$out/tuesday-plain-water.json"

title "A HEART client never offers Water; it offers Thaw, which arrived in the response"
etag=$(heart -D - -o /dev/null "$base/beds/2" | tr -d '\r' | sed -n 's/^[Ee][Tt]ag: //p')
target=$(jq -r '._forms["garden:thaw"]._links.target.href' "$out/tuesday-heart.json")
heart -o "$out/tuesday-heart-thaw.json" -w 'HTTP %{http_code}\n' -X POST -H 'Content-Type: application/json' \
  -H "If-Match: $etag" -d '{}' "$base$target"
jq '{status, cropName, forms: (._forms | keys)}' "$out/tuesday-heart-thaw.json"

title "Done. Every response is in $out"
