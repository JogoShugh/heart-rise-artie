#!/bin/sh
# Builds the merge watch release report from the two runs' traces (pass.yaml, fail.yaml, written by ReleaseTrace)
# and the goals as they stand in exhibits/merge-watch-release. Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"

sh "$assets/build-run-report.sh" "$here/pass.yaml" "$here/pass.html" "Release of Pull Request 482, Passing" \
  "ARTIE releases pull request 482 across five parties: watched until it can be merged, merged by squashing in git, built by the real Jenkins, smoke tested on the preview it deployed (5 of 5), and promoted to Dev."
sh "$assets/build-run-report.sh" "$here/fail.yaml" "$here/fail.html" "Release of Pull Request 483, Stopped" \
  "The same release for pull request 483, which sets renewals to zero weeks: built and deployed, smoke scenario S3 fails, so it is not promoted, and ARTIE says why on the pull request."

# The sequence diagrams: PlantUML from each trace, rendered when a plantuml.jar is at hand (PLANTUML_JAR, or
# exhibits/merge-watch-release/tools/plantuml.jar, which tools/get-plantuml.sh fetches); else the SVGs kept are used.
node "$here/sequence.mjs" "$here/pass.yaml" "$here/pass.puml" "Release of pull request 482, passing"
node "$here/sequence.mjs" "$here/fail.yaml" "$here/fail.puml" "Release of pull request 483, stopped"
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
escape "$exhibit/smoke/smoke.goal.md" > "$tmp/SMOKE_GOAL"
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
