#!/bin/sh
# Serves the release's GitLab (gitlab/run.sh, seeded by gitlab/seed.sh) through the HEART gateway with the gitlab
# connector: merge requests as scm: pull requests, at http://RELEASE_HOST:8094. The gateway calls GitLab with the
# seed's token (gitlab/gitlab.env, never printed); clients present gitlab-token.
#   gitlab/gateway.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
host=${RELEASE_HOST:-localhost}
# shellcheck disable=SC1090
. "$here/gitlab.env"
connector="$root/heart/heart-gateway/connectors/gitlab"
export GATEWAY_CONNECTOR="$connector" UPSTREAM_SPEC="$connector/upstream.openapi.yaml"
export UPSTREAM_URL="http://$host:8929/api/v4" UPSTREAM_AUTH=bearer UPSTREAM_TOKEN="$GITLAB_TOKEN"
# The gateway keeps only each client token's SHA-256.
HEART_AUTH_BEARER_TOKENS=$(printf '%s' gitlab-token | shasum -a 256 | cut -d' ' -f1)
export HEART_AUTH_BEARER_TOKENS PORT=8094
# The JDK this Mac runs with, when it is there; elsewhere, JAVA_HOME as set, or the java on the PATH (22 or newer).
mac_java=$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal
[ -n "${JAVA_HOME:-}" ] || [ ! -d "$mac_java" ] || export JAVA_HOME="$mac_java"
exec "$root/heart/heart-gateway/build/install/heart-gateway/bin/heart-gateway"
