#!/bin/sh
# Builds RISE dynamic webhook, envisioned (docs/reports/rise-webhook-envisioned): the shared report shell, filled with the shared
# look and JSON viewer, this report's style, the data (webhook.js) and its renderer (page.js). One self-contained page, so
# it works on GitHub Pages and as an artifact alike.
#
#   build.sh [ARTIFACT.html]    writes index.html beside this script, and the artifact copy when given
set -eu
here=$(cd "$(dirname "$0")" && pwd)
assets="$here/../assets"
out="$here/index.html"
artifact=${1:-}
page=$(mktemp)
trap 'rm -f "$page"' EXIT

TITLE="RISE Dynamic Webhook, Envisioned" \
HEADING="The dynamic webhook, every message in order" \
LEDE="Envisioned, not built. Slice 1 of RISE, RESTful: one loan, one reply, one watch, one answer, then the refusals. Each numbered step is one request and its response, headers and body. Defaults: act bodies are application/hal+json under the profile; the answer is held and linked. Names and digests are made up." \
awk -v base="$assets/report-base.css" -v jvcss="$assets/json-view.css" -v style="$here/page.css" \
    -v theme="$assets/theme-switch.js" -v jvjs="$assets/json-view.js" -v data="$here/webhook.js" -v pagejs="$here/page.js" '
  function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
  /^@@BASE@@$/   { emit(base); next }
  /^@@STYLE@@$/  { emit(jvcss); emit(style); next }
  /^@@BODY@@$/   { print "<div id=\"webhook\"></div>"; next }
  /^@@THEME@@$/  { emit(theme); next }
  /^@@SCRIPT@@$/ { emit(jvjs); emit(data); emit(pagejs); next }
  { gsub(/@@TITLE@@/, ENVIRON["TITLE"]); gsub(/@@HEADING@@/, ENVIRON["HEADING"]); gsub(/@@LEDE@@/, ENVIRON["LEDE"]); print }
' "$assets/report-shell.html" > "$page"

{
  printf '%s\n' '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body style="margin:0">'
  cat "$page"
  printf '%s\n' '</body></html>'
} > "$out"
[ -z "$artifact" ] || cp "$page" "$artifact"
echo "wrote $out${artifact:+ and $artifact}"
