#!/bin/sh
# Builds the two-dialects report: the release in HAL Schema Forms (runs/hsf, written by
# exhibits/merge-watch-release/variants.sh) beside the release in HAL-FORMS (runs/hal-forms, written by
# exhibits/merge-watch-release/hal-forms/run.sh, its walk as runs/hal-forms/capture.yaml). Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"

lede() { # dir dialect pull
  inputs=$(sed -E 's/.*"inputs":\{([^}]*)\}.*/\1/; s/"//g; s/,/, /g; s/:/: /g' "$here/runs/$1/pr-$3.desk.json")
  printf 'ARTIE, in the release desk, releases pull request %s reading every party in %s, by the script kept from one Claude walk, given %s. No model is called.' "$3" "$2" "$inputs"
}
for pull in 501 502 503 504 505; do
  sh "$assets/build-run-report.sh" "$here/runs/hsf/pr-$pull.yaml" "$here/runs/hsf/pr-$pull.html" \
    "Pull Request $pull in HAL Schema Forms, Replayed" "$(lede hsf 'HAL Schema Forms' "$pull")"
  sh "$assets/build-run-report.sh" "$here/runs/hal-forms/pr-$pull.yaml" "$here/runs/hal-forms/pr-$pull.html" \
    "Pull Request $pull in HAL-FORMS, Replayed" "$(lede hal-forms HAL-FORMS "$pull")"
done
sh "$assets/build-run-report.sh" "$here/runs/hal-forms/capture.yaml" "$here/runs/hal-forms/capture.html" \
  "The Release, Walked Once with Claude, in HAL-FORMS" \
  "ARTIE walks release.goal.md once for pull request 501, reading every party in HAL-FORMS, the model planning each move; artie generate keeps the walk as hal-forms/release.heart.yaml, which every release after runs with no model."

escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape < "$exhibit/release.goal.md" > "$tmp/RELEASE_GOAL"
escape < "$exhibit/release.heart.yaml" > "$tmp/HSF_SCRIPT"
escape < "$exhibit/hal-forms/release.heart.yaml" > "$tmp/HF_SCRIPT"
escape < "$exhibit/hal-forms/release.pins.yaml" > "$tmp/HF_PINS"
node "$here/merge-offer.mjs" "$assets/js-yaml.min.js" "$here/runs/hsf/pr-501.yaml" | escape > "$tmp/HSF_MERGE"
node "$here/merge-offer.mjs" "$assets/js-yaml.min.js" "$here/runs/hal-forms/pr-501.yaml" | escape > "$tmp/HF_MERGE"
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
