#!/usr/bin/env bash
set -e

BASE="${ARTIE_BASE_URL:-http://localhost:8080}"
# SPEC 0.4 §6.1: Accept picks the dialect; the profile travels in
# Accept-Profile / Link rel="profile", never as a media-type parameter.
HSF='application/hal+json'
HSF_PROFILE='<https://github.com/jogoshugh/heart-rise/hal-schema-forms>'
HALFORMS='application/prs.hal-forms+json'
L1='forms=cid'
L2='forms=cid, schema=cid'

# Layer 1 ETag of a bed, as a client would hold it after a GET. SPEC 0.4
# §6.3: every act carries it in If-Match.
etag_of() {
  curl -s -o /dev/null -D - -H "Accept: $HSF" "$BASE/beds/$1" | tr -d '\r' | awk -F': ' 'tolower($1)=="etag"{print $2}'
}

RUN_DIR="run"
rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR"

echo "== resetting database to seed state =="
./reset-db.sh > "$RUN_DIR/00-reset-output.txt"

curl -s -H "Accept: $HALFORMS" "$BASE/beds/1" | jq -r '.status' > "$RUN_DIR/00-bed1-status.txt"
echo "reset done -- bed-1 status: $(cat "$RUN_DIR/00-bed1-status.txt")"

# ---------- sowSeed (bed-1, EMPTY) ----------

# L0: no Prefer, everything collapsed into one response.
curl -s -H "Accept: $HSF" -H "Accept-Profile: $HSF_PROFILE" "$BASE/beds/1" > "$RUN_DIR/01-bed1-l0.json"
# L2: state plus a link to the pointer-only affordance set.
curl -s -D "$RUN_DIR/01-bed1-headers.txt" -H "Accept: $HSF" -H "Accept-Profile: $HSF_PROFILE" -H "Prefer: $L2" \
     "$BASE/beds/1" > "$RUN_DIR/01-bed1-empty.json"

SOWSEED_SET_URL=$(jq -r '._links["hr:forms"].href' < "$RUN_DIR/01-bed1-empty.json")
echo "$SOWSEED_SET_URL" > "$RUN_DIR/01-set-url.txt"
# L1: a different set, with schemas embedded, at a different address.
SOWSEED_L1_SET_URL=$(curl -s -H "Accept: $HSF" -H "Prefer: $L1" "$BASE/beds/1" | jq -r '._links["hr:forms"].href')
echo "$SOWSEED_L1_SET_URL" > "$RUN_DIR/01-set-l1-url.txt"

curl -s "$BASE$SOWSEED_SET_URL" > "$RUN_DIR/01-set-pointer.json"
curl -s "$BASE$SOWSEED_L1_SET_URL" > "$RUN_DIR/01-set-embedded.json"
curl -sI "$BASE$SOWSEED_SET_URL" > "$RUN_DIR/01-set-headers.txt"

SOWSEED_SCHEMA_URL=$(jq -r '._forms.sowSeed.schema."$ref"' < "$RUN_DIR/01-set-pointer.json")
echo "$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema-url.txt"

curl -s "$BASE$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema.json"
curl -sI "$BASE$SOWSEED_SCHEMA_URL" > "$RUN_DIR/01-schema-headers.txt"

curl -s -X POST "$BASE/beds/1/actions/sow-seed" -H "If-Match: $(etag_of 1)" \
     -H "Content-Type: application/json" -d '{"seedType": "basil", "depthCm": 2}' \
     > "$RUN_DIR/01-sow-response.json"

echo "sowSeed captured"

# ---------- waterCell (bed-1, now PLANTED) ----------

curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" > "$RUN_DIR/02-bed2-halforms.json"
curl -s -H "Accept: $HSF" -H "Prefer: $L2" "$BASE/beds/1" > "$RUN_DIR/02-bed1-planted.json"

WATERCELL_SET_URL=$(jq -r '._links["hr:forms"].href' < "$RUN_DIR/02-bed1-planted.json")
echo "$WATERCELL_SET_URL" > "$RUN_DIR/02-set-url.txt"
WATERCELL_L1_SET_URL=$(curl -s -H "Accept: $HSF" -H "Prefer: $L1" "$BASE/beds/1" | jq -r '._links["hr:forms"].href')

curl -s "$BASE$WATERCELL_SET_URL" > "$RUN_DIR/02-set-pointer.json"
curl -s "$BASE$WATERCELL_L1_SET_URL" > "$RUN_DIR/02-set-embedded.json"
curl -sI "$BASE$WATERCELL_SET_URL" > "$RUN_DIR/02-set-headers.txt"

WATERCELL_SCHEMA_URL=$(jq -r '._forms.waterCell.schema."$ref"' < "$RUN_DIR/02-set-pointer.json")
echo "$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema-url.txt"

curl -s "$BASE$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema.json"
curl -sI "$BASE$WATERCELL_SCHEMA_URL" > "$RUN_DIR/02-schema-headers.txt"

curl -s -X POST "$BASE/beds/1/actions/water-cell" -H "If-Match: $(etag_of 1)" \
     -H "Content-Type: application/json" -d '{"milliliters": 500}' \
     > "$RUN_DIR/02-water-response.json"

echo "waterCell captured"

# ---------- harvest (bed-3, seeded READY_TO_HARVEST) ----------

curl -s -H "Accept: $HSF" -H "Prefer: $L1" "$BASE/beds/3" > "$RUN_DIR/03-bed3-pointer.json"

HARVEST_SET_URL=$(jq -r '._links["hr:forms"].href' < "$RUN_DIR/03-bed3-pointer.json")
echo "$HARVEST_SET_URL" > "$RUN_DIR/03-set-url.txt"

curl -s "$BASE$HARVEST_SET_URL" > "$RUN_DIR/03-set-embedded.json"

echo "harvest captured"

# ---------- cross-bed sharing proof ----------

curl -s -H "Accept: $HSF" -H "Prefer: $L2" "$BASE/beds/1" | jq -r '._links["hr:forms"].href' > "$RUN_DIR/04-bed1-forms-href.txt"
curl -s -H "Accept: $HSF" -H "Prefer: $L2" "$BASE/beds/3" | jq -r '._links["hr:forms"].href' > "$RUN_DIR/04-bed3-forms-href.txt"

echo "cross-bed proof captured"

# ---------- point-in-time ----------

curl -s -H "Accept: $HALFORMS" "$BASE/beds/2" > "$RUN_DIR/05-bed2-current.json"
curl -s -H "Accept: $HALFORMS" "$BASE/beds/2?asOf=2026-07-08T00:00:00Z" > "$RUN_DIR/05-bed2-asof.json"

echo "point-in-time captured"

# ---------- errors ----------

# Each act below would change bed 2 or 3 if it got through; none does.
curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/2/actions/water-cell" \
     -H "Content-Type: application/json" -d '{"milliliters": 100}' \
     > "$RUN_DIR/06-missing-status.txt"

curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/2/actions/water-cell" \
     -H "Content-Type: application/json" -H 'If-Match: "seq-999.hsf.L0"' -d '{"milliliters": 100}' \
     > "$RUN_DIR/06-stale-status.txt"

curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/3/actions/sow-seed" -H "If-Match: $(etag_of 3)" \
     -H "Content-Type: application/json" -d '{"seedType": "tomato"}' \
     > "$RUN_DIR/06-invalid-status.txt"

curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/beds/2/actions/water-cell" -H "If-Match: $(etag_of 2)" \
     -H "Content-Type: application/json" -d '{"milliliters": 0}' \
     > "$RUN_DIR/06-schema-status.txt"

# Layer 1 revalidation: the ETag just held, sent back, gets 304.
curl -s -o /dev/null -w "%{http_code}" -H "Accept: $HSF" -H "If-None-Match: $(etag_of 2)" "$BASE/beds/2" \
     > "$RUN_DIR/06-revalidate-status.txt"

curl -s -o /dev/null -w "%{http_code}" "$BASE/schemas/sha256-0000000000000000" \
     > "$RUN_DIR/06-notfound-status.txt"

echo "error cases captured"
echo
echo "All data captured to $RUN_DIR/. Now run: python3 render_journal.py"
