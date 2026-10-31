#!/usr/bin/env bash
# Resets the basketball database to its seed: game 1, the Eagles at home to the
# Marauders, scheduled; game 2, the Hornets at home to the Comets, live in
# period 2. Needs server-spring's Postgres up (docker compose up -d postgres).
set -euo pipefail
cd "$(dirname "$0")/../../server-spring"
# The database is created once; the server's migrations then create its tables.
if ! docker compose exec -T postgres psql -U artie -d gardenbed -tAc "SELECT 1 FROM pg_database WHERE datname = 'basketball'" | grep -q 1; then
  docker compose exec -T postgres psql -U artie -d gardenbed -c "CREATE DATABASE basketball"
  echo "Created the basketball database. Start the server once to migrate it, then run this again." >&2
  exit 0
fi
docker compose exec -T postgres psql -U artie -d basketball <<'SQL'
TRUNCATE heart_event, heart_snapshot, player, game RESTART IDENTITY CASCADE;

INSERT INTO game (id, label, home_name, away_name) VALUES
    (1, 'Eagles v Marauders', 'Eagles', 'Marauders'),
    (2, 'Hornets v Comets', 'Hornets', 'Comets');

INSERT INTO player (game_id, side, number, name) VALUES
    (1, 'home', 4, 'Avery'), (1, 'home', 11, 'Jordan'), (1, 'home', 23, 'Riley'),
    (1, 'home', 30, 'Sam'), (1, 'home', 32, 'Casey'),
    (1, 'away', 3, 'Taylor'), (1, 'away', 7, 'Morgan'), (1, 'away', 15, 'Quinn'),
    (1, 'away', 21, 'Drew'), (1, 'away', 34, 'Jamie'),
    (2, 'home', 5, 'Kai'), (2, 'home', 8, 'Rowan'), (2, 'home', 12, 'Ellis'),
    (2, 'home', 24, 'Harper'), (2, 'home', 33, 'Finley'),
    (2, 'away', 2, 'Logan'), (2, 'away', 9, 'Parker'), (2, 'away', 14, 'Reese'),
    (2, 'away', 22, 'Skyler'), (2, 'away', 31, 'Emerson');

INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at) VALUES
    ('game', 2, 1, 'TippedOff', '{"possession": "home"}'::jsonb, now() - INTERVAL '30 minutes'),
    ('game', 2, 2, 'BasketScored', '{"team": "home", "playerNumber": 24, "points": 2}'::jsonb, now() - INTERVAL '28 minutes'),
    ('game', 2, 3, 'BasketScored', '{"team": "away", "playerNumber": 9, "points": 3}'::jsonb, now() - INTERVAL '25 minutes'),
    ('game', 2, 4, 'FoulRecorded', '{"team": "home", "playerNumber": 8, "kind": "personal"}'::jsonb, now() - INTERVAL '22 minutes'),
    ('game', 2, 5, 'PeriodEnded', '{}'::jsonb, now() - INTERVAL '18 minutes'),
    ('game', 2, 6, 'PeriodStarted', '{}'::jsonb, now() - INTERVAL '15 minutes'),
    ('game', 2, 7, 'BasketScored', '{"team": "away", "playerNumber": 22, "points": 2}'::jsonb, now() - INTERVAL '10 minutes');

SELECT setval('game_id_seq', 2, true);
SQL
