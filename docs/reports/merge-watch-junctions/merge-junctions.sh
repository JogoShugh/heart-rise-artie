#!/bin/sh
# Walks the merge watch's junctions on the GitHub gateway (heart-sims github.test on 8098, behind the explorer
# proxy on 8152) and screenshots each: HAL Explorer reading it as HAL-FORMS, and the same address as text/html.
set -eu
OUT=$1
G=http://localhost:8098
P=http://localhost:8152
AUTH='Authorization: Bearer github-token'
HF='application/prs.hal-forms+json'
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
S=$(dirname "$OUT")/chrome-junctions
mkdir -p "$OUT"

shot() { # name path
  rm -rf "$S"
  "$CH" --headless=new --use-mock-keychain --user-data-dir="$S" --window-size=1400,3600 --virtual-time-budget=8000 \
    --screenshot="$OUT/$1-hal-forms.png" "$P/explorer/#hkey0=Accept&hval0=application/prs.hal-forms%2Bjson&uri=$P$2" >/dev/null 2>&1
  rm -rf "$S"
  "$CH" --headless=new --use-mock-keychain --user-data-dir="$S" --window-size=1400,1500 --virtual-time-budget=4000 \
    --screenshot="$OUT/$1-html.png" "$P$2" >/dev/null 2>&1
  curl -s -H "$AUTH" -H "Accept: $HF" "$G$2" > "$OUT/$1.hal-forms.json"
  echo "shot $1 $2"
}

etag() { curl -s -D - -o /dev/null -H "$AUTH" -H "Accept: $HF" "$G$1" | tr -d '\r' | sed -n 's/^[Ee][Tt]ag: //p'; }

act() { # method resource action body
  tag=$(etag "$2")
  code=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" -H "$AUTH" -H "Accept: $HF" -H "Content-Type: application/json" \
    -H "If-Match: $tag" -d "$4" "$G$2/actions/$3")
  echo "act $3 on $2: $code"
}

curl -s -X POST "$G/_sim/reset" -o /dev/null || true
shot 1-bookmark /
shot 2-repository /repos/acme/web
tag=$(etag /repos/acme/web)
watch=$(curl -s -D - -o /dev/null -X POST -H "$AUTH" -H "Accept: $HF" -H "Content-Type: application/json" -H "If-Match: $tag" -d '{"title":"Merge watch","body":"Close this issue to stop the watch."}' "$G/repos/acme/web/actions/open-issue" | tr -d "\r" | sed -n "s/^[Ll]ocation: //p")
echo "watch: $watch"
shot 3-watch-opened "$watch"
act POST /repos/acme/web/pulls/482 label '{"labels":["dependencies"]}'
shot 4-pulls-labelled '/repos/acme/web/pulls?labels=dependencies'
shot 5-pull-mergeable /repos/acme/web/pulls/482
act PUT /repos/acme/web/pulls/482 merge '{"merge_method":"squash"}'
shot 6-pull-merged /repos/acme/web/pulls/482
act PATCH "$watch" close '{}'
shot 7-watch-closed "$watch"
