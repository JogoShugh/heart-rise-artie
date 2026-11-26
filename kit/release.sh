#!/bin/sh
# The Merge Watch release on your machine: a pull request watched, merged, built by Jenkins, smoke-tested and
# promoted to Dev, by one kept script with no model, at GitHub or GitLab. Only Docker is needed.
#   kit/release.sh up [gitlab]       builds and starts the parties and Jenkins; with gitlab, GitLab too (about 4 GB)
#   kit/release.sh run github        five releases at the small GitHub inside the kit
#   kit/release.sh run gitlab        the same five at GitLab (after up gitlab)
#   kit/release.sh run github-live   the same five at OWNER/REPO on real GitHub, on library/* branches only
#   kit/release.sh reset [github|gitlab]   the small GitHub, GitLab or both back as first seeded, runs cleared
#   kit/release.sh down              stops everything; GitLab's data is kept
#   kit/release.sh shell             a shell in the release's container
# OWNER, REPO and GITHUB_TOKEN come from kit/.env (kit/env.example), for github-live only. Runs are in ./work.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
. "$here/env.sh"
export KIT_WORK=${WORK:-$PWD/work}
mkdir -p "$KIT_WORK"
compose() { docker compose -f "$here/release/compose.yaml" "$@"; }

usage() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; }
[ $# -gt 0 ] || { usage; exit 0; }
command=$1; shift
case $command in
  up)
    profile=""; [ "${1:-}" = gitlab ] && profile="--profile gitlab"
    compose $profile up -d --build
    printf 'waiting for Jenkins'
    until curl -s -o /dev/null http://127.0.0.1:8171/login; do printf '.'; sleep 3; done
    echo " up at http://localhost:8171"
    if [ -n "$profile" ]; then
      printf 'waiting for GitLab (several minutes the first time)'
      until curl -s -o /dev/null http://127.0.0.1:8929/users/sign_in; do printf '.'; sleep 10; done
      echo " up at http://localhost:8929"
    fi
    if [ -n "$profile" ]; then echo "Next: kit/release.sh run gitlab (or run github)"; else echo "Next: kit/release.sh run github"; fi ;;
  run)
    # Built and started as this checkout has them, every time: Docker's cache makes it seconds when nothing changed.
    profile=""; [ "${1:-github}" = gitlab ] && profile="--profile gitlab"
    echo "Building and starting the release's containers (after a pull: a few minutes)…"
    compose $profile up -d --build
    docker exec -i -e GITHUB_TOKEN -e OWNER -e REPO heart-release /kit/in-release.sh variants "${1:-github}" ;;
  reset)
    docker exec -i heart-release /kit/in-release.sh reset "${1:-all}" ;;
  down)
    compose --profile gitlab down ;;
  shell)
    docker exec -it heart-release bash ;;
  *)
    usage; exit 2 ;;
esac
