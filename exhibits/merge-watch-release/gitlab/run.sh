#!/bin/sh
# Starts the release's GitLab on this Mac (gitlab/gitlab-ce, trimmed to run beside Jenkins), at
# http://RELEASE_HOST:8929. Its root password is made once into gitlab/gitlab.env, which git ignores, and is never
# printed. GitLab takes a few minutes to start: this script waits until it answers.
#   gitlab/run.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
host=${RELEASE_HOST:-192.168.1.18}
env_file="$here/gitlab.env"
if [ ! -f "$env_file" ]; then
  umask 077
  printf 'GITLAB_ROOT_PASSWORD=%s\n' "$(openssl rand -base64 24 | tr -d '/+=')" > "$env_file"
fi
# shellcheck disable=SC1090
. "$env_file"
export GITLAB_ROOT_PASSWORD

docker rm -f merge-release-gitlab >/dev/null 2>&1 || true
docker volume create merge-release-gitlab-data >/dev/null
docker run -d --name merge-release-gitlab --shm-size 256m \
  -p 8929:8929 \
  -e GITLAB_ROOT_PASSWORD \
  -e GITLAB_OMNIBUS_CONFIG="external_url 'http://$host:8929'; \
puma['worker_processes'] = 0; sidekiq['concurrency'] = 5; \
prometheus_monitoring['enable'] = false; gitlab_rails['usage_ping_enabled'] = false; \
gitlab_rails['initial_root_password'] = ENV['GITLAB_ROOT_PASSWORD']" \
  -v merge-release-gitlab-data:/var/opt/gitlab \
  gitlab/gitlab-ce:17.11.1-ce.0 >/dev/null

printf 'waiting for GitLab'
until curl -sf -o /dev/null "http://$host:8929/users/sign_in"; do printf '.'; sleep 10; done
echo " up at http://$host:8929"
