#!/bin/sh
# Builds the real-GitHub report: the release goal captured once with Claude on real GitHub, and the script kept from it
# replayed with no model on real GitHub (runs/github) and on the local GitLab (runs/gitlab), each with the gateway's
# record of its native API calls (upstream.yaml) and screenshots of each forge's own pages (shots/). Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"

cp "$exhibit/release.capture.yaml" "$here/runs/capture.yaml"
cp "$exhibit/release.heart.yaml" "$here/runs/release.heart.yaml"
lede() { # forge name variant
  pull=$(sed -E 's/.*"pull":([0-9]+).*/\1/' "$here/runs/$1/pr-$3.desk.json")
  printf 'Variant %s, pull request %s on %s: release.heart.yaml, kept from one Claude walk on real GitHub, run by ARTIE in the release desk with no model.' "$3" "$pull" "$2"
}
for v in 501 502 503 504 505; do
  sh "$assets/build-run-report.sh" "$here/runs/github/pr-$v.yaml" "$here/runs/github/pr-$v.html" \
    "Variant $v on Real GitHub" "$(lede github 'real GitHub' "$v")"
  sh "$assets/build-run-report.sh" "$here/runs/gitlab/pr-$v.yaml" "$here/runs/gitlab/pr-$v.html" \
    "Variant $v on Local GitLab" "$(lede gitlab 'the local GitLab' "$v")"
done
sh "$assets/build-run-report.sh" "$here/runs/capture.yaml" "$here/runs/capture.html" \
  "The Release, Captured Once with Claude on Real GitHub" \
  "ARTIE walks release.goal.md once on real GitHub, the model choosing each move from what the gateway offers; artie generate keeps the walk as release.heart.yaml."

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
escape < "$exhibit/release.goal.md" > "$tmp/GOAL"
escape < "$exhibit/release.heart.yaml" > "$tmp/SCRIPT"
node "$here/data.mjs" "$assets/js-yaml.min.js" "$here/runs" | sed 's#</#<\\/#g' > "$tmp/DATA"
cp "$assets/js-yaml.min.js" "$tmp/JS_YAML"
usage() { sed -n '/^ *usage:/,/^ *[a-z_]*:$/p' "$here/runs/capture.yaml" | awk -v k="$1" '$1==k":"{print $2; exit}'; }
printf '%s' "$(usage calls)" > "$tmp/CALLS"
printf "%'d" "$(usage input)" > "$tmp/TOKENS_IN"
printf "%'d" "$(usage output)" > "$tmp/TOKENS_OUT"
grep -m1 "value: '[0-9]*'" "$here/runs/capture.yaml" | sed -E "s/.*'([0-9]+)'.*/\1/" | tr -d '\n' > "$tmp/CAPTURE_PULL"
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
