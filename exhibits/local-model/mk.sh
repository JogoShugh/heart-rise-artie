#!/bin/bash
# mk.sh MODEL : writes req-<kind>.json for each turn, in the reasoner's and planner's exact shape
set -e
cd "$(dirname "$0")"
M=$1
for k in repo merge comment; do
  jq -n --arg model "$M" --rawfile sys reasoner-system.txt --slurpfile tool reasoner-tool.json --rawfile turn turn-$k.txt \
    '{model:$model, max_tokens:1024, tools:$tool, tool_choice:{type:"auto"}, system:[{type:"text",text:$sys,cache_control:{type:"ephemeral"}}], messages:[{role:"user",content:[{type:"text",text:$turn}]}]}' > req-$k.json
done
jq -n --arg model "$M" --rawfile sys planner-system.txt --slurpfile tool planner-tool.json --rawfile turn turn-plan.txt \
  '{model:$model, max_tokens:1024, tools:$tool, tool_choice:{type:"auto"}, system:[{type:"text",text:$sys,cache_control:{type:"ephemeral"}}], messages:[{role:"user",content:[{type:"text",text:$turn}]}]}' > req-plan.json
ls -la req-*.json | awk '{print $5, $9}'
