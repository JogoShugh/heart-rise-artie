#!/usr/bin/env bash
set -e

BASE="http://localhost:8080"
JBADEAU='application/hal+json;profile="https://github.com/jbadeau/hal-schema-forms"'
HALFORMS='application/hal+json, application/prs.hal-forms+json'

RUN_DIR="run"
rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR"

echo "== resetting database to seed state =="
docker compose exec -T postgres psql -U artie -d gardenbed > "$RUN_DIR/00-reset-output.txt" <<'SQL'
TRUNCATE garden_bed_event, garden_bed_snapshot, garden_bed RESTART IDENTITY CASCADE;

INSERT INTO garden_bed (id, label) VALUES
    (1, 'bed-1'),
    (2, 'bed-2'),
    (3, 'bed-3');

INSERT INTO garden_bed_event (garden_bed_id, sequence_no, event_type, payload, occurred_at) VALUES
    (2, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '5 days'),
    (2, 2, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '4 days'),
    (2, 3, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '2 days'),
    (3, 1, 'SeedSown', '{"seedType": "basil", "depthCm": 1}'::jsonb, now() - INTERVAL '12 days'),
    (3, 2, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '10 days'),
    (3, 3, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '7 days');

SELECT setval('garden_bed_id_seq', 3, true);
SQL

curl -s -H "Accept: $HALFORMS" "$BASE/beds/1" | jq -r '.status' > "$RUN_DIR/00-bed1-status.txt"
echo "reset done -- bed-1 status: $(cat "$RUN_DIR/00-bed1-status.txt")"

# ---------- sowSeed (bed-1, EMPTY) ----------

curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" > "$RUN_DIR/01-bed1-empty.json"

SOWSEED_SET_URL=$(jq -r '._links["http://api.example.org/rels/forms"].href' < "$RUN_DIR/01-bed1-empty.json")
echo "$SOWSEED_SET_URL" > "$RUN_DIR/01-set-url.txt"

curl -s "$BASE$SOWSEED_SET_URL" > "$RUN_DIR/01-set-pointer.json"
curl -s -H "Prefer: embed=schemas" "$BASE$SOWSEED_SET_URL" > "$RUN_DIR/01-set-embedded.json"
curl -sI -H "Prefer: embed=schemas" "$BASE$SOWSEED_SET_URL" > "$RUN_DIR/01-set-headers.txt"

SOWSEED_SCHEMA_URL=$(jq -r '._forms.sowSeed.schema."$id"' < "$RUN_DIR/01-set-pointer.json")
echo "$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema-url.txt"

curl -s "$BASE$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema.json"
curl -sI "$BASE$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema-headers.txt"

curl -s -X POST "$BASE/beds/1/actions/sow-seed" \
     -H "Content-Type: application/json" -d '{"seedType": "basil", "depthCm": 2}' \
     > "$RUN_DIR/01-sow-response.json"

echo "sowSeed captured"

# ---------- waterCell (bed-1, now PLANTED) ----------

curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" > "$RUN_DIR/02-bed2-halforms.json"
curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" > "$RUN_DIR/02-bed1-planted.json"

WATERCELL_SET_URL=$(jq -r '._links["http://api.example.org/rels/forms"].href' < "$RUN_DIR/02-bed1-planted.json")
echo "$WATERCELL_SET_URL" > "$RUN_DIR/02-set-url.txt"

curl -s "$BASE$WATERCELL_SET_URL" > "$RUN_DIR/02-set-pointer.json"
curl -s -H "Prefer: embed=schemas" "$BASE$WATERCELL_SET_URL" > "$RUN_DIR/02-set-embedded.json"
curl -sI -H "Prefer: embed=schemas" "$BASE$WATERCELL_SET_URL" > "$RUN_DIR/02-set-headers.txt"

WATERCELL_SCHEMA_URL=$(jq -r '._forms.waterCell.schema."$id"' < "$RUN_DIR/02-set-pointer.json")
echo "$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema-url.txt"

curl -s "$BASE$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema.json"
curl -sI "$BASE$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema-headers.txt"

curl -s -X POST "$BASE/beds/1/actions/water-cell" \
     -H "Content-Type: application/json" -d '{"milliliters": 500}' \
     > "$RUN_DIR/02-water-response.json"

echo "waterCell captured"

# ---------- harvest (bed-3, seeded READY_TO_HARVEST) ----------

curl -s -H "Accept: $JBADEAU" "$BASE/beds/3" > "$RUN_DIR/03-bed3-pointer.json"

HARVEST_SET_URL=$(jq -r '._links["http://api.example.org/rels/forms"].href' < "$RUN_DIR/03-bed3-pointer.json")
echo "$HARVEST_SET_URL" > "$RUN_DIR/03-set-url.txt"

curl -s -H "Prefer: embed=schemas" "$BASE$HARVEST_SET_URL" > "$RUN_DIR/03-set-embedded.json"

echo "harvest captured"

# ---------- cross-bed sharing proof ----------

curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | jq -r '._links["http://api.example.org/rels/forms"].href' > "$RUN_DIR/04-bed1-forms-href.txt"
curl -s -H "Accept: $JBADEAU" "$BASE/beds/3" | jq -r '._links["http://api.example.org/rels/forms"].href' > "$RUN_DIR/04-bed3-forms-href.txt"

echo "cross-bed proof captured"

# ---------- point-in-time ----------

curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" > "$RUN_DIR/05-bed2-current.json"
curl -s -H "Accept: $HALFORMS" "$BASE/beds/2?asOf=2026-07-08T00:00:00Z" > "$RUN_DIR/05-bed2-asof.json"

echo "point-in-time captured"

# ---------- errors ----------

curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/2/actions/water-cell" \
     -H "Content-Type: application/json" -H 'If-Match: "seq-999"' -d '{"milliliters": 100}' \
     > "$RUN_DIR/06-stale-status.txt"

curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/3/actions/sow-seed" \
     -H "Content-Type: application/json" -d '{"seedType": "tomato"}' \
     > "$RUN_DIR/06-invalid-status.txt"

curl -s -o /dev/null -w "%{http_code}" "$BASE/schemas/sha256-0000000000000000" \
     > "$RUN_DIR/06-notfound-status.txt"

echo "error cases captured"
echo
echo "All data captured to $RUN_DIR/. Now run: python3 render_journal.py"
