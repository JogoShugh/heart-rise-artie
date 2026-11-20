#!/usr/bin/env bash
# Records fixtures/garden/ from a real garden, for the offline run the gate uses.
# Run it by hand when the garden's representations change. It leaves any garden
# on 8080 alone: it starts its own garden and its own Postgres on other ports,
# with a throwaway token, and removes them afterwards.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
export JAVA_HOME=${ARTIE_JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.12-graal}
db=artie-conformance-record
port=18080
GARDEN_TOKEN=$(openssl rand -hex 32)
stop() {
  lsof -ti "tcp:$port" -sTCP:LISTEN | xargs kill 2> /dev/null || true
  docker rm -f "$db" > /dev/null 2>&1 || true
  rm -f "$reset"
}
reset=$(mktemp)
trap stop EXIT
sed -n "/<<'SQL'/,/^SQL$/p" "$root/server-spring/reset-db.sh" | sed '1d;$d' > "$reset.sql"
printf '#!/usr/bin/env bash\ndocker exec -i %s psql -q -U artie -d gardenbed < %s\n' "$db" "$reset.sql" > "$reset"
chmod +x "$reset"
docker run -d --name "$db" -e POSTGRES_DB=gardenbed -e POSTGRES_USER=artie -e POSTGRES_PASSWORD=artie \
  -p 55432:5432 postgres:16-alpine > /dev/null
until docker exec "$db" pg_isready -U artie -d gardenbed > /dev/null 2>&1; do sleep 1; done
mkdir -p "$root/server-spring/build"
(cd "$root/server-spring" && DB_PORT=55432 SERVER_PORT=$port HEART_COAP_PORT=15683 \
  HEART_AUTH_BEARER_TOKENS=$(printf %s "$GARDEN_TOKEN" | shasum -a 256 | cut -d' ' -f1) \
  ./gradlew bootRun --quiet > build/conformance-record.log 2>&1) &
for _ in $(seq 1 90); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/")" != "000" ] && break
  sleep 2
done
curl -s -o /dev/null "http://localhost:$port/" || { echo "The garden did not start." >&2; exit 1; }
(cd "$root/implementations/heart-ts" && npx tsc)
rm -rf "$here/fixtures/garden"
export GARDEN_TOKEN GARDEN_URL=http://localhost:$port GARDEN_RESET=$reset GARDEN_RECORD=1
(cd "$here" && npx cucumber-js)
