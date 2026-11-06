#!/bin/sh
# Try ARTIE on a GitHub repository of your own: a goal walked once by Claude, kept as a script, replayed with no model.
#   kit/try.sh plan   NAME                   reads and checks NAME.goal.md; sends nothing
#   kit/try.sh walk   NAME [name=value …]    Claude walks NAME.goal.md, live, on your repository
#   kit/try.sh keep   NAME                   NAME.heart.yaml and NAME.pins.yaml, from that walk
#   kit/try.sh replay NAME [name=value …]    NAME.heart.yaml again, held strictly to NAME.pins.yaml
#   kit/try.sh up                            keeps the systems running between commands, open to your browser
#   kit/try.sh down                          stops them; the next command starts them afresh
#   kit/try.sh artie  …                      any artie command, in the kit
#   kit/try.sh shell                         a shell in the kit
# Needs OWNER, REPO and GITHUB_TOKEN set, and ANTHROPIC_API_KEY for walk. Goals, scripts and runs are in ./work.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
work=${WORK:-$PWD/work}
image=${IMAGE:-heart-kit}
name=${KIT_NAME:-heart-kit}
# Where your machine reaches the gateway, the workspace, the build service and the deploy service while the kit is up.
set -- ${KIT_PORTS:-8392 8400 8401 8402} -- "$@"
ports="-p 127.0.0.1:$1:8392 -p 127.0.0.1:$2:8400 -p 127.0.0.1:$3:8401 -p 127.0.0.1:$4:8402"
addresses="GitHub, through the gateway: http://localhost:$1/
The workspace:                http://localhost:$2/workspace.json
The build service:            http://localhost:$3/jobs/release
The deploy service:           http://localhost:$4/apps/web"
shift 5

usage() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; }
[ $# -gt 0 ] || { usage; exit 0; }
command=$1; shift

docker image inspect "$image" > /dev/null 2>&1 || docker build -f "$here/Dockerfile" -t "$image" "$here/.."
mkdir -p "$work"

tty=""; [ -t 0 ] && [ -t 1 ] && tty="-t"
running() { [ "$(docker inspect -f '{{.State.Running}}' "$name" 2> /dev/null)" = true ]; }
# While the kit is up, each command runs in it, on the systems as earlier commands left them; else in a kit of its own.
kit() {
  if running; then
    docker exec -i $tty -e GITHUB_TOKEN -e ANTHROPIC_API_KEY "$name" /kit/in.sh "$@"
  else
    docker run --rm -i $tty -e GITHUB_TOKEN -e ANTHROPIC_API_KEY -v "$work:/work" "$image" "$@"
  fi
}
need() { eval "[ -n \"\${$1:-}\" ]" || { echo "Set $1: $2" >&2; exit 2; }; }
repository() { need OWNER "your GitHub account"; need REPO "the scratch repository to work on"; }

case $command in
  plan)
    kit artie run "$1.goal.md" --plan-only --reasoner claude ;;
  walk)
    repository; need ANTHROPIC_API_KEY "a key for Claude, which finds the way"
    goal=$1; shift
    kit artie run "$goal.goal.md" owner="$OWNER" repo="$REPO" --reasoner claude --trace "runs/$goal.trace.yaml" \
      --exchanges --report "runs/$goal" "$@" ;;
  keep)
    kit artie generate "runs/$1.trace.yaml" --script "$1.goal.md" --out "$1.heart.yaml" --format yaml ;;
  replay)
    repository
    goal=$1; shift
    # A script with a reason step, or written in words only, asks the model on every run.
    model=""; grep -q -e 'reasoned: true' -e '^kind: intent' "$work/$goal.heart.yaml" && model="--reasoner claude"
    kit artie run "$goal.heart.yaml" owner="$OWNER" repo="$REPO" --guard strict $model \
      --trace "runs/$goal-kept.trace.yaml" --exchanges --report "runs/$goal-kept" "$@" ;;
  up)
    need GITHUB_TOKEN "a fine-grained token for your scratch repository"
    running || docker run -d --name "$name" $ports -e GITHUB_TOKEN -e ANTHROPIC_API_KEY -v "$work:/work" \
      "$image" tail -f /dev/null > /dev/null
    # The entrypoint starts each system and waits for it, then becomes the command given, which holds the kit up.
    tries=0
    until docker exec "$name" sh -c 'tr "\0" " " < /proc/1/cmdline' 2> /dev/null | grep -q '^tail '; do
      tries=$((tries + 1))
      [ "$tries" -le 120 ] || { docker logs "$name" >&2; exit 1; }
      sleep 1
    done
    echo "The kit is up, until kit/try.sh down. Each system answers a browser with its own pages, given its credential:"
    echo "$addresses" ;;
  down)
    docker rm -f "$name" > /dev/null 2>&1 || true ;;
  artie)
    kit artie "$@" ;;
  shell)
    kit bash ;;
  *)
    usage; exit 2 ;;
esac
