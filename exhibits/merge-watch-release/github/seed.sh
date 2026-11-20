#!/bin/sh
# Seeds the release's real GitHub repository (default JogoShugh/heart-github-test) for one variant: the library app's
# main as library/main and the variant as library/pr-N, both force-pushed, so its own main is never touched; any pull
# request still open from library/pr-N closed; a new one opened, titled as the variant's last commit. Prints its
# number. Uses the token in ~/.artie/credentials, never printed.
#   github/seed.sh N
set -eu
here=$(cd "$(dirname "$0")" && pwd)
n=$1
slug=${RELEASE_GITHUB_REPO:-JogoShugh/heart-github-test}
repo="$here/../work/library-app"
token=$(awk '/\[https:\/\/api.github.com\]/{f=1} f&&/bearer/{print $3; exit}' "$HOME/.artie/credentials")
api() { curl -sf -H "Authorization: Bearer $token" -H "Accept: application/vnd.github+json" "$@"; }
git -C "$repo" -c credential.helper= push -q -f "https://x-access-token:$token@github.com/$slug.git" \
  "main:refs/heads/library/main" "pr-$n:refs/heads/library/pr-$n" 2>&1 | grep -v "^remote:" | sed "s/$token/<token>/g" || true
owner=${slug%%/*}
for open in $(api "https://api.github.com/repos/$slug/pulls?state=open&head=$owner:library/pr-$n" \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).map(p=>p.number).join(" ")))'); do
  api -X PATCH "https://api.github.com/repos/$slug/pulls/$open" -d '{"state":"closed"}' >/dev/null
done
title=$(git -C "$repo" log -1 --format=%s "pr-$n")
api -X POST "https://api.github.com/repos/$slug/pulls" \
  -d "$(node -e 'const [t,n]=process.argv.slice(1);console.log(JSON.stringify({title:t,head:"library/pr-"+n,base:"library/main",body:"Variant "+n+" of the Riverside Public Library release."}))' "$title" "$n")" \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).number))'
