#!/bin/sh
# Builds the deck: the release's messages for the sequence slide, from the desk's traces of the two-forge replays
# (GitHub 501 promoted, GitHub 504 stopped, GitLab 501 promoted), then the deck itself with heart-deck.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
runs="$repo/docs/reports/merge-watch-two-forges/runs"
tmp=$(mktemp)
node "$repo/docs/reports/merge-watch-sequence-animated/messages.mjs" "$tmp" \
  "gh501=$runs/github/pr-501.yaml" "gh504=$runs/github/pr-504.yaml" "gl501=$runs/gitlab/pr-501.yaml"
# Either forge's gateway is one lifeline, the forge: the script asks both for the same scm: forms.
node -e '
  const fs = require("fs"); global.window = {}; eval(fs.readFileSync(process.argv[1], "utf8"));
  const forge = (p) => (p === "github" || p === "gitlab" ? "forge" : p);
  for (const ms of Object.values(window.RUNS)) for (const m of ms) { m.from = forge(m.from); m.to = forge(m.to); }
  process.stdout.write("window.SEQ_RUNS = " + JSON.stringify(window.RUNS) + ";\n");
' "$tmp" > "$here/code/sequence.js"
rm -f "$tmp"
cat "$here/code/sequence.src.js" >> "$here/code/sequence.js"
node "$here/../heart-deck/build.mjs" "$here" "$@"
