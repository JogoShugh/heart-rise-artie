#!/bin/sh
# Seeds the release's GitLab (gitlab/run.sh) with the library app: project acme/web, main and the variant branches
# from work/library-app, and a merge request for each variant numbered as its pull request (501 to 505). Makes an
# API token for root once, kept in gitlab/gitlab.env beside the password; neither is ever printed.
#   gitlab/seed.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
host=${RELEASE_HOST:-192.168.1.18}
G="http://$host:8929"
repo="$here/../work/library-app"
env_file="$here/gitlab.env"
[ -d "$repo/.git" ] || { echo "no $repo: run ../make-app-repo.sh first"; exit 1; }
# shellcheck disable=SC1090
. "$env_file"

rails() { docker exec -i merge-release-gitlab gitlab-rails runner - ; }

if [ -z "${GITLAB_TOKEN:-}" ]; then
  GITLAB_TOKEN="glpat-$(openssl rand -hex 16)"
  printf "u = User.find_by_username('root')
t = u.personal_access_tokens.new(name: 'release-seed', scopes: %%w[api read_repository write_repository], expires_at: 300.days.from_now)
t.set_token('%s')
t.save!
" "$GITLAB_TOKEN" | rails >/dev/null
  umask 077
  printf 'GITLAB_TOKEN=%s\n' "$GITLAB_TOKEN" >> "$env_file"
fi
api() { curl -sf -H "PRIVATE-TOKEN: $GITLAB_TOKEN" "$@"; }

# The group and project, once.
api "$G/api/v4/groups/acme" >/dev/null 2>&1 \
  || api -X POST "$G/api/v4/groups" -d name=acme -d path=acme -d visibility=public >/dev/null
group=$(api "$G/api/v4/groups/acme" | sed -E 's/^\{"id":([0-9]+).*/\1/')
if ! api "$G/api/v4/projects/acme%2Fweb" >/dev/null 2>&1; then
  api -X POST "$G/api/v4/projects" -d name=web -d path=web -d "namespace_id=$group" -d default_branch=main \
    -d visibility=public >/dev/null
  # Merge requests are numbered as the pull requests they stand for: the next is 501.
  printf "p = Project.find_by_full_path('acme/web')
InternalId.create!(project: p, usage: :merge_requests, last_value: 500) unless InternalId.exists?(project: p, usage: :merge_requests)
" | rails >/dev/null
fi

# The app's branches, as made: every seed starts the app over, so main takes a force push (it is a demo's).
api -X DELETE "$G/api/v4/projects/acme%2Fweb/protected_branches/main" >/dev/null 2>&1 || true
auth=$(printf 'oauth2:%s' "$GITLAB_TOKEN" | base64)
git -C "$repo" -c "http.extraHeader=Authorization: Basic $auth" push -q -f "$G/acme/web.git" \
  main pr-501 pr-502 pr-503 pr-504 pr-505

# A merge request for each variant, titled as its last commit, made afresh: a release merges one, and a merged merge
# request can't be opened again. The ones before are deleted, and the numbering starts again at 501.
printf "p = Project.find_by_full_path('acme/web')
MergeRequest.where(target_project: p).find_each { |mr| mr.destroy! }
InternalId.where(project: p, usage: :merge_requests).delete_all
InternalId.create!(project: p, usage: :merge_requests, last_value: 500)
" | rails >/dev/null
for n in 501 502 503 504 505; do
  title=$(git -C "$repo" log -1 --format=%s "pr-$n")
  made=$(api -X POST "$G/api/v4/projects/acme%2Fweb/merge_requests" --data-urlencode "title=$title" \
    -d "source_branch=pr-$n" -d target_branch=main | sed -E 's/^\{"id":[0-9]+,"iid":([0-9]+).*/\1/')
  [ "$made" = "$n" ] || { echo "merge request for pr-$n is !$made, not !$n"; exit 1; }
  echo "!$n $title"
done
