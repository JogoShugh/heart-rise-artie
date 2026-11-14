#!/bin/sh
# For variants.sh's RELEASE_SHOT at real GitHub: the pull request each release left, on github.com and as the gateway
# serves it in text/html (through the explorer proxy on 8152, which presents the gateway's test token).
#   github-shot.sh VARIANT NUMBER
here=$(cd "$(dirname "$0")" && pwd)
sh "$here/shot.sh" "https://github.com/JogoShugh/heart-github-test/pull/$2" "$here/shots/github-$1.png" 1280 1000
sh "$here/shot.sh" "http://localhost:8152/repos/JogoShugh/heart-github-test/pulls/$2" "$here/shots/html-github-$1.png" 1280 1600
