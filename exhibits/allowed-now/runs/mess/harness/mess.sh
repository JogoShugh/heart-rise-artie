#!/usr/bin/env bash
# One run on five bumps: mess.sh PHASE FORM N
#   FORM: direct | prompt | agent | script | gen-proxy | gen-prompt | replay-proxy | replay-prompt
#   gen-* lets the agent do the job, then save it as merge-bumps.sh in its own folder (gen-*-N).
#   replay-* runs gen-*-N's saved script with no model.
set -euo pipefail
S=/private/tmp/claude-501/-Users-josh-repos-artie/ce35ac87-5f2b-400b-873a-eb705d98bae2/scratchpad
X=/Users/josh/repos/artie/exhibits/allowed-now
R=$X/runs/mess; mkdir -p $R
phase=$1; form=$2; n=$3
label=mess-$phase-$form-$n; out=$R/$label
token=$(awk '/^\[https:\/\/api.github.com\]/{f=1;next} /^\[/{f=0} f && $1=="bearer"{print $3}' ~/.artie/credentials)
(cd $S/okt && GITHUB_TOKEN=$token node stage5.mjs $label) > $out.stage.txt

ask="Merge the open dependency bumps labelled $label in JogoShugh/heart-github-test."
save=" Then save what you did as a reusable bash script, merge-bumps.sh in this folder, taking owner, repo and label as arguments, using only curl and jq, so the next run of this job needs no model."
claude_run() { # dir, prompt, tools...
  local dir=$1 prompt=$2; shift 2
  (cd $dir && env -u GITHUB_TOKEN claude -p "$prompt" --model claude-sonnet-5-5 --output-format json \
    --setting-sources project --strict-mcp-config --allowedTools "$@") > $out.json || true
}
curlrc() { umask 077; printf %s\\n "header = \"Authorization: Bearer $token\"" > $1/auth.curlrc; }
skill() { mkdir -p $1/.claude/skills/merge-dependency-bumps; cp $X/skills/$2/SKILL.md $1/.claude/skills/merge-dependency-bumps/; }

start=$(date +%s)
case $form in
  direct) curlrc $S/direct-run
    claude_run $S/direct-run "$ask Use GitHub's REST API at https://api.github.com with curl -K auth.curlrc, which holds the token." "Bash(curl:*)"
    rm -f $S/direct-run/auth.curlrc ;;
  prompt) curlrc $S/prompt-run; claude_run $S/prompt-run "$ask" "Bash(curl:*)" "Skill"; rm -f $S/prompt-run/auth.curlrc ;;
  agent) claude_run $S/agent-run "$ask" "Bash(curl:*)" "Skill" ;;
  script) (cd $X && env -u GITHUB_TOKEN API_URL=http://localhost:8400 npx tsx merge-bumps.ts JogoShugh heart-github-test $label) > $out.txt 2>&1 || true ;;
  gen-proxy) d=$S/gen-proxy-$n; skill $d merge-dependency-bumps
    claude_run $d "$ask$save" "Bash(curl:*)" "Bash(jq:*)" "Skill" "Write"; cp $d/merge-bumps.sh $out.merge-bumps.sh || true ;;
  gen-prompt) d=$S/gen-prompt-$n; skill $d merge-dependency-bumps-rules-in-prompt; curlrc $d
    claude_run $d "$ask$save" "Bash(curl:*)" "Bash(jq:*)" "Skill" "Write"; rm -f $d/auth.curlrc; cp $d/merge-bumps.sh $out.merge-bumps.sh || true ;;
  replay-proxy) d=$S/gen-proxy-$n; (cd $d && env -u GITHUB_TOKEN bash merge-bumps.sh JogoShugh heart-github-test $label) > $out.txt 2>&1 || true ;;
  replay-prompt) d=$S/gen-prompt-$n; curlrc $d
    (cd $d && env -u GITHUB_TOKEN bash merge-bumps.sh JogoShugh heart-github-test $label) > $out.txt 2>&1 || true; rm -f $d/auth.curlrc ;;
esac
secs=$(( $(date +%s) - start ))
sleep 5
score=$(cd $S/okt && GITHUB_TOKEN=$token node score.mjs $out.stage.txt)
cost=""; [ -f $out.json ] && cost=$(jq -r '"turns=\(.num_turns) cost=\(.total_cost_usd) in=\(.usage.input_tokens + .usage.cache_read_input_tokens + .usage.cache_creation_input_tokens) out=\(.usage.output_tokens)"' $out.json 2>/dev/null || echo "json?")
echo "$phase $form $n seconds=$secs $score $cost" | tee -a $R/results.txt
