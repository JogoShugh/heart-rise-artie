#!/bin/sh
# Builds the merge watch release report from the release desk's traces of its runs (runs/pr-N.yaml, written by
# exhibits/merge-watch-release/variants.sh), the smoke test's capture, and the goals and scripts as they stand in
# exhibits/merge-watch-release. Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"

# Each release, a run report of its own: ARTIE in the desk, the same kept scripts, its own inputs, no model.
lede() { # pull
  inputs=$(sed -E 's/.*"inputs":\{([^}]*)\}.*/\1/; s/"//g; s/,/, /g; s/:/: /g' "$here/runs/pr-$1.desk.json")
  notes=$(sed -E 's/.*"notes":\[(.*)\].*/\1/; s/","/ /g; s/"//g' "$here/runs/pr-$1.desk.json")
  printf 'ARTIE, in the release desk, releases pull request %s by the kept release script, its smoke test the script captured once with Claude, given %s. No model is called. %s' "$1" "$inputs" "$notes"
}
for pull in 501 502 503 504 505; do
  sh "$assets/build-run-report.sh" "$here/runs/pr-$pull.yaml" "$here/runs/pr-$pull.html" \
    "Release of Pull Request $pull" "$(lede "$pull")"
done
sh "$assets/build-run-report.sh" "$exhibit/smoke/smoke.capture.yaml" "$here/runs/smoke-capture.html" \
  "The Smoke Test, Captured Once with Claude" \
  "ARTIE walks smoke.goal.md once, the model planning each move, against a preview known to be good; artie generate keeps the walk as smoke.heart.yaml, which every release then runs with its own inputs and no model."

# The sequence diagrams: PlantUML from a released run and a stopped one, rendered when a plantuml.jar is at hand
# (PLANTUML_JAR, or exhibits/merge-watch-release/tools/plantuml.jar, which tools/get-plantuml.sh fetches).
node "$here/sequence.mjs" "$here/runs/pr-501.yaml" "$here/pass.puml" "Release of pull request 501: promoted to Dev"
node "$here/sequence.mjs" "$here/runs/pr-504.yaml" "$here/fail.puml" "Release of pull request 504: stopped by its smoke test"
jar=${PLANTUML_JAR:-$exhibit/tools/plantuml.jar}
if [ -f "$jar" ]; then
  java -Djava.awt.headless=true -jar "$jar" -tsvg "$here/pass.puml" "$here/fail.puml"
  echo "rendered pass.svg, fail.svg"
else
  echo "no plantuml.jar: kept the SVGs as they were (run exhibits/merge-watch-release/tools/get-plantuml.sh)"
fi

escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$1"; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape "$exhibit/release.goal.md" > "$tmp/RELEASE_GOAL"
escape "$exhibit/release.heart.yaml" > "$tmp/RELEASE_SCRIPT"
escape "$exhibit/smoke/smoke.goal.md" > "$tmp/SMOKE_GOAL"
escape "$exhibit/smoke/smoke.heart.yaml" > "$tmp/SMOKE_SCRIPT"
awk -v dir="$tmp" '{
  while (match($0, /@@[A-Z_]+@@/)) {
    key = substr($0, RSTART + 2, RLENGTH - 4); file = dir "/" key; text = ""; first = 1
    while ((getline line < file) > 0) { text = text (first ? "" : "\n") line; first = 0 }
    close(file)
    $0 = substr($0, 1, RSTART - 1) text substr($0, RSTART + RLENGTH)
  }
  print
}' "$here/overview.src.html" > "$here/overview.html"
echo "wrote overview.html"
