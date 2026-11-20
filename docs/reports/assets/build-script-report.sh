#!/bin/sh
# Builds the line-by-line report of a script's run (spec/PLAN-LINE-BY-LINE-REPORT.md): one self-contained HTML
# page, the script first, then what each line did, from the run's capture and, when given, the gateway's record
# of its upstream calls. Every asset is inlined from this folder, so the page works on GitHub Pages and as an
# artifact alike.
#
#   build-script-report.sh TRACE.yaml OUT.html "Title" "One sentence saying what was run." SCRIPT.kts [UPSTREAM.yaml]
set -eu
trace=$1 out=$2 title=$3 lede=$4 script=$5 upstream=${6:-}
here=$(cd "$(dirname "$0")" && pwd)

html_escape() { printf '%s' "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
safe_title=$(html_escape "$title")
safe_lede=$(html_escape "$lede")
empty=$(mktemp)
trap 'rm -f "$empty"' EXIT
[ -n "$upstream" ] || upstream=$empty

TITLE=$safe_title LEDE=$safe_lede awk \
    -v srcss="$here/script-report.css" -v jvcss="$here/json-view.css" -v jsyaml="$here/js-yaml.min.js" \
    -v jvjs="$here/json-view.js" -v srjs="$here/script-report.js" -v ktjs="$here/kotlin-view.js" \
    -v trace="$trace" -v script="$script" -v upstream="$upstream" '
  BEGIN { title = ENVIRON["TITLE"]; lede = ENVIRON["LEDE"] }
  function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
  # Text in the page, read back by its textContent: & < > escaped.
  function emit_text(file,   line) {
    while ((getline line < file) > 0) { gsub(/&/, "\\&amp;", line); gsub(/</, "\\&lt;", line); gsub(/>/, "\\&gt;", line); print line }
    close(file)
  }
  function inline(marker, file,   parts) { split($0, parts, marker); printf "%s", parts[1]; emit_text(file); print parts[2] }
  /\/\*@SCRIPT_REPORT_CSS@\*\// { emit(srcss); next }
  /\/\*@JSON_VIEW_CSS@\*\//     { emit(jvcss); next }
  /\/\*@JS_YAML@\*\//           { emit(jsyaml); next }
  /\/\*@JSON_VIEW_JS@\*\//      { emit(jvjs); next }
  /\/\*@SCRIPT_REPORT_JS@\*\//  { emit(srjs); next }
  /\/\*@KOTLIN_VIEW_JS@\*\//    { emit(ktjs); next }
  /@SCRIPT@/   { inline("@SCRIPT@", script); next }
  /@TRACE@/    { inline("@TRACE@", trace); next }
  /@UPSTREAM@/ { inline("@UPSTREAM@", upstream); next }
  function swap(s, from, to,   i, out) {
    out = ""
    while ((i = index(s, from)) > 0) { out = out substr(s, 1, i - 1) to; s = substr(s, i + length(from)) }
    return out s
  }
  { print swap(swap($0, "@TITLE@", title), "@LEDE@", lede) }
' "$here/script-report.html" > "$out"
echo "wrote $out"
