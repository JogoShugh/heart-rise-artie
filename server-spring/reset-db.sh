#!/usr/bin/env bash
# Resets the running stack's Postgres to the seed state: bed 1 EMPTY,
# bed 2 PLANTED, bed 3 READY_TO_HARVEST, bed 4 PLANTED. Used by capture.sh and by
# scripts/live-client-check.sh. Run from anywhere; needs `docker compose up`.
set -euo pipefail
cd "$(dirname "$0")"
docker compose exec -T postgres psql -U artie -d gardenbed <<'SQL'
TRUNCATE heart_event, heart_snapshot, garden_bed RESTART IDENTITY CASCADE;

INSERT INTO garden_bed (id, label, width_ft, length_ft) VALUES
    (1, 'bed-1', 4, 8),
    (2, 'bed-2', 4, 8),
    (3, 'bed-3', 3, 6),
    (4, 'bed-4', 4, 12);

INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at) VALUES
    ('garden-bed', 2, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '5 days'),
    ('garden-bed', 2, 2, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '4 days'),
    ('garden-bed', 2, 3, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '2 days'),
    ('garden-bed', 3, 1, 'SeedSown', '{"seedType": "basil", "depthCm": 1}'::jsonb, now() - INTERVAL '12 days'),
    ('garden-bed', 3, 2, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '10 days'),
    ('garden-bed', 3, 3, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '7 days'),
    ('garden-bed', 4, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '3 days');

SELECT setval('garden_bed_id_seq', 4, true);
SQL
