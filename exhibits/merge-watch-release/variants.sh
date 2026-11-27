#!/bin/sh
# Releases each variant through the release desk, as a person asks it: a pull request, and what its smoke test is
# given. The same kept scripts every time, the captured smoke test among them, with no model: only the inputs vary.
# Each release starts from main as made, the parties started fresh; Jenkins (jenkins/run.sh) runs throughout.
# Writes each run's trace, and what the desk said of it, to OUT (default: docs/reports/merge-watch-release/runs).
# With RELEASE_SCM=gitlab, each release is at GitLab (gitlab/run.sh, gateway.sh) instead, seeded afresh; GitLab works
# out mergeability itself. With RELEASE_SCM=github-live, at real GitHub (github/gateway.sh), each variant a pull request
# of its own number, opened afresh by github/seed.sh. With RELEASE_ONE_DESK=1, one desk runs every release, so what
# its ARTIE cached in one release, the next reads with no request.
#   variants.sh [OUT]
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=${1:-$root/docs/reports/merge-watch-release/runs}
host=${RELEASE_HOST:-localhost}
H="http://$host"
DESK="Authorization: Bearer desk-token"
mkdir -p "$out"
scm=${RELEASE_SCM:-github}
at=""
[ "$scm" = gitlab ] && at="\"scm\": \"$H:8094/\", "
slug=${RELEASE_GITHUB_REPO:-JogoShugh/heart-github-test}
. "$here/jenkins/jenkins.env.example"
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_HOOK_SECRET RELEASE_HOST="$host"
export RELEASE_GITHUB_CONNECTOR=${RELEASE_GITHUB_CONNECTOR:-github-scm}
export RELEASE_REPOSITORY="$here/work/library-app" RELEASE_SCRIPT="${RELEASE_SCRIPT:-$here/release.heart.yaml}"
# The JDK this Mac runs with, when it is there; elsewhere, JAVA_HOME as set, or the java on the PATH (22 or newer).
mac_java=$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal
[ -n "${JAVA_HOME:-}" ] || [ ! -d "$mac_java" ] || export JAVA_HOME="$mac_java"

done_count=0 promoted=0 held=0
release() { # pull fields-json
  pull=$1 fields=$2
  began=$(date +%s)
  echo "pr-$pull: watch, merge, build on Jenkins, smoke-test, promote to Dev…"
  sh "$here/make-app-repo.sh" >/dev/null
  if [ "$scm" = gitlab ]; then
    echo "pr-$pull: GitLab reseeded first (about a minute; log: $out/pr-$pull.seed.log)…"
    sh "$here/gitlab/seed.sh" > "$out/pr-$pull.seed.log" 2>&1 &
    seeding=$! waited=0
    while kill -0 "$seeding" 2> /dev/null; do
      sleep 1; waited=$((waited + 1))
      [ $((waited % 15)) -ne 0 ] || echo "pr-$pull: reseeding GitLab, $waited s"
    done
    wait "$seeding" || { echo "pr-$pull: GitLab's seed failed: $out/pr-$pull.seed.log" >&2; exit 1; }
  fi
  number=$pull
  if [ "$scm" = github-live ]; then
    number=$(sh "$here/github/seed.sh" "$pull" 2> "$out/pr-$pull.seed.log")
    at="\"scm\": \"$H:8093/\", \"owner\": \"${slug%%/*}\", \"repo\": \"${slug#*/}\", "
    echo "variant $pull is pull request $number of $slug"
  fi
  if [ -z "${parties:-}" ]; then
    "$root/client/artie-release/build/install/artie-release/bin/artie-release" > "$out/pr-$pull.parties.log" 2>&1 &
    parties=$!
  fi
  until curl -sf -o /dev/null -H "$DESK" "$H:8160/"; do sleep 1; done
  [ "$scm" = github ] && curl -sf -X PUT -d null "$H:8098/_release/github/pulls/$pull/mergeable"
  etag=$(curl -sfi -H "$DESK" -H 'Accept: application/hal+json' "$H:8160/" | tr -d '\r' \
    | awk -F': ' 'tolower($1)=="etag"{print $2}')
  loc=$(curl -sfi -X POST -H "$DESK" -H "If-Match: $etag" -H 'Accept: application/hal+json' \
    -H 'Content-Type: application/json' -d "{\"pull\": $number, $at$fields}" "$H:8160/actions/release" \
    | tr -d '\r' | awk -F': ' 'tolower($1)=="location"{print $2; exit}')
  sleep 6
  [ "$scm" = github ] && curl -sf -X PUT -d true "$H:8098/_release/github/pulls/$pull/mergeable"
  # A line every 15 seconds while it runs, so a person sees it is still going.
  echo "pr-$pull: the release desk's ARTIE is running the kept script…"
  next=$(( $(date +%s) - began + 15 ))
  while :; do
    said=$(curl -sf -H "$DESK" -H 'Accept: application/json' "$H:8160$loc")
    case "$said" in *'"state":"running"'*) ;; *) break ;; esac
    took=$(( $(date +%s) - began ))
    [ "$took" -lt "$next" ] || { echo "pr-$pull: running, $took s"; next=$((took + 15)); }
    # A run that failed inside the desk can stay "running": stop waiting after RELEASE_CAP seconds, and say where to look.
    [ "$took" -lt "${RELEASE_CAP:-300}" ] || {
      echo "pr-$pull: no end after $took s; the parties' log: $out/pr-$pull.parties.log" >&2
      grep -m1 -i 'exception' "$out/pr-$pull.parties.log" >&2 || true
      break
    }
    sleep 3
  done
  printf '%s\n' "$said" > "$out/pr-$pull.desk.json"
  curl -sf -H "$DESK" "$H:8160$loc/trace" > "$out/pr-$pull.yaml"
  curl -sf -H 'Authorization: Bearer env-token' -H 'Accept: application/json' "$H:8190/environments/1" \
    > "$out/pr-$pull.dev.json"
  # One desk for every release, when RELEASE_ONE_DESK says so: its HTTP cache outlives each release.
  if [ -z "${RELEASE_ONE_DESK:-}" ]; then kill "$parties"; wait "$parties" 2>/dev/null || true; parties=; fi
  # A look at the forge as each release left it, when RELEASE_SHOT names a script: given the variant and its number.
  [ -n "${RELEASE_SHOT:-}" ] && sh "$RELEASE_SHOT" "$pull" "$number"
  state=$(printf '%s' "$said" | sed -n 's/.*"state":"\([a-z]*\)".*/\1/p')
  case $(cat "$out/pr-$pull.dev.json") in
    *'"promotedAt":null'*) dev="not promoted to Dev" ;;
    *'"promotedAt":"'*) dev="promoted to Dev" ;;
    *) dev="Dev not read" ;;
  esac
  echo "pr-$pull: ${state:-no answer}, $dev ($(( $(date +%s) - began )) s)"
  done_count=$((done_count + 1))
  case $dev in "promoted to Dev") promoted=$((promoted + 1)) ;; *) held=$((held + 1)) ;; esac
  echo "  so far: $done_count of 5 done; $promoted promoted, $held not promoted"
}

release 501 '"book": "Charlotte'"'"'s Web", "author": "E. B. White", "problem": "damaged", "comment": "Spine cracked."'
release 502 '"book": "The Hobbit", "author": "J. R. R. Tolkien", "problem": "wrong-book", "comment": "This is the sequel."'
release 503 '"book": "Matilda", "author": "Roald Dahl", "problem": "lost", "comment": "Not on the shelf at home."'
release 504 '"book": "Charlotte'"'"'s Web", "author": "E. B. White", "problem": "missing-pages", "comment": "Pages 40 to 44."'
release 505 '"book": "The Hobbit", "author": "J. R. R. Tolkien", "problem": "damaged", "comment": "Water damage on the cover."'
[ -z "${parties:-}" ] || { kill "$parties"; wait "$parties" 2>/dev/null || true; }
