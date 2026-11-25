#!/bin/sh
# Way 1: ARTIE walks the release goal cold, with Claude as its reasoner, at GitLab, for merge request 501. Each walk
# starts from GitLab reseeded and the parties fresh. The first walk is kept, by artie generate, as the script the
# second way replays: release.gitlab.heart.yaml and its pins. Writes runs/walk-N.* and one line per walk to
# runs/walk.jsonl: the check of what GitLab and Dev were left with, the seconds, and the model's tokens.
#   walk.sh [TIMES]      (default 5; needs GitLab, its gateway on 8094, Jenkins, and a model key)
set -eu
. "$(dirname "$0")/lib.sh"
times=${1:-5}
first=${FIRST:-1}
runs="$here/runs"
mkdir -p "$runs"
pull=501
for i in $(seq "$first" "$times"); do
  fresh
  IFS='|' read -r book author problem comment << EOF
$(inputs_of $pull)
EOF
  rm -f "$runs/walk-$i.dev.json"
  dev_watch "$runs/walk-$i.dev.json"
  start=$(date +%s)
  "$parties_bin" capture "$release/release.goal.md" "$runs/walk-$i.capture.yaml" pull=$pull scm=$H:8094/ \
    "$book" "$author" "$problem" "$comment" > "$runs/walk-$i.out" 2>&1 || true
  seconds=$(( $(date +%s) - start ))
  dev_unwatch
  verdict=$(DEV_FILE="$runs/walk-$i.dev.json" check $pull)
  usage=$(node -e '
    const t = require("fs").readFileSync(process.argv[1], "utf8");
    const u = t.match(/\n  usage:\n((?:    .*\n)+)/);
    const o = {}; if (u) for (const l of u[1].trim().split("\n")) { const [k, v] = l.trim().split(": "); o[k] = +v; }
    console.log(JSON.stringify(o));
  ' "$runs/walk-$i.capture.yaml" 2> /dev/null || echo '{}')
  echo "{\"way\":\"walk\",\"run\":$i,\"seconds\":$seconds,\"usage\":$usage,\"check\":$verdict}" | tee -a "$runs/walk.jsonl"
  if [ "$i" = 1 ]; then
    "$artie_bin" generate "$runs/walk-1.capture.yaml" --out "$here/release.gitlab.heart.yaml" \
      --script "$release/release.goal.md" --format yaml > "$runs/walk-1.generate.out" 2>&1 \
      || { echo "artie generate failed: runs/walk-1.generate.out" >&2; exit 1; }
  fi
done
