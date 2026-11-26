#!/bin/sh
# Try ARTIE on GitHub: a goal walked once by Claude, kept as a script, replayed with no model.
#   kit/try.sh tour                          the kept scripts, one after another, with no model (below)
#   kit/try.sh replay NAME [name=value …]    NAME.heart.yaml again, held strictly to its pins
#   kit/try.sh stop   NAME                   ends the watch NAME (closeout, triage): closes its watch issue
#   kit/try.sh seed                          six issues for the goals to work on, by a kept script
#   kit/try.sh plan   NAME                   reads and checks NAME.goal.md; sends nothing
#   kit/try.sh walk   NAME [name=value …]    Claude walks NAME.goal.md, live (needs ANTHROPIC_API_KEY)
#   kit/try.sh keep   NAME                   NAME.heart.yaml and NAME.pins.yaml, from that walk
#   kit/try.sh up                            keeps the systems running between commands, open to your browser
#   kit/try.sh down                          stops them; the next command starts them afresh
#   kit/try.sh reset                         down, then up: the small GitHub and the services as first started
#   kit/try.sh artie  …                      any artie command, in the kit
#   kit/try.sh shell                         a shell in the kit
#   Before the command: --docker-q builds the image quietly; --docker-args "ARGS" passes ARGS to docker build.
# GitHub is a small one in the kit, repository acme/web, unless USE_GITHUB=yes in kit/.env: then it is real GitHub,
# OWNER/REPO, with GITHUB_TOKEN. Each pins file holds to the GitHub it was made at: NAME.pins.yaml to real GitHub,
# NAME.sim.pins.yaml to the small one. Goals, scripts and runs are in ./work.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
# Your keys and repository, from kit/.env when it is there.
. "$here/env.sh"
# Options for the image build, before the command.
build_args="--progress=plain"
while :; do
  case ${1:-} in
    --docker-q) build_args="-q"; shift ;;
    --docker-args) build_args="$build_args ${2:?--docker-args takes ARGS}"; shift 2 ;;
    *) break ;;
  esac
done
work=${WORK:-$PWD/work}
image=${IMAGE:-heart-kit}
name=${KIT_NAME:-heart-kit}
# Where your machine reaches the gateway, the workspace, the build service and the deploy service while the kit is up.
set -- ${KIT_PORTS:-8392 8400 8401 8402} -- "$@"
ports="-p 127.0.0.1:$1:8392 -p 127.0.0.1:$2:8400 -p 127.0.0.1:$3:8401 -p 127.0.0.1:$4:8402"
gateway_port=$1
addresses="GitHub, through the gateway: http://localhost:$1/
The workspace:                http://localhost:$2/workspace.json
The build service:            http://localhost:$3/jobs/release
The deploy service:           http://localhost:$4/apps/web"
shift 5

usage() { sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; }
[ $# -gt 0 ] || { usage; exit 0; }
command=$1; shift

# Real GitHub only when opted in; otherwise the small GitHub, whatever kit/.env holds.
case $(printf '%s' "${USE_GITHUB:-}" | tr 'A-Z' 'a-z') in
  yes | y | true | on | 1) real=yes ;;
  *) real="" ;;
esac
if [ -n "$real" ]; then
  [ -n "${GITHUB_TOKEN:-}" ] || { echo "USE_GITHUB is yes: set GITHUB_TOKEN in kit/.env (kit/setup.sh asks)" >&2; exit 2; }
  pins_of() { echo "$1.pins.yaml"; }
  github="$OWNER/$REPO on GitHub"
  mode=real
else
  GITHUB_TOKEN=""; OWNER=acme; REPO=web
  pins_of() { echo "$1.sim.pins.yaml"; }
  github="acme/web at the small GitHub in the kit"
  mode=small
fi
export GITHUB_TOKEN

mkdir -p "$work/runs"
# Built every time, so an image from an older checkout is never used; unchanged layers come from Docker's cache.
# Docker's own progress, step by step: seconds when nothing changed, minutes the first time or after a pull.
echo "Building the kit image (the first time or after a pull: a few minutes)…"
docker build $build_args -f "$here/Dockerfile" -t "$image" "$here/.." || { echo "The kit didn't build." >&2; exit 1; }
echo "Built."

# A terminal for the container only when this command has one in and out, asked at each call: the tour pipes both.
tty() { if [ -t 0 ] && [ -t 1 ]; then echo -t; fi; }
# Up, and with the GitHub asked for now: a kit up with the other one is not this one.
running() {
  [ "$(docker inspect -f '{{.State.Running}} {{index .Config.Labels "heart-kit.github"}} {{.Image}}' "$name" 2> /dev/null)" \
    = "true $mode $(docker image inspect -f '{{.Id}}' "$image")" ]
}
# While the kit is up, each command runs in it, on the systems as earlier commands left them; else in a kit of its own.
kit() {
  if running; then
    docker exec -i $(tty) -e GITHUB_TOKEN -e ANTHROPIC_API_KEY "$name" /kit/in.sh "$@"
  else
    docker run --rm -i $(tty) -e GITHUB_TOKEN -e ANTHROPIC_API_KEY -v "$work:/work" "$image" "$@"
  fi
}
need() { eval "[ -n \"\${$1:-}\" ]" || { echo "Set $1: $2" >&2; exit 2; }; }
repository() { need OWNER "your GitHub account"; need REPO "the scratch repository to work on"; }
up() {
  running && return
  printf 'Starting the kit: GitHub gateway, workspace, build and deploy services'
  down
  docker run -d --name "$name" --label "heart-kit.github=$mode" $ports -e GITHUB_TOKEN -e ANTHROPIC_API_KEY \
    -v "$work:/work" "$image" tail -f /dev/null > /dev/null
  # The entrypoint starts each system and waits for it, then becomes the command given, which holds the kit up.
  tries=0
  until docker exec "$name" sh -c 'tr "\0" " " < /proc/1/cmdline' 2> /dev/null | grep -q '^tail '; do
    tries=$((tries + 1))
    [ "$tries" -le 120 ] || { echo; echo "The kit didn't start in 120 s. Its log:" >&2; docker logs "$name" >&2; exit 1; }
    printf .; sleep 1
  done
  echo " up ($tries s)"
}
down() { docker rm -f "$name" > /dev/null 2>&1 || true; }
replay() { # NAME [name=value …]
  goal=$1; shift
  [ -f "$work/$goal.heart.yaml" ] || { echo "no $goal.heart.yaml in $work: walk and keep it first" >&2; exit 2; }
  # A script with a reason step, or written in words only, asks the model on every run.
  model=""
  if grep -q -e 'reasoned: true' -e '^kind: intent' "$work/$goal.heart.yaml"; then
    need ANTHROPIC_API_KEY "$goal asks the model on every run; it needs a key for Claude"
    model="--reasoner claude"
  fi
  pins=$(pins_of "$goal")
  guard="--guard strict --pins $pins"
  # A script kept from your own walk has its pins beside it, made at the GitHub you walked; none for the other.
  [ -f "$work/$pins" ] || { echo "no $pins in $work: replaying $goal unguarded" >&2; guard=""; }
  kit artie run "$goal.heart.yaml" owner="$OWNER" repo="$REPO" $guard $model \
    --trace "runs/$goal-kept.trace.yaml" --exchanges --report "runs/$goal-kept" "$@"
}
# Runs a command quietly, its output kept in runs/LOG.out: its last line when it ends well, its last lines when not.
quiet() { # LOG command …
  log="$work/runs/$1.out"; shift
  start=$(date +%s)
  { s=0; "$@" < /dev/null 2>&1 || s=$?; echo "$s" > "$log.status"; } | tee "$log" | awk '{ print "  " $0; fflush() }'
  status=$(cat "$log.status"); rm -f "$log.status"
  [ "$status" = 0 ] || { echo "failed after $(($(date +%s) - start)) s; full log: $log" >&2; exit 1; }
  echo "  ($(($(date +%s) - start)) s)"
}
stop_watch() { # NAME
  case $1 in
    closeout) title="Close-out watch" ;;
    triage) title="Triage watch" ;;
    *) echo "stop takes closeout or triage" >&2; exit 2 ;;
  esac
  kit artie run stop-watch.heart.yaml owner="$OWNER" repo="$REPO" title="$title" < /dev/null > "$work/runs/stop-$1.out" 2>&1 \
    || { tail -5 "$work/runs/stop-$1.out" >&2; exit 1; }
  echo "closed \"$title\": the watch ends at its next look"
}
# Runs the watch NAME in the background until it has acted COUNT times, then stops it, and waits for it to end.
watch_then_stop() { # NAME COUNT [name=value …]
  goal=$1 count=$2; shift 2
  out="$work/runs/$goal-kept.out"
  replay "$goal" "$@" < /dev/null > "$out" 2>&1 &
  watcher=$!
  tries=0 shown=0
  until shown=$(show_new "$out" "$shown"); [ "$(grep -c -E '^[0-9]+ act github:(close|close-not-planned|close-duplicate|label)' "$out" 2> /dev/null || true)" -ge "$count" ]; do
    tries=$((tries + 1))
    kill -0 "$watcher" 2> /dev/null || break
    [ "$tries" -le 120 ] || break
    sleep 1
  done
  stop_watch "$goal"
  wait "$watcher" || { show_new "$out" "$shown" > /dev/null; echo "failed; full log: $out" >&2; return 1; }
  show_new "$out" "$shown" > /dev/null
}
# Prints, indented, the lines of FILE after its first SHOWN (to stderr, so it shows at once), and the new count.
show_new() { # FILE SHOWN
  total=$(wc -l < "$1" 2> /dev/null || echo 0)
  [ "$total" -le "$2" ] || sed -n "$(($2 + 1)),${total}p" "$1" | sed 's/^/  /' >&2
  echo "$total"
}

case $command in
  tour)
    # The kept scripts, with no model: on the small GitHub by default, or on yours with USE_GITHUB=yes.
    echo "The tour, at $github, with no model:"
    down; up
    echo; echo "seed: six issues, three waiting for triage and three ready to close"
    quiet seed kit artie run seed.heart.yaml owner="$OWNER" repo="$REPO" --trace runs/seed.trace.yaml
    echo; echo "hello: open an issue, comment on it, close it"
    quiet hello-kept replay hello
    echo; echo "closeout: close the three ready-to-close issues, each as its labels say, then end the watch"
    watch_then_stop closeout 3 version=1.0
    echo; echo "release: queue a build, deploy it to staging, record it on GitHub"
    quiet release-kept replay release version=1.1
    echo
    if [ -n "${ANTHROPIC_API_KEY:-}" ] && [ "$(printf '%s' "${USE_ANTHROPIC:-}" | tr 'A-Z' 'a-z')" = yes ]; then
      echo "triage: label the three waiting issues; its one reasoned step asks Claude"
      watch_then_stop triage 6
    else
      echo "triage: skipped; its one reasoned step asks the model (USE_ANTHROPIC=yes in kit/.env)"
    fi
    echo
    echo "Each run's report: work/runs/NAME-kept/index.html. The kit stays up until kit/try.sh down:"
    echo "$addresses"
    [ -n "$real" ] || echo "(the small GitHub's credential: github-token)" ;;
  plan)
    kit artie run "$1.goal.md" --plan-only --reasoner claude ;;
  walk)
    repository; need ANTHROPIC_API_KEY "a key for Claude, which finds the way"
    goal=$1; shift
    kit artie run "$goal.goal.md" owner="$OWNER" repo="$REPO" --reasoner claude --trace "runs/$goal.trace.yaml" \
      --exchanges --report "runs/$goal" "$@" ;;
  keep)
    kit artie generate "runs/$1.trace.yaml" --script "$1.goal.md" --out "$1.heart.yaml" --format yaml
    # What generate pins is what the walk was offered: at the small GitHub, that is the small GitHub's pins.
    if [ -z "$real" ]; then mv "$work/$1.pins.yaml" "$work/$1.sim.pins.yaml"; echo "pins kept as $1.sim.pins.yaml"; fi ;;
  replay)
    repository; replay "$@" ;;
  stop)
    stop_watch "$1" ;;
  seed)
    kit artie run seed.heart.yaml owner="$OWNER" repo="$REPO" --trace runs/seed.trace.yaml ;;
  up)
    up
    echo "The kit is up, until kit/try.sh down, with $github."
    echo "Each system answers a browser with its own pages, given its credential:"
    echo "$addresses"
    [ -n "$real" ] || echo "(the small GitHub's credential: github-token)" ;;
  down)
    down ;;
  reset)
    down; up
    echo "The kit is up again, every system as first started, with $github."
    [ -z "$real" ] || echo "Real GitHub keeps what runs did there; kit/bootstrap.sh seeds it again." ;;
  artie)
    kit artie "$@" ;;
  shell)
    kit bash ;;
  *)
    usage; exit 2 ;;
esac
