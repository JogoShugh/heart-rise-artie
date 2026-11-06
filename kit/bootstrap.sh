#!/bin/sh
# Makes your fork of this repository the kit's target. It builds the kit, then seeds the fork with six issues about
# its own code, by replaying a kept script with no model: three waiting for triage, and three ready to close, one of
# each kind. Then the kit's goals work on them (kit/README.md).
#   kit/bootstrap.sh          the fork is read from git's origin; OWNER and REPO, if set, name another
# Needs GITHUB_TOKEN: a fine-grained token for the fork alone, with Contents, Issues and Pull requests read and write.
set -eu
here=$(cd "$(dirname "$0")" && pwd)

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

Next, in this folder:
  export OWNER=$OWNER REPO=$REPO ANTHROPIC_API_KEY=…
  kit/try.sh walk triage            Claude labels the three issues waiting for triage; close "Triage watch" to stop
  kit/try.sh walk closeout version=1.0
                                    closes the three ready to close, each its own way; close "Close-out watch" to stop
  kit/try.sh keep triage            then replay it, with no model but its one reasoned step: kit/try.sh replay triage
EOF
