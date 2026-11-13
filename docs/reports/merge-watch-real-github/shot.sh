#!/bin/sh
# A screenshot of URL into OUT.png, by headless Chrome, as a reader not signed in sees it.
#   shot.sh URL OUT.png [WIDTH] [HEIGHT]
set -eu
profile=$(mktemp -d)
trap 'rm -rf "$profile"' EXIT
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --use-mock-keychain --hide-scrollbars \
  --user-data-dir="$profile" --window-size="${3:-1280},${4:-1600}" --virtual-time-budget=8000 \
  --screenshot="$2" "$1" 2>/dev/null
