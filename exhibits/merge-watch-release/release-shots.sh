#!/bin/sh
# Screenshots the release's junctions, each in HAL Explorer (as HAL-FORMS) and as the HTML page, through the
# explorer proxies (8251 watcher, 8252 ARTIE's replies, 8253 CI, 8254 preview, 8255 Dev).
set -eu
OUT=$1
REPLY=$2
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
S=$(dirname "$OUT")/chrome-release
mkdir -p "$OUT"
shot() { # name port path
  for kind in hal-forms html; do
    rm -rf "$S"
    if [ "$kind" = hal-forms ]; then
      url="http://localhost:$2/explorer/#hkey0=Accept&hval0=application/prs.hal-forms%2Bjson&uri=http://localhost:$2$3"
      size=1400,2600
    else
      url="http://localhost:$2$3"
      size=1400,1300
    fi
    "$CH" --headless=new --use-mock-keychain --user-data-dir="$S" --window-size=$size --virtual-time-budget=8000 \
      --screenshot="$OUT/$1-$kind.png" "$url" >/dev/null 2>&1
  done
  echo "shot $1"
}
shot 1-watch 8251 /watches/1
shot 2-reply-completed 8252 "/replies/$REPLY"
shot 3-build 8253 /builds/1
shot 4-preview-loan 8254 /loans/3311
shot 5-dev 8255 /environments/1
