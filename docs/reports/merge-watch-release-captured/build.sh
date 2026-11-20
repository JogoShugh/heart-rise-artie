#!/bin/sh
# Builds the captured release report: the release goal walked once with Claude (the capture), and five releases
# replayed by the script generated from it (runs/pr-N.yaml, written by exhibits/merge-watch-release/variants.sh
# with this folder's runs/ as OUT), each with its own inputs and no model. Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"

lede() { # pull
  inputs=$(sed -E 's/.*"inputs":\{([^}]*)\}.*/\1/; s/"//g; s/,/, /g; s/:/: /g' "$here/runs/pr-$1.desk.json")
  printf 'ARTIE, in the release desk, releases pull request %s by release.heart.yaml, the script generated from one Claude capture of the release goal, given %s. No model is called.' "$1" "$inputs"
}
for pull in 501 502 503 504 505; do
  sh "$assets/build-run-report.sh" "$here/runs/pr-$pull.yaml" "$here/runs/pr-$pull.html" \
    "Release of Pull Request $pull, Replayed" "$(lede "$pull")"
done
sh "$assets/build-run-report.sh" "$exhibit/release.capture.yaml" "$here/runs/capture.html" \
  "The Release, Captured Once with Claude" \
  "ARTIE walks release.goal.md once for pull request 501, the model planning each move; artie generate keeps the walk as release.heart.yaml, which every release after runs with its own inputs and no model."

escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$1"; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape "$exhibit/release.goal.md" > "$tmp/RELEASE_GOAL"
escape "$exhibit/release.heart.yaml" > "$tmp/RELEASE_SCRIPT"
awk -v dir="$tmp" '{
  while (match($0, /@@[A-Z_]+@@/)) {
    key = substr($0, RSTART + 2, RLENGTH - 4); file = dir "/" key; text = ""; first = 1
    while ((getline line < file) > 0) { text = text (first ? "" : "\n") line; first = 0 }
    close(file)
    $0 = substr($0, 1, RSTART - 1) text substr($0, RSTART + RLENGTH)
  }
  print
}' "$here/index.src.html" > "$here/index.html"
echo "wrote index.html"
