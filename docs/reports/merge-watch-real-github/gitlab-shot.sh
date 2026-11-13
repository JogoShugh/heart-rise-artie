#!/bin/sh
# For variants.sh's RELEASE_SHOT at GitLab: the merge request each release left, on GitLab and as the gateway serves it
# in text/html (through the explorer proxy on 8153), before the next release's seed makes the merge requests afresh.
#   gitlab-shot.sh VARIANT NUMBER
here=$(cd "$(dirname "$0")" && pwd)
sh "$here/shot.sh" "http://localhost:8929/acme/web/-/merge_requests/$2" "$here/shots/gitlab-$1.png"
sh "$here/shot.sh" "http://localhost:8153/projects/acme%252Fweb/merge_requests/$2" "$here/shots/html-gitlab-$1.png" 1280 1600
