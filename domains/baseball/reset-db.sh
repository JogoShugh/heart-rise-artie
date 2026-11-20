#!/usr/bin/env bash
# Resets the baseball database to its seed: game 1, the Cubs at home to the Bears, scheduled; game 2, the Tigers
# at home to the Owls, in the bottom of the second. Needs server-spring's Postgres up (docker compose up -d postgres).
set -euo pipefail
cd "$(dirname "$0")/../../server-spring"
# The database is created once; the server's migrations then create its tables.
if ! docker compose exec -T postgres psql -U artie -d gardenbed -tAc "SELECT 1 FROM pg_database WHERE datname = 'baseball'" | grep -q 1; then
  docker compose exec -T postgres psql -U artie -d gardenbed -c "CREATE DATABASE baseball"
  echo "Created the baseball database. Start the server once to migrate it, then run this again." >&2
  exit 0
fi
docker compose exec -T postgres psql -U artie -d baseball <<'SQL'
TRUNCATE heart_event, heart_snapshot, player, game RESTART IDENTITY CASCADE;

INSERT INTO game (id, label, home_name, away_name) VALUES
    (1, 'Cubs v Bears', 'Cubs', 'Bears'),
    (2, 'Tigers v Owls', 'Tigers', 'Owls');

-- Each team in batting order: the whole team bats.
INSERT INTO player (game_id, side, batting_order, number, name) VALUES
    (1, 'home', 1, 1, 'Avery'), (1, 'home', 2, 3, 'Jordan'), (1, 'home', 3, 4, 'Riley'), (1, 'home', 4, 6, 'Sam'),
    (1, 'home', 5, 7, 'Casey'), (1, 'home', 6, 9, 'Logan'), (1, 'home', 7, 11, 'Parker'), (1, 'home', 8, 13, 'Reese'),
    (1, 'away', 1, 2, 'Taylor'), (1, 'away', 2, 5, 'Morgan'), (1, 'away', 3, 8, 'Quinn'), (1, 'away', 4, 10, 'Drew'),
    (1, 'away', 5, 12, 'Jamie'), (1, 'away', 6, 14, 'Miller'), (1, 'away', 7, 17, 'Harper'), (1, 'away', 8, 21, 'Ellis'),
    (2, 'home', 1, 3, 'Kai'), (2, 'home', 2, 8, 'Rowan'), (2, 'home', 3, 12, 'Finley'), (2, 'home', 4, 15, 'Harper'),
    (2, 'home', 5, 19, 'Emerson'), (2, 'home', 6, 22, 'Skyler'), (2, 'home', 7, 27, 'Peyton'),
    (2, 'away', 1, 4, 'Blake'), (2, 'away', 2, 6, 'Cameron'), (2, 'away', 3, 9, 'Dakota'), (2, 'away', 4, 11, 'Hayden'),
    (2, 'away', 5, 16, 'Jesse'), (2, 'away', 6, 20, 'Kendall'), (2, 'away', 7, 24, 'Marley');

-- Game 2: the Owls score two in the top of the first; the Tigers one in the bottom; the Owls go down in order
-- in the second; the Tigers have a runner on first.
INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at)
SELECT 'game', 2, row_number() OVER (), type, payload::jsonb, now() - (40 - row_number() OVER ()) * INTERVAL '1 minute'
FROM (VALUES
    ('PlayBall', '{}'), ('Hit', '{"result": "single"}'), ('Hit', '{"result": "home-run"}'),
    ('Hit', '{"result": "fielded-out"}'), ('Pitched', '{"result": "missed"}'), ('Hit', '{"result": "fielded-out"}'),
    ('Hit', '{"result": "fielded-out"}'), ('NextHalf', '{}'),
    ('Hit', '{"result": "double"}'), ('Hit', '{"result": "double"}'), ('Hit', '{"result": "fielded-out"}'),
    ('Hit', '{"result": "fielded-out"}'), ('Hit', '{"result": "fielded-out"}'), ('NextHalf', '{}'),
    ('Hit', '{"result": "fielded-out"}'), ('Hit', '{"result": "fielded-out"}'), ('Hit', '{"result": "fielded-out"}'),
    ('NextHalf', '{}'), ('Pitched', '{"result": "foul"}'), ('Hit', '{"result": "single"}')
) AS plays(type, payload);

SELECT setval('game_id_seq', 2, true);
SQL
