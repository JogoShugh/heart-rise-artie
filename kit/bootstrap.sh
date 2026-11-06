#!/bin/sh
# Makes a repository of yours the kit's target. It builds the kit, then seeds the repository with six issues about
# this project's code, by replaying a kept script with no model: three waiting for triage, and three ready to close,
# one of each kind. Then the kit's goals work on them (kit/README.md).
#   kit/bootstrap.sh          OWNER and REPO name the repository; unset, it is your fork, read from git's origin
# Needs GITHUB_TOKEN: a fine-grained token for that repository alone, with Contents, Issues and Pull requests read and
# write. OWNER, REPO and the token can be in kit/.env (kit/env.example).
set -eu
here=$(cd "$(dirname "$0")" && pwd)
# Your keys and repository, from kit/.env when it is there.
. "$here/env.sh"

slug=$(git -C "$here/.." remote get-url origin | sed -E 's#\.git$##; s#^.*github\.com[:/]##')
OWNER=${OWNER:-${slug%%/*}}
REPO=${REPO:-${slug#*/}}
if [ "$OWNER/$REPO" = "JogoShugh/heart-rise-artie" ]; then
  echo "origin is the project itself. Fork it, so the kit works on your own copy: gh repo fork --remote" >&2
  exit 2
fi
if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "Set GITHUB_TOKEN: a fine-grained token for $OWNER/$REPO alone, with Contents, Issues and Pull requests" >&2
  echo "read and write." >&2
  exit 2
fi

# A new fork has its issues turned off.
issues=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO" | sed -n 's/.*"has_issues": *\([a-z]*\).*/\1/p' | head -1)
case $issues in
  true) ;;
  false)
    echo "Issues are turned off in $OWNER/$REPO, as in every new fork. Turn them on in its Settings, under" >&2
    echo "Features, or: gh repo edit $OWNER/$REPO --enable-issues" >&2
    exit 2 ;;
  *)
    echo "GITHUB_TOKEN can't read $OWNER/$REPO: check the repository's name and the token's access." >&2
    exit 2 ;;
esac

echo "Seeding $OWNER/$REPO with six issues, by a kept script, with no model…"
"$here/try.sh" artie run seed.heart.yaml owner="$OWNER" repo="$REPO" --entry http://localhost:8392/ \
  --trace runs/seed.trace.yaml

cat <<EOF

Next, in this folder (kit/README.md, under Run):
  kit/try.sh walk hello                  opens an issue, comments on it, closes it
  kit/try.sh walk triage                 labels the three needs-triage issues; close "Triage watch" on GitHub to end
  kit/try.sh walk closeout version=1.0   closes the three ready-to-close issues; close "Close-out watch" to end
  kit/try.sh walk release version=1.1    queues a build, deploys it, records the release on GitHub
EOF
