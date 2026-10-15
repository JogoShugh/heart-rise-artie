#!/usr/bin/env bash
# Builds the identifier site in docs/ from standards/identifiers.json: one page
# for each HEART identifier under https://w3id.org/heart-rise/, saying what it
# means, plus an index. GitHub Pages serves docs/; w3id.org redirects each
# identifier to its page (standards/w3id/). The garden's identifiers.feature
# checks that every identifier a response names has a page here.
# Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
python3 -I - "$root/standards/identifiers.json" "$root/docs" <<'EOF'
import html, json, os, sys

table, site = sys.argv[1], sys.argv[2]
data = json.load(open(table))
base = data["base"]
os.makedirs(site, exist_ok=True)
open(os.path.join(site, ".nojekyll"), "w").close()

STYLE = """
:root { --bg: #f6f8f7; --card: #fff; --ink: #18221d; --soft: #4d5b54; --line: #dfe6e2; --leaf: #2d7a4c; }
@media (prefers-color-scheme: dark) { :root { --bg: #121816; --card: #19211e; --ink: #e3ebe7; --soft: #9aaba3; --line: #2c3833; --leaf: #6cc290; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink); font: 16px/1.6 system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width: 46rem; margin: 0 auto; padding: 3rem 1rem 4rem; display: grid; gap: 1.25rem; }
.kind { font-size: .75rem; letter-spacing: .08em; text-transform: uppercase; color: var(--soft); }
h1 { margin: 0; font-size: 2rem; line-height: 1.2; }
code { font: .9em ui-monospace, Menlo, monospace; background: var(--card); border: 1px solid var(--line); border-radius: 4px; padding: .1em .35em; overflow-wrap: anywhere; }
a { color: var(--leaf); }
ul { padding-left: 1.2rem; }
table { border-collapse: collapse; width: 100%; background: var(--card); border: 1px solid var(--line); }
td, th { text-align: left; padding: .5rem .75rem; border-bottom: 1px solid var(--line); vertical-align: top; }
footer { color: var(--soft); font-size: .9rem; border-top: 1px solid var(--line); padding-top: 1rem; }
"""

def page(path, title, body):
    depth = path.count("/") + (1 if path else 0)
    css = "../" * depth + "style.css"
    folder = os.path.join(site, path)
    os.makedirs(folder, exist_ok=True)
    open(os.path.join(folder, "index.html"), "w").write(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} · HEART identifiers</title><link rel="stylesheet" href="{css}"></head>
<body><main>{body}
<footer>HEART identifiers, built from <code>standards/identifiers.json</code>. Index: <a href="{'../' * depth or './'}">{html.escape(base)}</a></footer>
</main></body></html>
""")

open(os.path.join(site, "style.css"), "w").write(STYLE)
rows = []
for entry in data["identifiers"]:
    uri = base + ("/" + entry["path"] if entry["path"] else "")
    see = "".join(f'<li><a href="{html.escape(s)}">{html.escape(s)}</a></li>' for s in entry["see"])
    body = f"""<span class="kind">{html.escape(entry["kind"])}</span>
<h1>{html.escape(entry["title"])}</h1>
<p><code>{html.escape(uri)}</code></p>
<p>{html.escape(entry["definition"])}</p>
<h2>Defined in</h2><ul>{see}</ul>"""
    if entry["path"]:
        page(entry["path"], entry["title"], body)
        rows.append(f'<tr><td>{html.escape(entry["kind"])}</td><td><a href="{entry["path"]}/"><code>{html.escape(uri)}</code></a></td><td>{html.escape(entry["title"])}</td></tr>')
    else:
        index_body = body
index_body += "<h2>Every HEART identifier</h2><table><tr><th>Kind</th><th>Identifier</th><th>Meaning</th></tr>" + "".join(rows) + "</table>"
page("", "HEART", index_body)
print(f"built {len(data['identifiers'])} identifier pages in {site}")
EOF
