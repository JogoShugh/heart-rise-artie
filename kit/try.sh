#!/bin/sh
# Try ARTIE on a GitHub repository of your own: a goal walked once by Claude, kept as a script, replayed with no model.
#   kit/try.sh plan   NAME                   reads and checks NAME.goal.md; sends nothing
#   kit/try.sh walk   NAME [name=value …]    Claude walks NAME.goal.md, live, on your repository
#   kit/try.sh keep   NAME                   NAME.heart.yaml and NAME.pins.yaml, from that walk
#   kit/try.sh replay NAME [name=value …]    NAME.heart.yaml again, held strictly to NAME.pins.yaml
#   kit/try.sh artie  …                      any artie command, in the kit
#   kit/try.sh shell                         a shell in the kit
# Needs OWNER, REPO and GITHUB_TOKEN set, and ANTHROPIC_API_KEY for walk. Goals, scripts and runs are in ./work.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
work=${WORK:-$PWD/work}
image=${IMAGE:-heart-kit}

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; }
[ $# -gt 0 ] || { usage; exit 0; }
command=$1; shift

docker image inspect "$image" > /dev/null 2>&1 || docker build -f "$here/Dockerfile" -t "$image" "$here/.."
mkdir -p "$work"

tty=""; [ -t 0 ] && [ -t 1 ] && tty="-t"
kit() { docker run --rm -i $tty -e GITHUB_TOKEN -e ANTHROPIC_API_KEY -v "$work:/work" "$image" "$@"; }
need() { eval "[ -n \"\${$1:-}\" ]" || { echo "Set $1: $2" >&2; exit 2; }; }
repository() { need OWNER "your GitHub account"; need REPO "the scratch repository to work on"; }

case $command in
  plan)
    kit artie run "$1.goal.md" --plan-only --reasoner claude ;;
  walk)
    repository; need ANTHROPIC_API_KEY "a key for Claude, which finds the way"
    name=$1; shift
    kit artie run "$name.goal.md" owner="$OWNER" repo="$REPO" --reasoner claude --trace "runs/$name.trace.yaml" "$@" ;;
  keep)
    kit artie generate "runs/$1.trace.yaml" --script "$1.goal.md" --out "$1.heart.yaml" --format yaml ;;
  replay)
    repository
    name=$1; shift
    # A script with a reason step, or written in words only, asks the model on every run.
    model=""; grep -q -e 'reasoned: true' -e '^kind: intent' "$work/$name.heart.yaml" && model="--reasoner claude"
    kit artie run "$name.heart.yaml" owner="$OWNER" repo="$REPO" --guard strict $model --trace "runs/$name-kept.trace.yaml" "$@" ;;
  artie)
    kit artie "$@" ;;
  shell)
    kit bash ;;
  *)
    usage; exit 2 ;;
esac
