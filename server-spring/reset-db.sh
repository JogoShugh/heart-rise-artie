#!/usr/bin/env bash
# Resets the running stack's Postgres to the seed state: bed 1 EMPTY,
# bed 2 PLANTED, bed 3 READY_TO_HARVEST, bed 4 PLANTED. Used by capture.sh and by
# scripts/live-client-check.sh. Run from anywhere; needs `docker compose up`.
set -euo pipefail
cd "$(dirname "$0")"
docker compose exec -T postgres psql -U artie -d gardenbed <<'SQL'
TRUNCATE garden_bed_event, garden_bed_snapshot, garden_bed RESTART IDENTITY CASCADE;

INSERT INTO garden_bed (id, label) VALUES
    (1, 'bed-1'),
    (2, 'bed-2'),
    (3, 'bed-3'),
    (4, 'bed-4');

INSERT INTO garden_bed_event (garden_bed_id, sequence_no, event_type, payload, occurred_at) VALUES
    (2, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '5 days'),
    (2, 2, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '4 days'),
    (2, 3, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '2 days'),
    (3, 1, 'SeedSown', '{"seedType": "basil", "depthCm": 1}'::jsonb, now() - INTERVAL '12 days'),
    (3, 2, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '10 days'),
    (3, 3, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '7 days'),
    (4, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '3 days');

SELECT setval('garden_bed_id_seq', 4, true);
SQL
