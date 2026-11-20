#!/usr/bin/env bash
# Resets the tickets database to its seed: ticket 1, "Cannot log in", new;
# ticket 2, "Invoice shows the wrong total", assigned to marco and waiting on
# the customer. Needs server-spring's Postgres up (docker compose up -d postgres).
set -euo pipefail
cd "$(dirname "$0")/../../server-spring"
# The database is created once; the server's migrations then create its tables.
if ! docker compose exec -T postgres psql -U artie -d gardenbed -tAc "SELECT 1 FROM pg_database WHERE datname = 'tickets'" | grep -q 1; then
  docker compose exec -T postgres psql -U artie -d gardenbed -c "CREATE DATABASE tickets"
  echo "Created the tickets database. Start the server once to migrate it, then run this again." >&2
  exit 0
fi
docker compose exec -T postgres psql -U artie -d tickets <<'SQL'
TRUNCATE heart_event, heart_snapshot, ticket RESTART IDENTITY CASCADE;

INSERT INTO ticket (id, subject) VALUES
    (1, 'Cannot log in'),
    (2, 'Invoice shows the wrong total');

INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at) VALUES
    ('ticket', 1, 1, 'Opened', '{"subject": "Cannot log in", "customer": "Dana Reyes", "description": "The login button does nothing.", "priority": "high"}'::jsonb, now() - INTERVAL '2 hours'),
    ('ticket', 2, 1, 'Opened', '{"subject": "Invoice shows the wrong total", "customer": "Sam Okafor", "description": "October invoice is 40 too high.", "priority": "normal"}'::jsonb, now() - INTERVAL '1 day'),
    ('ticket', 2, 2, 'Assigned', '{"agent": "marco"}'::jsonb, now() - INTERVAL '23 hours'),
    ('ticket', 2, 3, 'Commented', '{"text": "Tax applied twice?", "internal": true}'::jsonb, now() - INTERVAL '22 hours'),
    ('ticket', 2, 4, 'CustomerAsked', '{"question": "Could you send the invoice number?"}'::jsonb, now() - INTERVAL '21 hours');

SELECT setval('ticket_id_seq', 2, true);
SQL
