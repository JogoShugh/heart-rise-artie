#!/bin/sh
# Starts the release's Jenkins on this Mac: jenkins/jenkins:lts-jdk21 with the Notification plugin, an admin from
# jenkins.env (else jenkins.env.example), and job "app", which builds Riverside Public Library at a commit of the
# local repo work/library-app and deploys it as the preview on 8180.
#   Jenkins: http://localhost:8171   preview: http://localhost:8180   notifications to the wrapper on 8170
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
env_file="$here/jenkins.env"
[ -f "$env_file" ] || env_file="$here/jenkins.env.example"
# shellcheck disable=SC1090
. "$env_file"
work="$here/../work"
[ -d "$work/library-app/.git" ] || { echo "no $work/library-app: run ../make-app-repo.sh first"; exit 1; }
[ -d "$root/heart/heart-sims/build/sims/lib" ] || { echo "build the simulator first: ./gradlew -p heart :heart-sims:installSims"; exit 1; }

context=$(mktemp -d)
trap 'rm -rf "$context"' EXIT
cp -R "$here/Dockerfile" "$here/init.groovy.d" "$context/"
mkdir -p "$context/jobs/app"
hook="http://host.docker.internal:8170/hooks/jenkins?secret=$JENKINS_HOOK_SECRET"
sed "s#@@HOOK_URL@@#$hook#" "$here/jobs/app/config.xml.template" > "$context/jobs/app/config.xml"
docker build -q -t merge-release-jenkins "$context"

docker rm -f merge-release-jenkins >/dev/null 2>&1 || true
docker run -d --name merge-release-jenkins \
  -p 8171:8080 -p 8180:8180 \
  --add-host host.docker.internal:host-gateway \
  -e JENKINS_ADMIN_USER -e JENKINS_ADMIN_PASSWORD \
  -v "$work/library-app:/repos/library-app:ro" \
  -v "$root/heart/heart-sims/build/sims/lib:/sims/lib:ro" \
  merge-release-jenkins >/dev/null
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD
for i in $(seq 1 120); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -u "$JENKINS_ADMIN_USER:$JENKINS_ADMIN_PASSWORD" http://localhost:8171/job/app/api/json || true)
  [ "$code" = 200 ] && { echo "Jenkins up on http://localhost:8171, job app ready"; exit 0; }
  sleep 2
done
echo "Jenkins did not come up"; docker logs --tail 30 merge-release-jenkins; exit 1
