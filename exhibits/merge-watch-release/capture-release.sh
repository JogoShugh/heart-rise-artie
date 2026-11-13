#!/bin/sh
# The release goal's one inference run: the parties served, the desk's ARTIE walks release.goal.md once with Claude
# for pull request PULL (default 501), and artie generate keeps the walk as release.heart.yaml, the script every
# release after runs with no model. Needs a model key (ANTHROPIC_API_KEY, or ~/.artie/credentials) and Jenkins
# (jenkins/run.sh). GitHub works out mergeability a few seconds into the watch, as the small GitHub's control says.
# With RELEASE_SCM=github-live, the walk is on real GitHub (github/gateway.sh on 8093): variant PULL seeded there by
# github/seed.sh, which opens a pull request of its own number; GitHub works out mergeability itself.
#   capture-release.sh [PULL]
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
pull=${1:-501}
host=${RELEASE_HOST:-localhost}
H="http://$host"
. "$here/jenkins/jenkins.env.example"
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_HOOK_SECRET RELEASE_HOST="$host"
export RELEASE_GITHUB_CONNECTOR=${RELEASE_GITHUB_CONNECTOR:-github-scm}
export RELEASE_REPOSITORY="$here/work/library-app" RELEASE_SCRIPT="$here/release.heart.yaml"
export JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}
sh "$here/make-app-repo.sh" >/dev/null
scm=${RELEASE_SCM:-github}
set -- "pull=$pull"
if [ "$scm" = github-live ]; then
  slug=${RELEASE_GITHUB_REPO:-JogoShugh/heart-github-test}
  number=$(sh "$here/github/seed.sh" "$pull")
  echo "variant $pull is pull request $number of $slug"
  set -- "pull=$number" "scm=$H:8093/" "owner=${slug%%/*}" "repo=${slug#*/}"
fi
"$root/client/artie-release/build/install/artie-release/bin/artie-release" \
  capture "$here/release.goal.md" "$here/release.capture.yaml" "$@" &
capturing=$!
until curl -sf -o /dev/null -H "Authorization: Bearer desk-token" "$H:8160/"; do sleep 1; done
if [ "$scm" = github ]; then
  curl -sf -X PUT -d null "$H:8098/_release/github/pulls/$pull/mergeable"
  sleep 8
  curl -sf -X PUT -d true "$H:8098/_release/github/pulls/$pull/mergeable"
fi
wait "$capturing"
"$root/client/artie-cli/build/install/artie/bin/artie" generate "$here/release.capture.yaml" \
  --out "$here/release.heart.yaml" --script "$here/release.goal.md" --format yaml
