#!/usr/bin/env bash
# Resets the tolls database to its seed: account 1, Pat Miller, open with two tags and a few trips; account 2,
# Jordan Lee, new and not yet funded; account 3, Casey Ortiz, suspended below zero. Needs server-spring's Postgres
# up (docker compose up -d postgres).
set -euo pipefail
cd "$(dirname "$0")/../../server-spring"
# The database is created once; the server's migrations then create its tables.
if ! docker compose exec -T postgres psql -U artie -d gardenbed -tAc "SELECT 1 FROM pg_database WHERE datname = 'tolls'" | grep -q 1; then
  docker compose exec -T postgres psql -U artie -d gardenbed -c "CREATE DATABASE tolls"
  echo "Created the tolls database. Start the server once to migrate it, then run this again." >&2
  exit 0
fi
docker compose exec -T postgres psql -U artie -d tolls <<'SQL'
TRUNCATE heart_event, heart_snapshot, account RESTART IDENTITY CASCADE;

INSERT INTO account (id, holder) VALUES (1, 'Pat Miller'), (2, 'Jordan Lee'), (3, 'Casey Ortiz');

INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at) VALUES
    ('account', 1, 1, 'FundsAdded', '{"cents": 4000}', now() - INTERVAL '9 days'),
    ('account', 1, 2, 'TagAdded', '{"tag": "T-1001"}', now() - INTERVAL '9 days'),
    ('account', 1, 3, 'TagAdded', '{"tag": "T-1002"}', now() - INTERVAL '9 days'),
    ('account', 1, 4, 'TripCharged', '{"tag": "T-1001", "plaza": "harbor-bridge", "vehicleClass": "car", "time": "peak", "fareCents": 700}', now() - INTERVAL '6 days'),
    ('account', 1, 5, 'TripCharged', '{"tag": "T-1002", "plaza": "turnpike-exit-9", "vehicleClass": "car", "time": "off-peak", "fareCents": 260}', now() - INTERVAL '3 days'),
    ('account', 3, 1, 'FundsAdded', '{"cents": 2500}', now() - INTERVAL '20 days'),
    ('account', 3, 2, 'TagAdded', '{"tag": "T-3001"}', now() - INTERVAL '20 days'),
    ('account', 3, 3, 'TripCharged', '{"tag": "T-3001", "plaza": "north-tunnel", "vehicleClass": "five-axle-truck", "time": "peak", "fareCents": 3250}', now() - INTERVAL '2 days');

SELECT setval('account_id_seq', 3, true);
SQL
