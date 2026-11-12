#!/bin/sh
# Makes work/library-app, the local git repository the release builds from: Riverside Public Library's policy file
# and its smoke goal on main, and two pull request branches, as the small GitHub's pull requests name them:
#   pr-482  "Say the renewal rule in the README"   (keeps renewals at three weeks: smoke passes)
#   pr-483  "Renewals: hand-out week"               (renewal.weeks=0: smoke scenario S3 fails)
# Started fresh each time: the release run merges into it.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
repo="$here/work/library-app"
rm -rf "$repo"
mkdir -p "$repo/smoke"
cd "$repo"
git init -q -b main
git config user.name "Ada Lovelace"
git config user.email "ada@example.invalid"

printf 'renewal.weeks=3\n' > library.properties
cp "$here/smoke/smoke.goal.md" smoke/
printf '# Riverside Public Library\n\nThe library app, as the release builds and deploys it.\n' > README.md
git add -A && git commit -q -m "Riverside Public Library: policy file and smoke goal"

git checkout -q -b pr-482
printf '\nA loan renews three weeks at a time, up to three times, unless a hold is waiting.\n' >> README.md
git commit -q -am "Say the renewal rule in the README"

git checkout -q main
git checkout -q -b pr-483
printf 'renewal.weeks=0\n' > library.properties
git commit -q -am "Renewals: hand-out week"

git checkout -q main
git log --all --oneline --graph
