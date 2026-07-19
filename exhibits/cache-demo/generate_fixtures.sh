#!/usr/bin/env bash
# generate_fixtures.sh
#
# Builds a three-level, content-addressed fixture set:
#   resources/<bed>.json          -- state + a single link to its affordance-set
#   affordance-sets/sha256-*.json -- named affordances, each pointing at a schema
#   schemas/sha256-*.json         -- immutable schema bodies
#
# Hashes are REAL sha256 of the actual file content, truncated to 16 hex
# chars for readable filenames. Re-running this script is deterministic --
# same content in, same hash out, same filenames out.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES="$ROOT/fixtures"
RESOURCES="$FIXTURES/resources"
SETS="$FIXTURES/affordance-sets"
SCHEMAS="$FIXTURES/schemas"

rm -rf "$FIXTURES"
mkdir -p "$RESOURCES" "$SETS" "$SCHEMAS"

hash_of() {
  # sha256 of stdin, first 16 hex chars
  shasum -a 256 | cut -c1-16
}

declare -A SCHEMA_HASH   # name -> hash
declare -A SET_HASH      # set-name -> hash
declare -A SET_MEMBERS   # set-name -> space separated affordance names

write_schema() {
  local name="$1" body="$2"
  local h
  h="$(printf '%s' "$body" | hash_of)"
  printf '%s' "$body" > "$SCHEMAS/sha256-${h}.json"
  SCHEMA_HASH["$name"]="$h"
}

# --- Level 3: schemas (10 distinct affordance schemas) ---------------------

write_schema sowSeed '{
  "title": "Sow Seed",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "seedType": { "type": "string", "enum": ["tomato", "basil", "pepper"] },
    "depthCm": { "type": "number", "minimum": 0.5, "maximum": 5 }
  },
  "required": ["cellId", "seedType"]
}'

write_schema waterCell '{
  "title": "Water Cell",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "milliliters": { "type": "number", "minimum": 10, "maximum": 500 }
  },
  "required": ["cellId", "milliliters"]
}'

write_schema harvest '{
  "title": "Harvest",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "yieldGrams": { "type": "number", "minimum": 0 }
  },
  "required": ["cellId"]
}'

write_schema fertilize '{
  "title": "Fertilize",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "product": { "type": "string" }
  },
  "required": ["cellId", "product"]
}'

write_schema prune '{
  "title": "Prune",
  "type": "object",
  "properties": { "cellId": { "type": "string" } },
  "required": ["cellId"]
}'

write_schema inspect '{
  "title": "Inspect",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "notes": { "type": "string" }
  },
  "required": ["cellId"]
}'

write_schema transplant '{
  "title": "Transplant",
  "type": "object",
  "properties": {
    "fromCellId": { "type": "string" },
    "toCellId": { "type": "string" }
  },
  "required": ["fromCellId", "toCellId"]
}'

write_schema mulch '{
  "title": "Mulch",
  "type": "object",
  "properties": { "cellId": { "type": "string" } },
  "required": ["cellId"]
}'

write_schema thin '{
  "title": "Thin Seedlings",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "keepCount": { "type": "integer", "minimum": 1 }
  },
  "required": ["cellId", "keepCount"]
}'

write_schema rotate '{
  "title": "Rotate Crop",
  "type": "object",
  "properties": {
    "cellId": { "type": "string" },
    "nextCrop": { "type": "string" }
  },
  "required": ["cellId", "nextCrop"]
}'

echo "Wrote ${#SCHEMA_HASH[@]} schema files to $SCHEMAS"

# --- Level 2: affordance-sets (variety in size: 1, 2, 3, 10) ---------------

write_set() {
  local set_name="$1"; shift
  local members=("$@")
  SET_MEMBERS["$set_name"]="${members[*]}"

  local body="{"
  local first=1
  for m in "${members[@]}"; do
    [ $first -eq 1 ] && first=0 || body+=","
    local h="${SCHEMA_HASH[$m]}"
    body+="\"${m}\":{\"method\":\"POST\",\"target\":\"/beds/{bedId}/cells\",\"schemaRef\":{\"href\":\"/schemas/sha256-${h}.json\"}}"
  done
  body+="}"

  local h
  h="$(printf '%s' "$body" | hash_of)"
  printf '%s' "$body" > "$SETS/sha256-${h}.json"
  SET_HASH["$set_name"]="$h"
}

write_set setSolo  sowSeed
write_set setPair  sowSeed waterCell
write_set setTrio  sowSeed waterCell harvest
write_set setFull  sowSeed waterCell harvest fertilize prune inspect transplant mulch thin rotate

echo "Wrote ${#SET_HASH[@]} affordance-set files to $SETS"
for k in "${!SET_HASH[@]}"; do
  n_members=$(wc -w <<< "${SET_MEMBERS[$k]}")
  echo "  $k -> sha256-${SET_HASH[$k]}.json  ($n_members affordance(s): ${SET_MEMBERS[$k]})"
done

# --- Level 1: resources (12 beds, deliberately reusing set hashes) ---------

declare -A BED_SET=(
  [bed-1]=setPair
  [bed-2]=setPair
  [bed-3]=setSolo
  [bed-4]=setTrio
  [bed-5]=setPair
  [bed-6]=setTrio
  [bed-7]=setFull
  [bed-8]=setSolo
  [bed-9]=setFull
  [bed-10]=setTrio
  [bed-11]=setPair
  [bed-12]=setSolo
)

for bed in "${!BED_SET[@]}"; do
  set_name="${BED_SET[$bed]}"
  set_hash="${SET_HASH[$set_name]}"
  cat > "$RESOURCES/${bed}.json" <<EOF
{
  "id": "${bed}",
  "state": { "moisture": $((RANDOM % 100)), "crop": "tomato" },
  "_links": {
    "self": { "href": "/resources/${bed}.json" },
    "affordances": { "href": "/affordance-sets/sha256-${set_hash}.json" }
  }
}
EOF
done

echo "Wrote ${#BED_SET[@]} resource files to $RESOURCES"

# --- Manifest, for humans -----------------------------------------------

MANIFEST="$FIXTURES/manifest.txt"
{
  echo "bed -> affordance-set -> hash -> affordance count"
  for bed in $(printf '%s\n' "${!BED_SET[@]}" | sort -t- -k2 -n); do
    set_name="${BED_SET[$bed]}"
    set_hash="${SET_HASH[$set_name]}"
    n_members=$(wc -w <<< "${SET_MEMBERS[$set_name]}")
    printf '%-8s -> %-8s -> sha256-%s.json -> %s\n' "$bed" "$set_name" "$set_hash" "$n_members"
  done
} > "$MANIFEST"

echo
echo "Manifest written to $MANIFEST:"
cat "$MANIFEST"
