#!/bin/zsh
# One close-out watch on the scratch repository: three issues staged, each labelled ready-to-close and one of
# shipped, wontfix or duplicate; then the watch; once all three are closed (or after 8 minutes), the watch's issue
# closed, as a person would, which ends it. Prints how each issue closed.
#   closeout.sh NAME SCRIPT [artie run arguments...]
set -u
name=$1 script=$2; shift 2
here=${0:A:h}
cd /Users/josh/repos/artie/client/scripts && . ./.env
export ARTIE_TOKEN_LOCALHOST_8393=$ARTIE_TOKEN_LOCALHOST_8392
export JAVA_OPTS="-Djavax.net.ssl.trustStore=$HOME/.cache/artie/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"
export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/openjdk-22.0.1/Contents/Home
[[ " $* " == *" --reasoner "* ]] && export ANTHROPIC_API_KEY=$(cat ~/.anthropic/api_key)
API=https://api.github.com/repos/JogoShugh/heart-github-test
gh() { curl -s -H "Authorization: Bearer $ARTIE_TOKEN_LOCALHOST_8392" -H "Accept: application/vnd.github+json" "$@"; }
field() { node -e "const o=JSON.parse(require('fs').readFileSync(0)); console.log($1)"; }
cd $here && mkdir -p runs

for l in ready-to-close shipped wontfix duplicate; do gh -X POST $API/labels -d "{\"name\":\"$l\"}" > /dev/null; done
typeset -A staged
for kind in shipped wontfix duplicate; do
  staged[$kind]=$(gh -X POST $API/issues -d "{\"title\":\"Close-out check: $kind ($name)\",\"body\":\"Staged for the close-out watch.\",\"labels\":[\"ready-to-close\",\"$kind\"]}" | field o.number)
done
echo "$name staged: shipped #$staged[shipped], wontfix #$staged[wontfix], duplicate #$staged[duplicate]"

pins=()
[[ -f ${script%.heart.yaml}.pins.yaml ]] && pins=(--pins ${script%.heart.yaml}.pins.yaml)
began=$(date +%s)
since=$(date -u -r $began +%Y-%m-%dT%H:%M:%SZ)
/Users/josh/repos/artie/client/artie-cli/build/install/artie/bin/artie run $script owner=JogoShugh version=1.3 \
  $pins --exchanges --trace runs/$name.trace.yaml "$@" > runs/$name.out 2>&1 &
pid=$!
closed=""
while kill -0 $pid 2>/dev/null; do
  sleep 15
  open=0
  for kind in shipped wontfix duplicate; do
    [[ $(gh $API/issues/$staged[$kind] | field o.state) == open ]] && open=$((open + 1))
  done
  if [[ -z $closed ]] && { (( open == 0 )) || (( $(date +%s) - began > 480 )); }; then
    for w in $(gh "$API/issues?state=open&per_page=50&since=$since" | field "o.filter(x=>x.title==='Close-out watch').map(x=>x.number).join(' ')"); do
      gh -X PATCH $API/issues/$w -d '{"state":"closed"}' > /dev/null; closed=$w
    done
    stopped=$(date +%s)
  fi
  [[ -n $closed ]] && (( $(date +%s) - stopped > 180 )) && { kill $pid; break; }
done
wait $pid 2>/dev/null
for kind in shipped wontfix duplicate; do
  echo "$name #$staged[$kind] labelled $kind: $(gh $API/issues/$staged[$kind] | field '[o.state, o.state_reason].join(" ")'), comments: $(gh $API/issues/$staged[$kind]/comments | field 'o.map(c=>c.body).join(" | ")')"
done
echo "$name: watch #${closed:-none}, $(( $(date +%s) - began )) s, $(tail -1 runs/$name.out)"
