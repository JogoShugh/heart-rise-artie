#!/bin/sh
# Releases each variant through the release desk, as a person asks it: a pull request, and what its smoke test is
# given. The same kept scripts every time, the captured smoke test among them, with no model: only the inputs vary.
# Each release starts from main as made, the parties started fresh; Jenkins (jenkins/run.sh) runs throughout.
# Writes each run's trace, and what the desk said of it, to OUT (default: docs/reports/merge-watch-release/runs).
#   variants.sh [OUT]
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=${1:-$root/docs/reports/merge-watch-release/runs}
host=${RELEASE_HOST:-192.168.1.18}
H="http://$host"
DESK="Authorization: Bearer desk-token"
mkdir -p "$out"
. "$here/jenkins/jenkins.env.example"
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_HOOK_SECRET RELEASE_HOST="$host"
export RELEASE_GITHUB_CONNECTOR=${RELEASE_GITHUB_CONNECTOR:-github-scm}
export RELEASE_REPOSITORY="$here/work/library-app" RELEASE_SCRIPT="$here/release.heart.yaml"
export JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}

release() { # pull fields-json
  pull=$1 fields=$2
  sh "$here/make-app-repo.sh" >/dev/null
  "$root/client/artie-release/build/install/artie-release/bin/artie-release" > "$out/pr-$pull.parties.log" 2>&1 &
  parties=$!
  until curl -sf -o /dev/null -H "$DESK" "$H:8160/"; do sleep 1; done
  curl -sf -X PUT -d null "$H:8098/_release/github/pulls/$pull/mergeable"
  etag=$(curl -sfi -H "$DESK" -H 'Accept: application/hal+json' "$H:8160/" | tr -d '\r' \
    | awk -F': ' 'tolower($1)=="etag"{print $2}')
  loc=$(curl -sfi -X POST -H "$DESK" -H "If-Match: $etag" -H 'Accept: application/hal+json' \
    -H 'Content-Type: application/json' -d "{\"pull\": $pull, $fields}" "$H:8160/actions/release" \
    | tr -d '\r' | awk -F': ' 'tolower($1)=="location"{print $2; exit}')
  sleep 6
  curl -sf -X PUT -d true "$H:8098/_release/github/pulls/$pull/mergeable"
  while :; do
    said=$(curl -sf -H "$DESK" -H 'Accept: application/json' "$H:8160$loc")
    case "$said" in *'"state":"running"'*) sleep 3 ;; *) break ;; esac
  done
  printf '%s\n' "$said" > "$out/pr-$pull.desk.json"
  curl -sf -H "$DESK" "$H:8160$loc/trace" > "$out/pr-$pull.yaml"
  curl -sf -H 'Authorization: Bearer env-token' -H 'Accept: application/json' "$H:8190/environments/1" \
    > "$out/pr-$pull.dev.json"
  kill "$parties"; wait "$parties" 2>/dev/null || true
  echo "pr-$pull: $said"
}

release 501 '"book": "Charlotte'"'"'s Web", "author": "E. B. White", "problem": "damaged", "comment": "Spine cracked."'
release 502 '"book": "The Hobbit", "author": "J. R. R. Tolkien", "problem": "wrong-book", "comment": "This is the sequel."'
release 503 '"book": "Matilda", "author": "Roald Dahl", "problem": "lost", "comment": "Not on the shelf at home."'
release 504 '"book": "Charlotte'"'"'s Web", "author": "E. B. White", "problem": "missing-pages", "comment": "Pages 40 to 44."'
release 505 '"book": "The Hobbit", "author": "J. R. R. Tolkien", "problem": "damaged", "comment": "Water damage on the cover."'
