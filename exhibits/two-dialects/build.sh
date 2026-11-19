#!/bin/sh
# Builds the deck: the release's messages for the sequence slide, from the desk's traces of pull request 501 in each
# dialect (docs/reports/merge-watch-two-dialects/runs), then the deck itself with heart-deck.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
runs="$repo/docs/reports/merge-watch-two-dialects/runs"
tmp=$(mktemp)
node "$repo/docs/reports/merge-watch-sequence-animated/messages.mjs" "$tmp" \
  "hsf501=$runs/hsf/pr-501.yaml" "hf501=$runs/hal-forms/pr-501.yaml" "hf504=$runs/hal-forms/pr-504.yaml"
node -e '
  const fs = require("fs"); global.window = {}; eval(fs.readFileSync(process.argv[1], "utf8"));
  const forge = (p) => (p === "github" || p === "gitlab" ? "forge" : p);
  for (const ms of Object.values(window.RUNS)) for (const m of ms) { m.from = forge(m.from); m.to = forge(m.to); }
  process.stdout.write("window.SEQ_RUNS = " + JSON.stringify(window.RUNS) + ";\n");
' "$tmp" > "$here/code/sequence.js"
rm -f "$tmp"
cat "$here/code/sequence.src.js" >> "$here/code/sequence.js"
node "$here/../heart-deck/build.mjs" "$here" "$@"
