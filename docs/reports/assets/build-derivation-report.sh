#!/bin/sh
# Builds the derivation report of a generated script (spec/PLAN-SCRIPT-GENERATOR.md): one self-contained HTML page,
# the run's script beside the one artie generate wrote from its capture, each derivation of NAME.derivation.yaml
# numbered on both sides, its note one click away. Every asset is inlined from this folder, so the page works on
# GitHub Pages and as an artifact alike.
#
#   build-derivation-report.sh SOURCE.kts GENERATED.kts NAME.derivation.yaml OUT.html "Title" "One sentence."
set -eu
source=$1 generated=$2 derivation=$3 out=$4 title=$5 lede=$6
here=$(cd "$(dirname "$0")" && pwd)

html_escape() { printf '%s' "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g'; }
safe_title=$(html_escape "$title")
safe_lede=$(html_escape "$lede")

TITLE=$safe_title LEDE=$safe_lede awk \
    -v drcss="$here/derivation-report.css" -v jsyaml="$here/js-yaml.min.js" -v ktjs="$here/kotlin-view.js" \
    -v drjs="$here/derivation-report.js" -v source="$source" -v generated="$generated" -v derivation="$derivation" '
  BEGIN { title = ENVIRON["TITLE"]; lede = ENVIRON["LEDE"] }
  function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
  # Text in the page, read back by its textContent: & < > escaped.
  function emit_text(file,   line) {
    while ((getline line < file) > 0) { gsub(/&/, "\\&amp;", line); gsub(/</, "\\&lt;", line); gsub(/>/, "\\&gt;", line); print line }
    close(file)
  }
  function inline(marker, file,   parts) { split($0, parts, marker); printf "%s", parts[1]; emit_text(file); print parts[2] }
  /\/\*@DERIVATION_REPORT_CSS@\*\// { emit(drcss); next }
  /\/\*@JS_YAML@\*\//               { emit(jsyaml); next }
  /\/\*@KOTLIN_VIEW_JS@\*\//        { emit(ktjs); next }
  /\/\*@DERIVATION_REPORT_JS@\*\//  { emit(drjs); next }
  /@SOURCE@/     { inline("@SOURCE@", source); next }
  /@GENERATED@/  { inline("@GENERATED@", generated); next }
  /@DERIVATION@/ { inline("@DERIVATION@", derivation); next }
  function swap(s, from, to,   i, out) {
    out = ""
    while ((i = index(s, from)) > 0) { out = out substr(s, 1, i - 1) to; s = substr(s, i + length(from)) }
    return out s
  }
  { print swap(swap($0, "@TITLE@", title), "@LEDE@", lede) }
' "$here/derivation-report.html" > "$out"
