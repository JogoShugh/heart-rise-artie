#!/usr/bin/env bash
# demo.sh
#
# End-to-end: bring up Postgres + the Spring backend, capture a real HTTP
# walkthrough against it, and render the result into journal.html.
#
# Three existing pieces, wired together in the order the README describes:
#   docker compose up --build -d   -- builds & starts postgres + app
#   ./capture.sh                    -- resets the DB, captures run/ from a live instance
#   python3 render_journal.py       -- reads run/, writes journal.html
#
# docker-compose.yml and Dockerfile are UNVERIFIED as of this writing (see
# their own header comments) -- this script is likely the first thing to
# actually exercise them end to end, so don't be surprised if the first run
# surfaces something.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

BASE="http://localhost:8080"

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon isn't running -- start Docker Desktop, then re-run this script." >&2
  exit 1
fi

echo "== building and starting postgres + app =="
docker compose up --build -d

echo
echo "== waiting for the app to answer on $BASE =="
ATTEMPTS=60
until curl -sf -o /dev/null "$BASE/beds/1" || [ "$ATTEMPTS" -eq 0 ]; do
  ATTEMPTS=$((ATTEMPTS - 1))
  sleep 2
done

if [ "$ATTEMPTS" -eq 0 ]; then
  echo "App never came up on $BASE after ~2 minutes. Check its logs:" >&2
  echo "  docker compose logs app" >&2
  exit 1
fi
echo "app is up"

echo
echo "== capturing a real HTTP walkthrough into run/ =="
./capture.sh

echo
echo "== rendering journal.html from run/ =="
python3 render_journal.py

echo
echo "== done =="
echo "journal.html written to $ROOT/journal.html"
if command -v open >/dev/null 2>&1; then
  open journal.html
fi
