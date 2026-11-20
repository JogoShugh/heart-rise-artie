#!/bin/bash
# ask.sh MODEL KIND : POST req-KIND.json to the local /v1/messages, keep the answer as ans-MODEL-KIND.json, print the gist
cd "$(dirname "$0")"
M=$1; K=$2; T=${M//[:\/]/_}
S=$(date +%s.%N)
curl -s -o ans-$T-$K.json -w '%{http_code}' -X POST http://localhost:11434/v1/messages \
  -H 'x-api-key: none' -H 'anthropic-version: 2023-06-01' -H 'Content-Type: application/json' \
  --data-binary @req-$K.json > status-$T-$K.txt
E=$(date +%s.%N)
printf '%s %s status=%s wall=%.1fs\n' "$M" "$K" "$(cat status-$T-$K.txt)" "$(echo "$E - $S" | bc)"
jq -c '{stop: .stop_reason, usage: .usage, content: [.content[] | {type, name, input, text: (.text // "" | .[0:200])}]}' ans-$T-$K.json
