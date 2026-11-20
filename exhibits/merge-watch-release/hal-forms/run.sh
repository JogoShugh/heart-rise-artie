#!/bin/sh
# The release in HAL-FORMS: walked once with Claude (RELEASE_DIALECT=hal-forms), kept by artie generate alone,
# then the five variants replayed with no model. Writes only into this folder; the HAL Schema Forms files beside it
# are left as they are.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
X=$(cd "$here/.." && pwd)
root=$(cd "$X/../.." && pwd)
export JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal}
. "$X/jenkins/jenkins.env.example"
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_HOOK_SECRET RELEASE_HOST=localhost
export RELEASE_GITHUB_CONNECTOR=github-scm RELEASE_REPOSITORY="$X/work/library-app" RELEASE_DIALECT=hal-forms
export RELEASE_SCRIPT="$X/release.heart.yaml"
sh "$X/jenkins/run.sh" | tail -1
if [ "${1:-}" != replay ]; then
  sh "$X/make-app-repo.sh" >/dev/null
  "$root/client/artie-release/build/install/artie-release/bin/artie-release" \
    capture "$X/release.goal.md" "$here/release.capture.yaml" pull=501 > "$here/capture.log" 2>&1 &
  cap=$!
  until curl -sf -o /dev/null -H "Authorization: Bearer desk-token" localhost:8160/; do sleep 1; done
  curl -sf -X PUT -d null localhost:8098/_release/github/pulls/501/mergeable; sleep 8
  curl -sf -X PUT -d true localhost:8098/_release/github/pulls/501/mergeable
  wait "$cap"; echo "capture: $(tail -1 "$here/capture.log")"
  "$root/client/artie-cli/build/install/artie/bin/artie" generate "$here/release.capture.yaml" \
    --out "$here/release.heart.yaml" --script "$X/release.goal.md" --format yaml
fi
sed -e "s|^here=.*|here=$X|" -e 's|RELEASE_SCRIPT="\$here/release.heart.yaml"|RELEASE_SCRIPT="'"$here"'/release.heart.yaml"|' \
  "$X/variants.sh" > "$here/.variants.sh"
sh "$here/.variants.sh" "$here/runs" 2>&1 | grep -E '^pr-' | sed -E 's/^(pr-[0-9]+): .*"state":"([a-z]+)".*/\1: \2/'
for n in 501 502 503 504 505; do printf "pr-%s %s\n" $n "$(grep -oE '"promotedAt":("[^"]*"|null)' "$here/runs/pr-$n.dev.json")"; done
docker stop merge-release-jenkins >/dev/null
