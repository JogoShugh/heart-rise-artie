#!/usr/bin/env bash
# Setup for the GitHub task scripts (github-task-*.kts): one ready pull request in the scratch repository,
# made with GitHub's own API, because the gateway offers no act that makes a branch or a pull request.
# Prints the pull request's number, once GitHub has worked out whether it can be merged.
#   github-task-setup.sh JogoShugh heart-github-test scripted
# Reads the token from ~/.artie/credentials ([https://api.github.com], bearer) and never prints it.
set -euo pipefail
owner=$1 repo=$2 label=$3
token=$(awk -F' *= *' '/^\[https:\/\/api.github.com\]/{f=1;next} /^\[/{f=0} f && $1=="bearer"{print $2}' ~/.artie/credentials)
[ -n "$token" ] || { echo "no GitHub token in ~/.artie/credentials" >&2; exit 1; }
api() { curl -sS -f -H "Authorization: Bearer $token" -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" "$@"; }
base=https://api.github.com/repos/$owner/$repo
stamp=$(date -u +%Y%m%dT%H%M%S)
branch=heart-task-$label-$stamp

main=$(api "$base" | jq -r .default_branch)
sha=$(api "$base/git/ref/heads/$main" | jq -r .object.sha)
api -X POST "$base/git/refs" -d "$(jq -n --arg r "refs/heads/$branch" --arg s "$sha" '{ref:$r,sha:$s}')" >/dev/null
content=$(printf 'Bed names may not be blank (%s, %s).\n' "$label" "$stamp" | base64)
api -X PUT "$base/contents/fixes/$branch.md" -d "$(jq -n --arg m "Refuse a bed with no name ($label)" \
  --arg c "$content" --arg b "$branch" '{message:$m,content:$c,branch:$b}')" >/dev/null
number=$(api -X POST "$base/pulls" -d "$(jq -n --arg t "Refuse a bed with no name ($label)" --arg h "$branch" \
  --arg b "$main" '{title:$t,head:$h,base:$b,body:"Made by the GitHub task setup."}')" | jq -r .number)

for _ in $(seq 1 30); do
  m=$(api "$base/pulls/$number" | jq -r .mergeable)
  [ "$m" = true ] && { echo "$number"; exit 0; }
  sleep 2
done
echo "pull request #$number never became mergeable" >&2
exit 1
