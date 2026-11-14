#!/bin/sh
# Builds RISE, end to end (docs/reports/rise-flows): the shared report shell, filled with the shared look and
# JSON viewer, this page's style, the flows (flows.js) and their renderer (page.js). One self-contained page,
# so it works on GitHub Pages and as an artifact alike.
#
#   build.sh [ARTIFACT.html]    writes index.html beside this script, and the artifact copy when given
set -eu
here=$(cd "$(dirname "$0")" && pwd)
assets="$here/../assets"
out="$here/index.html"
artifact=${1:-}
page=$(mktemp)
trap 'rm -f "$page"' EXIT

TITLE="RISE, End to End" \
HEADING="RISE, end to end: every variation, every message" \
LEDE="A mockup, not built. Ana Torres's assistant asks Riverside Public Library to say when The Hobbit can be renewed, and is told back over a webhook, a poll, a slow act and AMQP; then one title is followed by many subscribers, five ways, and through the W3C's LDN, WebSub and ActivityPub. Digests, tags, tokens and times are made up; the shapes follow HEART as built." \
awk -v base="$assets/report-base.css" -v jvcss="$assets/json-view.css" -v style="$here/page.css" \
    -v theme="$assets/theme-switch.js" -v jvjs="$assets/json-view.js" -v flows="$here/flows.js" -v pagejs="$here/page.js" '
  function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
  /^@@BASE@@$/   { emit(base); next }
  /^@@STYLE@@$/  { emit(jvcss); emit(style); next }
  /^@@BODY@@$/   { print "<div id=\"rise\"></div>"; next }
  /^@@THEME@@$/  { emit(theme); next }
  /^@@SCRIPT@@$/ { emit(jvjs); emit(flows); emit(pagejs); next }
  { gsub(/@@TITLE@@/, ENVIRON["TITLE"]); gsub(/@@HEADING@@/, ENVIRON["HEADING"]); gsub(/@@LEDE@@/, ENVIRON["LEDE"]); print }
' "$assets/report-shell.html" > "$page"

{
  printf '%s\n' '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body style="margin:0">'
  cat "$page"
  printf '%s\n' '</body></html>'
} > "$out"
[ -z "$artifact" ] || cp "$page" "$artifact"
echo "wrote $out${artifact:+ and $artifact}"
