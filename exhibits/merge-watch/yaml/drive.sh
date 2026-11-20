#!/bin/zsh
# One merge-watch run on the scratch repository, as the earlier local-model runs were driven: a fresh bump that can be
# merged and a draft that can't, both labelled dependencies; then the watch; then, once the bump is merged and the
# draft's author told (or after 12 minutes), the watch's issue closed, as a person would, which ends the watch.
#   drive.sh NAME SCRIPT [MODEL]        (no MODEL: Claude)
set -u
name=$1 script=$2 model=${3:-}
cd /Users/josh/repos/artie/client/scripts && . ./.env
export ARTIE_TOKEN_LOCALHOST_8393=$ARTIE_TOKEN_LOCALHOST_8392
export JAVA_OPTS="-Djavax.net.ssl.trustStore=$HOME/.cache/artie/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"
R=/Users/josh/repos/artie; W=$R/exhibits/merge-watch; OUT=$W/runs/yaml; mkdir -p $OUT
API=https://api.github.com/repos/JogoShugh/heart-github-test
gh() { curl -s -H "Authorization: Bearer $ARTIE_TOKEN_LOCALHOST_8392" -H "Accept: application/vnd.github+json" "$@"; }
pick() { node -e "$1" ; }

cd $R/exhibits/dependabot-sim
: > $OUT/$name.stage.out
# GitHub answers 500 now and then to a pull request's creation: try again, with a new version, up to four times.
stage() {  # version-suffix, extra bump arguments...; prints the pull request's number
  local suffix=$1; shift
  for try in 1 2 3 4; do
    local v=$(( $(date +%s) % 100000 + 100 )).$suffix
    artie run bump.kts owner=JogoShugh to=$v label=dependencies "$@" >> $OUT/$name.stage.out 2>&1
    local num=$(gh "$API/pulls?state=open&per_page=100&sort=created&direction=desc" |
      pick "const p=JSON.parse(require('fs').readFileSync(0)); console.log(p.find(x=>x.title.includes('to $v ('))?.number ?? '')")
    [[ -n $num ]] && { print $num; return; }
    sleep 5
  done
}
bump=$(stage 0.0)
draft=$(stage 0.1 file=deps-draft.txt draft=true)
[[ -z $bump || -z $draft ]] && { echo "$name: staging failed (bump=$bump draft=$draft)"; exit 1; }

args=(owner=JogoShugh --reasoner claude --entry https://localhost:8393/ --exchanges --trace $OUT/$name.trace.yaml)
# The lock file beside the script, named outright: artie looks for NAME.pins.yaml in the working folder, not beside it.
[[ $script == *.heart.yaml ]] && args+=(--pins ${script%.heart.yaml}.pins.yaml)
[[ -n $model ]] && args+=(--model $model --model-url http://localhost:11434 --max-tokens 1024)
cd $W
start=$(date +%s)
close_watches() {  # every watch issue opened since $1, closed
  local since=$(date -u -r $1 +%Y-%m-%dT%H:%M:%SZ)
  for w in $(gh "$API/issues?state=open&per_page=50&since=$since" | pick "const p=JSON.parse(require('fs').readFileSync(0)); console.log(p.filter(x=>x.title==='Merge watch' && !x.pull_request && x.created_at>='$since').map(x=>x.number).join(' '))"); do
    gh -X PATCH "$API/issues/$w" -d '{"state":"closed"}' > /dev/null; closed=$w
  done
}
attempts=0
merged=false told=0 closed=""
while (( attempts < 3 )); do
  attempts=$(( attempts + 1 )); began=$(date +%s); closed=""
  artie run $script $args > $OUT/$name.out 2>&1 &
  pid=$!
  while kill -0 $pid 2>/dev/null; do
    sleep 15
    merged=$(gh "$API/pulls/$bump" | pick "console.log(JSON.parse(require('fs').readFileSync(0)).merged)")
    told=$(gh "$API/issues/$draft/comments" | pick "console.log(JSON.parse(require('fs').readFileSync(0)).length)")
    if [[ -z $closed ]] && { [[ $merged == true && $told -gt 0 ]] || (( $(date +%s) - began > 720 )); }; then
      close_watches $began
      stopped=$(date +%s)
    fi
    if [[ -n $closed ]] && (( $(date +%s) - stopped > 240 )); then kill $pid; break; fi
  done
  wait $pid 2>/dev/null
  merged=$(gh "$API/pulls/$bump" | pick "console.log(JSON.parse(require('fs').readFileSync(0)).merged)")
  told=$(gh "$API/issues/$draft/comments" | pick "console.log(JSON.parse(require('fs').readFileSync(0)).length)")
  # GitHub's passing 500s can end a run where it stands (an exception, not a look ARTIE tries again): close what it
  # opened, and try again on the same bump and draft, only if the work isn't done.
  if grep -q "^Exception.*was refused (500)" $OUT/$name.out && [[ $merged != true || $told -eq 0 ]]; then
    close_watches $began; cp $OUT/$name.out $OUT/$name.attempt-$attempts.out; continue
  fi
  break
done
secs=$(( $(date +%s) - start ))
calls=$(grep -m1 -A2 "^  usage:" $OUT/$name.trace.yaml 2>/dev/null | awk '/calls:/{print $2}')
output=$(grep -m1 -A3 "^  usage:" $OUT/$name.trace.yaml 2>/dev/null | awk '/output:/{print $2}')
echo "$name | model=${model:-claude} | attempts=$attempts | bump #$bump merged=$merged | draft #$draft told=$told | watch #${closed:-none} | ${secs}s | calls=${calls:-?} output=${output:-?} | $(tail -1 $OUT/$name.out)"
