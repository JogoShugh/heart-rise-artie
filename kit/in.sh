#!/bin/sh
# Runs a command in the kit as the entrypoint does, in /work, with each system's credential named by its origin:
# GitHub's is GITHUB_TOKEN, the caller's own; the build and deploy services' are their test tokens. Used by the
# entrypoint, and by kit/try.sh for each command while the kit is up. No token is written.
set -eu
export ARTIE_TOKEN_LOCALHOST_8392="$GITHUB_TOKEN" ARTIE_TOKEN_LOCALHOST_8401=build-token ARTIE_TOKEN_LOCALHOST_8402=deploy-token
cd /work
exec "$@"
