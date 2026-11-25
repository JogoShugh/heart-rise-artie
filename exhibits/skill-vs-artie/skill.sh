#!/bin/sh
# Way 3: a model does the release every time. Claude Code, headless (claude -p --bare), with the artie-by-hand skill
# as its system prompt and curl as its only way to reach the systems, for each of merge requests 501 to 505, TIMES
# times over. Each release starts from GitLab reseeded and the parties fresh. Bash allows only curl, so the model
# itself decides every request, each wait included: no loop or script can wait for it. Capped at CAP seconds and
# BUDGET dollars a release. It writes no trace of its own (that would only cost it more): the session's stream records
# every request. Writes runs/skill-R-PULL/ (that stream, and the parties' log) and one line
# per release to runs/skill.jsonl: the check of what GitLab and Dev were left with, seconds, dollars and tokens.
#   skill.sh [TIMES] [PULLS]      (default 5, "501 502 503 504 505"; needs GitLab, its gateway on 8094, Jenkins,
#                                  and a model key, read from ~/.artie/credentials as ARTIE reads it, never printed)
set -eu
. "$(dirname "$0")/lib.sh"
times=${1:-5}
pulls=${2:-501 502 503 504 505}
model=${MODEL:-claude-sonnet-5-5}
cap=${CAP:-900}
budget=${BUDGET:-5}
runs="$here/runs"
mkdir -p "$runs"
if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  ANTHROPIC_API_KEY=$(awk '/^\[/{on = ($0 ~ /api\.anthropic\.com/)} on && $1 == "bearer" {print $3; exit}' \
    "$HOME/.artie/credentials")
  export ANTHROPIC_API_KEY
fi
[ -n "$ANTHROPIC_API_KEY" ] || { echo "no model key" >&2; exit 2; }

prompt_of() { # PULL
  IFS='|' read -r book author problem comment << EOF
$(inputs_of "$1")
EOF
  cat << EOF
Carry out this goal by the artie-by-hand skill in your system prompt. You are the client: there is no ARTIE.

The goal, as its author wrote it (Markdown, inputs in its front matter):

$(cat "$release/release.goal.md")

This run's inputs: pull=$1, owner=acme, repo=web, scm=$H:8094/, watcher=$H:8099/, jenkins=$H:8170/, dev=$H:8190/,
environment=1, ${book}, ${author}, renewable=Matilda, ${problem}, ${comment}.

Credentials, by origin, each sent only there:
- localhost:8094 (GitLab, through its HEART gateway): Bearer gitlab-token
- localhost:8099 (the watcher): Bearer watcher-token
- localhost:8170 (Jenkins, through its wrapper): Bearer ci-token
- localhost:8190 (Dev): Bearer env-token
- localhost:8180 (the deployment a build starts, the library app): Basic, library card 21000451, PIN 1234

There is no host to keep replies: ask with no reply, then read, as the skill says under "With no host".
The smoke script a build links is a kept script, in YAML: carry out its steps yourself, by the skill's rules.

Your Bash allows curl only. Don't write a trace file: this run records every request you send. End with the outcome.
EOF
}

for r in $(seq 1 "$times"); do
  for pull in $pulls; do
    dir="$runs/skill-$r-$pull"
    rm -rf "${dir:?}"; mkdir -p "$dir/runs"
    fresh
    parties_up "$dir/parties.log"
    start=$(date +%s)
    (cd "$dir" && perl -e 'alarm shift; exec @ARGV' "$cap" claude -p "$(prompt_of $pull)" --bare --model "$model" \
      --append-system-prompt "$(cat "$root/exhibits/artie-by-hand/SKILL.md")" \
      --tools Bash,Read --allowedTools 'Bash(curl:*)' Read --permission-mode dontAsk \
      --max-budget-usd "$budget" --output-format stream-json --verbose > stream.jsonl 2> stderr.txt) || true
    seconds=$(( $(date +%s) - start ))
    verdict=$(check $pull)
    parties_down
    cost=$(node -e '
      const lines = require("fs").readFileSync(process.argv[1], "utf8").trim().split("\n").filter(Boolean)
        .map(l => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean);
      const result = lines.filter(l => l.type === "result").pop() || {};
      const uses = lines.filter(l => l.type === "assistant").flatMap(l => (l.message?.content || []))
        .filter(c => c.type === "tool_use");
      const curls = uses.filter(c => c.name === "Bash" && /^\s*curl\b/.test(c.input?.command || "")).length;
      const denied = (result.permission_denials || []).length;
      const u = result.usage || {};
      console.log(JSON.stringify({ dollars: result.total_cost_usd ?? null, turns: result.num_turns ?? null,
        ended: result.subtype ?? "killed", requests: curls, denied, input: u.input_tokens, output: u.output_tokens,
        cache_read: u.cache_read_input_tokens, cache_write: u.cache_creation_input_tokens }));
    ' "$dir/stream.jsonl")
    echo "{\"way\":\"skill\",\"run\":$r,\"pull\":$pull,\"seconds\":$seconds,\"model\":$cost,\"check\":$verdict}" \
      | tee -a "$runs/skill.jsonl"
  done
done
