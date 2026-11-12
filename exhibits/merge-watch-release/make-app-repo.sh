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
cp "$here/smoke/smoke.goal.md" "$here/smoke/smoke.heart.yaml" smoke/
printf '# Riverside Public Library\n\nThe library app, as the release builds and deploys it.\n' > README.md
git add -A && git commit -q -m "Riverside Public Library: policy file and smoke goal"

git checkout -q -b pr-482
printf '\nA loan renews three weeks at a time, up to three times, unless a hold is waiting.\n' >> README.md
git commit -q -am "Say the renewal rule in the README"

git checkout -q main
git checkout -q -b pr-483
printf 'renewal.weeks=0\n' > library.properties
git commit -q -am "Renewals: hand-out week"

# Variants: each its own number, title and change, released by the same kept scripts with their own inputs.
variant() { # number title weeks note
  git checkout -q main
  git checkout -q -b "pr-$1"
  printf 'renewal.weeks=%s\n' "$3" > library.properties
  printf '\n%s\n' "$4" >> README.md
  git commit -q -am "$2"
}
variant 501 "Renewals: two weeks for short loans" 2 "Short loans renew two weeks at a time."
variant 502 "Renewals: four weeks over the summer" 4 "Over the summer a loan renews four weeks at a time."
variant 503 "Renewals: one week while stock is short" 1 "While stock is short a loan renews one week at a time."
variant 504 "Renewals: none during the audit" 0 "During the stock audit, renewing is paused."
variant 505 "Say who to ask about renewals" 3 "Ask at the desk about renewals."

git checkout -q main
git log --all --oneline --graph
