#!/usr/bin/env bash
# The meddler ladder (spec/ideas/gateway-pass-through.md, step 4): one task on issue 7 of acme/web in heart-sims'
# GitHub service, the gateway in front of its small GitHub; a teammate retitles and closes the issue on GitHub
# itself right after the client first reads it. Each arm: the owner's rungs, and the client. No model.
#   heart-sims on $SIM (docker run -p 8098:8099 heart-sims 8099 github.test); ./ladder.sh [reps]
set -euo pipefail
cd "$(dirname "$0")"
SIM=${SIM:-http://localhost:8098}
REPS=${1:-3}
ARTIE=../../client/artie-cli/build/install/artie/bin/artie
OUT=runs/ladder-$(date +%Y%m%d-%H%M%S).jsonl
export ARTIE_TOKEN_LOCALHOST_8098=github-token   # the small GitHub's test token for ada; not a secret
now() { perl -MTime::HiRes=time -e 'printf "%d", time * 1000'; }
TEAMMATE='{"issue":7,"title":"The nightly build is slow on Mondays","state":"closed","state_reason":"not_planned"}'

# arm | the owner's rungs | script | inputs
ARMS=(
  "bare, GitHub as it is (transparent)||bare.kts|validators=false"
  "bare with validators, no rung to check them||bare.kts|validators=true"
  "bare, offered rung|offered|bare.kts|validators=false"
  "bare with validators, conditional rung|conditional|bare.kts|validators=true"
  "bare with validators, every rung|described,connector,offered,conditional|bare.kts|validators=true"
  "HEART script, no model||heart.kts|"
)

for rep in $(seq 1 "$REPS"); do
  for arm in "${ARMS[@]}"; do
    IFS='|' read -r name dam script inputs <<<"$arm"
    curl -sf -X POST "$SIM/_sim/reset" >/dev/null
    curl -sf -X PUT "$SIM/_sim/github/dam" --data "$dam" >/dev/null
    curl -sf -X PUT "$SIM/_sim/github/meddle" --data "$TEAMMATE" >/dev/null
    started=$(now)
    set +e
    said=$("$ARTIE" run "scripts/$script" gateway="$SIM" $inputs --trace "runs/trace-$rep-${script%.kts}-$(echo "$dam" | tr ',' '-').yaml" 2>&1)
    exit=$?
    set -e
    ms=$(( $(now) - started ))
    issue=$(curl -sf -H "Authorization: Bearer github-token" -H "X-Gateway-Mode: pass-through" "$SIM/repos/acme/web/issues/7")
    jq -nc --arg arm "$name" --arg dam "$dam" --argjson rep "$rep" --argjson exit "$exit" --argjson ms "$ms" \
      --arg said "$(echo "$said" | tail -3)" --argjson issue "$issue" '{
        arm: $arm, dam: $dam, rep: $rep, exit: $exit, ms: $ms, said: $said,
        title: $issue.title, state: $issue.state, state_reason: $issue.state_reason,
        teammate_title_kept: ($issue.title | startswith("The nightly build is slow on Mondays")),
        teammate_reason_kept: ($issue.state_reason == "not_planned"),
        triaged: ($issue.title | endswith("(triaged)"))
      }' | tee -a "$OUT"
  done
done
echo "written: $OUT"
