#!/bin/sh
# Builds a run report: one self-contained HTML page from a trace (heart-run-report skill), with the viewer
# inlined from this folder, so the page works on GitHub Pages and as an artifact alike.
#
#   build-run-report.sh TRACE.yaml OUT.html "Title" "One sentence saying what was run, against what." [SCRIPT]
#
# SCRIPT, when given, is the script that made the trace (a heart-script .kts): shown on the page, as written.
set -eu
trace=$1 out=$2 title=$3 lede=$4 script=${5:-}
here=$(cd "$(dirname "$0")" && pwd)

# Text for HTML: & < > escaped; the trace too, as YAML text in the page, read back by its textContent.
html_escape() { printf '%s' "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
safe_title=$(html_escape "$title")
safe_lede=$(html_escape "$lede")

# The script, escaped, in a block of its own; nothing at all when there is none.
script_block=$(mktemp)
trap 'rm -f "$script_block"' EXIT
if [ -n "$script" ]; then
  {
    printf '    <details class="rr-script" open>\n      <summary>The script: <code>%s</code></summary>\n' "$(html_escape "$(basename "$script")")"
    printf '      <pre><code class="kotlin">'
    sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$script"
    printf '</code></pre>\n    </details>\n'
  } > "$script_block"
fi

TITLE=$safe_title LEDE=$safe_lede awk \
    -v rrcss="$here/run-report.css" -v jvcss="$here/json-view.css" \
    -v jvjs="$here/json-view.js" -v jsyaml="$here/js-yaml.min.js" -v rrjs="$here/run-report.js" -v ktjs="$here/kotlin-view.js" -v trace="$trace" -v scriptblock="$script_block" '
  BEGIN { title = ENVIRON["TITLE"]; lede = ENVIRON["LEDE"] }
  function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
  function emit_trace(file,   line) {
    while ((getline line < file) > 0) { gsub(/&/, "\\&amp;", line); gsub(/</, "\\&lt;", line); gsub(/>/, "\\&gt;", line); print line }
    close(file)
  }
  /\/\*@RUN_REPORT_CSS@\*\// { emit(rrcss); next }
  /\/\*@JSON_VIEW_CSS@\*\//  { emit(jvcss); next }
  /\/\*@JSON_VIEW_JS@\*\//   { emit(jvjs); next }
  /\/\*@JS_YAML@\*\//       { emit(jsyaml); next }
  /\/\*@RUN_REPORT_JS@\*\//  { emit(rrjs); next }
  /^@SCRIPT@$/ { emit(scriptblock); next }
  /\/\*@KOTLIN_VIEW_JS@\*\//  { emit(ktjs); next }
  /@TRACE@/ {
    split($0, parts, "@TRACE@"); printf "%s", parts[1]; emit_trace(trace); print parts[2]; next
  }
  # Literal replacement: gsub would read "&" in the text as the match.
  function swap(s, from, to,   i, out) {
    out = ""
    while ((i = index(s, from)) > 0) { out = out substr(s, 1, i - 1) to; s = substr(s, i + length(from)) }
    return out s
  }
  { print swap(swap($0, "@TITLE@", title), "@LEDE@", lede) }
' "$here/run-report.html" > "$out"
echo "wrote $out"
