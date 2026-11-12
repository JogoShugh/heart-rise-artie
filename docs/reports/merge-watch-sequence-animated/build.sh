#!/bin/sh
# Builds the animated sequence's messages from the desk's traces of the captured release's replays (501 promoted,
# 504 stopped), written by exhibits/merge-watch-release/variants.sh. Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
runs="$here/../merge-watch-release-captured/runs"
node "$here/messages.mjs" "$here/runs.js" "501=$runs/pr-501.yaml" "504=$runs/pr-504.yaml"
