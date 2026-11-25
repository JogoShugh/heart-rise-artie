#!/bin/sh
# Way 2: the script kept from the first walk (release.gitlab.heart.yaml), replayed by the release desk's ARTIE with
# no model, for each of merge requests 501 to 505, TIMES times over. Each release starts from GitLab reseeded and the
# parties fresh, asked of the desk as a person would ask it. Writes runs/kept-R-PULL.* and one line per release to
# runs/kept.jsonl: the check of what GitLab and Dev were left with, and the seconds.
#   kept.sh [TIMES]      (default 5; needs GitLab, its gateway on 8094, and Jenkins)
set -eu
RELEASE_SCRIPT="$(cd "$(dirname "$0")" && pwd)/release.gitlab.heart.yaml"
. "$(dirname "$0")/lib.sh"
[ -f "$RELEASE_SCRIPT" ] || { echo "no release.gitlab.heart.yaml: run walk.sh first" >&2; exit 2; }
times=${1:-5}
runs="$here/runs"
mkdir -p "$runs"
DESK="Authorization: Bearer desk-token"
for r in $(seq "${FIRST:-1}" "$times"); do
  for pull in 501 502 503 504 505; do
    fresh
    parties_up "$runs/kept-$r-$pull.parties.log"
    fields=$(inputs_of $pull | node -e '
      const o = {}; for (const kv of require("fs").readFileSync(0, "utf8").trim().split("|")) {
        const i = kv.indexOf("="); o[kv.slice(0, i)] = kv.slice(i + 1); }
      console.log(JSON.stringify(o));')
    body=$(node -e 'console.log(JSON.stringify({pull: +process.argv[1], scm: process.argv[2], ...JSON.parse(process.argv[3])}))' \
      "$pull" "$H:8094/" "$fields")
    start=$(date +%s)
    etag=$(curl -sfi -H "$DESK" -H 'Accept: application/hal+json' "$H:8160/" | tr -d '\r' \
      | awk -F': ' 'tolower($1)=="etag"{print $2}')
    loc=$(curl -sfi -X POST -H "$DESK" -H "If-Match: $etag" -H 'Accept: application/hal+json' \
      -H 'Content-Type: application/json' -d "$body" "$H:8160/actions/release" \
      | tr -d '\r' | awk -F': ' 'tolower($1)=="location"{print $2; exit}')
    while :; do
      said=$(curl -sf -H "$DESK" -H 'Accept: application/json' "$H:8160$loc")
      case "$said" in *'"state":"running"'*) sleep 1 ;; *) break ;; esac
    done
    seconds=$(( $(date +%s) - start ))
    printf '%s\n' "$said" > "$runs/kept-$r-$pull.desk.json"
    curl -sf -H "$DESK" "$H:8160$loc/trace" > "$runs/kept-$r-$pull.trace.yaml" || true
    verdict=$(check $pull)
    parties_down
    echo "{\"way\":\"kept\",\"run\":$r,\"pull\":$pull,\"seconds\":$seconds,\"check\":$verdict}" | tee -a "$runs/kept.jsonl"
  done
done
