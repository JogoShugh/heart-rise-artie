#!/bin/sh
# Builds the two-forges report: the release goal captured once with Claude on GitHub, and the script generated from
# it replayed for five pull requests on GitHub and on GitLab, with no model. runs/github and runs/gitlab are written by
# exhibits/merge-watch-release/variants.sh (RELEASE_SCM=gitlab for GitLab); runs/capture.yaml is the capture.
# Run from anywhere.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
exhibit="$root/exhibits/merge-watch-release"
connectors="$root/heart/heart-gateway/connectors"

lede() { # forge name pull
  inputs=$(sed -E 's/.*"inputs":\{([^}]*)\}.*/\1/; s/"//g; s/,/, /g; s/:/: /g' "$here/runs/$1/pr-$3.desk.json")
  printf 'ARTIE, in the release desk, releases pull request %s on %s by release.heart.yaml, the script generated from one Claude capture on GitHub, given %s. No model is called.' "$3" "$2" "$inputs"
}
for pull in 501 502 503 504 505; do
  sh "$assets/build-run-report.sh" "$here/runs/github/pr-$pull.yaml" "$here/runs/github/pr-$pull.html" \
    "Pull Request $pull on GitHub, Replayed" "$(lede github GitHub "$pull")"
  sh "$assets/build-run-report.sh" "$here/runs/gitlab/pr-$pull.yaml" "$here/runs/gitlab/pr-$pull.html" \
    "Merge Request $pull on GitLab, Replayed" "$(lede gitlab GitLab "$pull")"
done
sh "$assets/build-run-report.sh" "$here/runs/capture.yaml" "$here/runs/capture.html" \
  "The Release, Captured Once with Claude, in scm's Words" \
  "ARTIE walks release.goal.md once for pull request 501 on GitHub, the model planning each move; artie generate keeps the walk as release.heart.yaml, which every release after runs, on either forge, with no model."

escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
act() { sed -n '/^  merge:/,/^  comment:/p' "$1" | sed '$d'; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape < "$exhibit/release.goal.md" > "$tmp/RELEASE_GOAL"
escape < "$exhibit/release.heart.yaml" > "$tmp/RELEASE_SCRIPT"
node "$here/merge-step.mjs" "$assets/js-yaml.min.js" "$here/runs/github/pr-501.yaml" | escape > "$tmp/GITHUB_MERGE"
node "$here/merge-step.mjs" "$assets/js-yaml.min.js" "$here/runs/gitlab/pr-501.yaml" | escape > "$tmp/GITLAB_MERGE"
act "$connectors/github-scm/pull.resource.yaml" | escape > "$tmp/GITHUB_ACT"
act "$connectors/gitlab/pull.resource.yaml" | escape > "$tmp/GITLAB_ACT"
sed -n '/^normal:/,/^acts:/p' "$connectors/gitlab/pull.resource.yaml" | sed '$d' | escape > "$tmp/GITLAB_NORMAL"
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
