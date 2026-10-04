#!/usr/bin/env bash
set -e


BASE="http://localhost:8080"
JBADEAU='application/hal+json;profile="https://github.com/jbadeau/hal-schema-forms"'
HALFORMS='application/hal+json, application/prs.hal-forms+json'

# ---------- reset the database to the exact seed state, every run ----------
# Postgres data persists across script runs (docker compose doesn't wipe the
# volume between them) -- without this, bed-1's state carries over from
# whatever a PREVIOUS run left it in, and every step below silently reads
# the wrong bed status. Run this script from the spring-artie-backend/
# directory (where docker-compose.yml lives) so `docker compose exec` finds
# the right service.

echo "== resetting database to seed state =="
docker compose exec -T postgres psql -U artie -d gardenbed <<'SQL'
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
echo "== reset done -- bed-1 EMPTY, bed-2 PLANTED, bed-3 READY_TO_HARVEST =="
echo

# ---------- confirm the reset actually worked before doing anything else ----------

echo "== sanity check: bed-1 status right now (must say EMPTY) =="
curl -s -H "Accept: $HALFORMS" "$BASE/beds/1" | jq -r '.status'

# ---------- halforms dialect ----------

echo "== halforms dialect: GET bed-2 =="
curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" | yq -p json

# ---------- sowSeed (bed-1, starts EMPTY) ----------

echo "== jbadeau dialect: GET bed-1 (EMPTY, pointer only) =="
curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | yq -p json

SOWSEED_SET_URL=$(curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | jq -r '._links["hr:forms"].href')
echo "== captured sowSeed affordance-set href: $SOWSEED_SET_URL =="

echo "== GET sowSeed affordance-set (schema as \$id pointer) =="
curl -s "$BASE$SOWSEED_SET_URL" | yq -p json

echo "== GET sowSeed affordance-set with Prefer: embed=schemas -- real properties =="
curl -s -H "Prefer: embed=schemas" "$BASE$SOWSEED_SET_URL" | yq -p json

echo "== headers only: confirm Preference-Applied + Vary =="
curl -sI -H "Prefer: embed=schemas" "$BASE$SOWSEED_SET_URL" | grep -i "preference-applied\|vary"

SOWSEED_SCHEMA_URL=$(curl -s "$BASE$SOWSEED_SET_URL" | jq -r '._forms.sowSeed.schema."$id"')
echo "== captured sowSeed schema href: $SOWSEED_SCHEMA_URL =="

echo "== GET sowSeed schema directly =="
curl -s "$BASE$SOWSEED_SCHEMA_URL" | yq -p json

echo "== headers only: confirm Cache-Control immutable =="
curl -sI "$BASE$SOWSEED_SCHEMA_URL" | grep -i cache-control

echo "== sow a seed on bed-1 (EMPTY -> PLANTED) =="
curl -s -X POST "$BASE/beds/1/actions/sow-seed" \
     -H "Content-Type: application/json" -d '{"seedType": "basil", "depthCm": 2}' | yq -p json

# ---------- waterCell (bed-1, now PLANTED) ----------

echo "== jbadeau dialect: GET bed-1 (PLANTED, pointer only) =="
curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | yq -p json

WATERCELL_SET_URL=$(curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | jq -r '._links["hr:forms"].href')
echo "== captured waterCell affordance-set href: $WATERCELL_SET_URL =="

echo "== GET waterCell affordance-set (schema as \$id pointer) =="
curl -s "$BASE$WATERCELL_SET_URL" | yq -p json

echo "== GET waterCell affordance-set with Prefer: embed=schemas -- real properties =="
curl -s -H "Prefer: embed=schemas" "$BASE$WATERCELL_SET_URL" | yq -p json

echo "== headers only: confirm Preference-Applied + Vary =="
curl -sI -H "Prefer: embed=schemas" "$BASE$WATERCELL_SET_URL" | grep -i "preference-applied\|vary"

WATERCELL_SCHEMA_URL=$(curl -s "$BASE$WATERCELL_SET_URL" | jq -r '._forms.waterCell.schema."$id"')
echo "== captured waterCell schema href: $WATERCELL_SCHEMA_URL =="

echo "== GET waterCell schema directly =="
curl -s "$BASE$WATERCELL_SCHEMA_URL" | yq -p json

echo "== headers only: confirm Cache-Control immutable =="
curl -sI "$BASE$WATERCELL_SCHEMA_URL" | grep -i cache-control

echo "== water bed-1 up to READY_TO_HARVEST =="
curl -s -X POST "$BASE/beds/1/actions/water-cell" \
     -H "Content-Type: application/json" -d '{"milliliters": 500}' | yq -p json

# ---------- cross-bed sharing proof ----------

echo "== bed-1 forms href (should now match bed-3's -- both READY_TO_HARVEST) =="
curl -s -H "Accept: $JBADEAU" "$BASE/beds/1" | jq -r '._links["hr:forms"].href'

echo "== bed-3 forms href (seeded READY_TO_HARVEST) =="
curl -s -H "Accept: $JBADEAU" "$BASE/beds/3" | jq -r '._links["hr:forms"].href'

# ---------- harvest (bed-2, seeded READY_TO_HARVEST after 2 waterings... actually PLANTED, see note) ----------
# Note: bed-2's seed data is 1 sow + 2 waterings of 200ml each = 40 growth
# points, still PLANTED, not harvestable yet -- use bed-1 or bed-3 (both
# genuinely READY_TO_HARVEST) to see the harvest set instead.

echo "== jbadeau dialect: GET bed-3 (READY_TO_HARVEST, pointer only) =="
curl -s -H "Accept: $JBADEAU" "$BASE/beds/3" | yq -p json

HARVEST_SET_URL=$(curl -s -H "Accept: $JBADEAU" "$BASE/beds/3" | jq -r '._links["hr:forms"].href')
echo "== captured harvest affordance-set href: $HARVEST_SET_URL =="

echo "== GET harvest affordance-set with Prefer: embed=schemas -- properties genuinely empty, not a bug =="
curl -s -H "Prefer: embed=schemas" "$BASE$HARVEST_SET_URL" | yq -p json

echo "== jbadeau dialect: GET bed-3 with Prefer: embed=schemas (collapse all 3 layers into one response) =="
curl -s -H "Accept: $JBADEAU" -H "Prefer: embed=schemas" "$BASE/beds/3" | yq -p json

# ---------- point-in-time state / history ----------

echo "== current state of bed-2 =="
curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" | yq -p json

echo "== bed-2 state as of an earlier instant =="
curl -s -H "Accept: $HALFORMS" "$BASE/beds/2?asOf=2026-07-08T00:00:00Z" | yq -p json

echo "== full raw event log for bed-2 =="
curl -s "$BASE/beds/2/history" | yq -p json

# ---------- concurrency / error cases ----------

echo "== capture bed-2's current ETag =="
curl -sI -H "Accept: $HALFORMS" "$BASE/beds/2" | grep -i etag

echo "== stale If-Match -- expect 412 =="
curl -si -X POST "$BASE/beds/2/actions/water-cell" \
     -H "Content-Type: application/json" \
     -H 'If-Match: "seq-999"' \
     -d '{"milliliters": 100}' | head -1

echo "== invalid transition on bed-3 -- expect 409 (bed-3 is READY_TO_HARVEST, can't sow) =="
curl -si -X POST "$BASE/beds/3/actions/sow-seed" \
     -H "Content-Type: application/json" -d '{"seedType": "tomato"}' | head -1

echo "== unknown schema hash -- expect 404 =="
curl -si "$BASE/schemas/sha256-0000000000000000" | head -1