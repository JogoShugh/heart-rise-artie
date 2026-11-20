// "The Other R's" as one page: essay.html with each <!--diagram:name--> replaced by diagrams/name.svg,
// inlined in a figure captioned by the diagram's own title. Run diagrams.kts first.
//   artie run page.kts out=index.html artifact=/tmp/other-rs.html
// out is a whole document; artifact, if given, is the same page without the document skeleton.
import java.io.File

val essay by input(default = "essay.html", question = "Where is the essay?")
val diagrams by input(default = "diagrams", question = "Which folder holds the SVG files?")
val out by input(default = "index.html", question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")

val TITLE = "The Other R’s"

val STYLE = """
:root {
  --paper: #F7F3EA; --ink: #1D2128; --muted: #5B6370; --rule: #DDD5C6; --panel: #FFFDF8;
  --accent: #A45F12; --accent-soft: #F6E3C2; --code: #EFE9DC;
  --d-ink: var(--ink); --d-muted: var(--muted); --d-line: #C9C2B4; --d-panel: var(--panel);
  --d-accent: #B86E12; --d-accent-soft: var(--accent-soft);
  --d-font: 'IBM Plex Sans', system-ui, sans-serif; --d-mono: 'JetBrains Mono', ui-monospace, monospace;
  --c-key: #8A3B12; --c-str: #2F6B3A; --c-num: #1F5A99; --c-lit: #7A3E9D; --c-tag: #1F5A99; --c-attr: #8A3B12;
  --c-add-bg: #DFF0DC; --c-del-bg: #F6DEDA; --c-add: #2F6B3A; --c-del: #A3352B; --c-mark: #7A3E9D;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --paper: #14181E; --ink: #ECE6DA; --muted: #A3ABB6; --rule: #2C333D; --panel: #1C222A;
  --accent: #E7A64A; --accent-soft: #3A2C17; --code: #1C222A;
  --c-key: #F0A875; --c-str: #9FD49A; --c-num: #8DBCF0; --c-lit: #D3A6F0; --c-tag: #8DBCF0; --c-attr: #F0A875;
  --c-add-bg: #1E3322; --c-del-bg: #3A2020; --c-add: #9FD49A; --c-del: #F09A90; --c-mark: #D3A6F0;
  --d-line: #3A434F; --d-accent: #E7A64A; color-scheme: dark } }
:root[data-theme="dark"] {
  --paper: #14181E; --ink: #ECE6DA; --muted: #A3ABB6; --rule: #2C333D; --panel: #1C222A;
  --accent: #E7A64A; --accent-soft: #3A2C17; --code: #1C222A;
  --c-key: #F0A875; --c-str: #9FD49A; --c-num: #8DBCF0; --c-lit: #D3A6F0; --c-tag: #8DBCF0; --c-attr: #F0A875;
  --c-add-bg: #1E3322; --c-del-bg: #3A2020; --c-add: #9FD49A; --c-del: #F09A90; --c-mark: #D3A6F0;
  --d-line: #3A434F; --d-accent: #E7A64A; color-scheme: dark }
body { background: var(--paper); color: var(--ink); margin: 0;
  font: 400 1.125rem/1.7 'Source Serif 4', Georgia, serif; }
main { max-width: 44rem; margin: 0 auto; padding-block: 4rem 6rem;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); }
.masthead { border-bottom: 1px solid var(--rule); padding-bottom: 2rem; margin-bottom: 1rem; }
.kicker { font: 600 0.8rem/1 'IBM Plex Sans', system-ui, sans-serif; letter-spacing: 0.14em; text-transform: uppercase;
  color: var(--accent); margin: 0 0 1rem; }
h1 { font: 600 clamp(2.4rem, 6vw, 3.6rem)/1.05 'IBM Plex Sans', system-ui, sans-serif; margin: 0 0 1rem;
  text-wrap: balance; letter-spacing: -0.01em; }
.dek { font-size: 1.3rem; line-height: 1.5; color: var(--muted); margin: 0; }
h2 { font: 600 1.6rem/1.25 'IBM Plex Sans', system-ui, sans-serif; margin: 3.5rem 0 1rem; text-wrap: balance; }
.num { display: inline-block; min-width: 2rem; color: var(--accent); }
p { margin: 0 0 1.1rem; }
em { font-style: italic; }
blockquote { margin: 1.8rem 0; padding: 0.4rem 0 0.4rem 1.4rem; border-left: 3px solid var(--accent);
  font-size: 1.3rem; line-height: 1.55; }
code { font: 0.88em 'JetBrains Mono', ui-monospace, monospace; background: var(--code); padding: 0.1em 0.3em;
  border-radius: 4px; }
pre { background: var(--code); border-radius: 10px; padding: 1.1rem 1.3rem; overflow-x: auto; margin: 1.4rem 0;
  font-size: 0.85rem; line-height: 1.55; }
pre code { background: none; padding: 0; font-size: 0.85rem; line-height: 1.55; }
.bar { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--paper);
  border-bottom: 1px solid var(--rule); }
.bar-in { max-width: 44rem; margin: 0 auto; display: flex; gap: 0.6rem; justify-content: flex-end; flex-wrap: wrap;
  padding-block: 0.6rem; padding-left: max(16px, env(safe-area-inset-left, 0px));
  padding-right: max(16px, env(safe-area-inset-right, 0px)); }
.bar button { font: 500 0.85rem 'IBM Plex Sans', system-ui, sans-serif; color: var(--ink); background: var(--panel);
  border: 1px solid var(--rule); border-radius: 999px; padding: 0.4rem 0.9rem; cursor: pointer; }
.bar button:hover { border-color: var(--accent); }
.howto { font: 400 0.92rem/1.5 'IBM Plex Sans', system-ui, sans-serif; color: var(--muted); margin-top: 1.2rem; }
details.deep { margin: 1.2rem 0 1.6rem; border: 1px solid var(--rule); border-radius: 12px; background: var(--panel); }
details.deep > summary { cursor: pointer; padding: 0.8rem 1.1rem; list-style: none;
  font: 600 0.95rem/1.4 'IBM Plex Sans', system-ui, sans-serif; color: var(--accent); }
details.deep > summary::-webkit-details-marker { display: none; }
details.deep > summary::before { content: "+"; display: inline-block; width: 1.2rem; font-weight: 600; }
details.deep[open] > summary::before { content: "−"; }
details.deep > :not(summary) { margin-left: 1.1rem; margin-right: 1.1rem; }
details.deep > :last-child { margin-bottom: 1.1rem; }
details.deep p, details.deep li { font-size: 1rem; }
details.deep pre { background: var(--code); }
.table { overflow-x: auto; margin: 1rem 0; }
table { border-collapse: collapse; width: 100%; font: 400 0.9rem/1.45 'IBM Plex Sans', system-ui, sans-serif; }
th, td { text-align: left; vertical-align: top; padding: 0.5rem 0.6rem; border-bottom: 1px solid var(--rule); }
th { font-weight: 600; color: var(--muted); }
ul.obs { margin: 1.2rem 0 1.6rem; padding: 0; list-style: none; display: grid; gap: 0.55rem; }
ul.obs li { position: relative; padding-left: 1.4rem; line-height: 1.55; }
ul.obs li::before { content: ""; position: absolute; left: 0.2rem; top: 0.68em; width: 0.45rem; height: 0.45rem;
  border-radius: 50%; background: var(--accent); }
ul.obs b { font-weight: 600; }
blockquote cite { display: block; margin-top: 0.6rem; font: 400 0.9rem 'IBM Plex Sans', system-ui, sans-serif;
  font-style: normal; color: var(--muted); }
figure { margin: 2.2rem 0; }
figure .art { overflow-x: auto; background: var(--panel); border: 1px solid var(--rule); border-radius: 12px;
  padding: 1rem; }
figure svg { display: block; width: 100%; min-width: 560px; height: auto; }
figcaption { font: 400 0.9rem/1.5 'IBM Plex Sans', system-ui, sans-serif; color: var(--muted); margin-top: 0.6rem; }
@media (min-width: 1100px) { figure { margin-left: -6rem; margin-right: -6rem; } }
.sources { margin-top: 4rem; border-top: 1px solid var(--rule); padding-top: 1rem;
  font: 400 0.92rem/1.6 'IBM Plex Sans', system-ui, sans-serif; color: var(--muted); }
h3.step { font: 600 1.05rem/1.4 'IBM Plex Sans', system-ui, sans-serif; margin: 2.2rem 0 0.4rem; color: var(--accent); }
.sources h2 { font-size: 1rem; margin: 1rem 0 0.5rem; }
.sources ul { padding-left: 1.2rem; }
.cj-k { color: var(--c-key); } .cj-s { color: var(--c-str); } .cj-n { color: var(--c-num); } .cj-l { color: var(--c-lit); font-weight: 600; }
.cj-t { color: var(--c-tag); } .cj-a { color: var(--c-attr); } .cj-m { color: var(--c-mark); font-weight: 600; }
pre code .ln { display: block; margin: 0 -1.3rem; padding: 0 1.3rem; }
pre code .ln.add { background: var(--c-add-bg); } pre code .ln.del { background: var(--c-del-bg); }
pre code .ln.add .sg { color: var(--c-add); font-weight: 600; } pre code .ln.del .sg { color: var(--c-del); font-weight: 600; }
ol.rules { padding-left: 1.4rem; } ol.rules > li { margin: 0 0 1.6rem; } ol.rules blockquote { font-size: 1.05rem; margin: 0.4rem 0 0.6rem; }
ol.rules > li::marker { font: 600 1.1rem "IBM Plex Sans", system-ui, sans-serif; color: var(--accent); }
.handoff { margin-top: 2rem; padding: 1rem 1.2rem; border: 1px solid var(--accent); border-radius: 12px; background: var(--accent-soft); }
""".trimIndent()

val FONTS = "https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600" +
    "&family=JetBrains+Mono:wght@400&family=Source+Serif+4:ital,wght@0,400;0,600;1,400&display=swap"

fun figure(name: String): String {
    val svg = File(diagrams, "$name.svg").readText().trim()
    val caption = Regex("<title[^>]*>(.*?)</title>").find(svg)?.groupValues?.get(1) ?: name
    return """<figure id="fig-$name"><div class="art">$svg</div><figcaption>$caption</figcaption></figure>"""
}

val body = Regex("<!--diagram:([a-z-]+)-->").replace(File(essay).readText()) { figure(it.groupValues[1]) }

/** The bar above the page: open or close every technical detail, and the theme: as the system has it, light or dark. */
val BAR = """<nav class="bar" aria-label="Page controls"><div class="bar-in">
<button type="button" id="all">Show all detail</button>
<button type="button" id="theme">Theme: auto</button>
</div></nav>"""

val SCRIPT = """<script>
(function () {
  var root = document.documentElement, all = document.getElementById('all'), theme = document.getElementById('theme');
  var order = ['auto', 'light', 'dark'];
  function setTheme(t) {
    if (t === 'auto') root.removeAttribute('data-theme'); else root.setAttribute('data-theme', t);
    theme.textContent = 'Theme: ' + t;
    try { localStorage.setItem('theme', t); } catch (e) {}
  }
  var saved = 'auto';
  try { saved = localStorage.getItem('theme') || 'auto'; } catch (e) {}
  setTheme(order.indexOf(saved) < 0 ? 'auto' : saved);
  theme.addEventListener('click', function () {
    var now = root.getAttribute('data-theme') || 'auto';
    setTheme(order[(order.indexOf(now) + 1) % order.length]);
  });
  function details() { return Array.prototype.slice.call(document.querySelectorAll('details.deep')); }
  function sync() { all.textContent = details().every(function (d) { return d.open; }) ? 'Hide all detail' : 'Show all detail'; }
  all.addEventListener('click', function () {
    var open = !details().every(function (d) { return d.open; });
    details().forEach(function (d) { d.open = open; });
    sync();
  });
  details().forEach(function (d) { d.addEventListener('toggle', sync); });

  // Code: JSON, HTML, HTTP and goal text coloured by token; a diff's lines marked added or removed.
  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function span(c, s) { return '<span class="cj-' + c + '">' + esc(s) + '</span>'; }
  function tokens(text, re, paint) {
    var out = '', at = 0, m;
    re.lastIndex = 0;
    while ((m = re.exec(text)) !== null) {
      out += esc(text.slice(at, m.index)) + paint(m);
      at = m.index + m[0].length;
      if (m[0].length === 0) re.lastIndex++;
    }
    return out + esc(text.slice(at));
  }
  var JSON_RE = /("(?:[^"\\]|\\.)*")(\s*:)?|\b(true|false|null)\b|(-?\b\d+(?:\.\d+)?\b)/g;
  function json(t) {
    return tokens(t, JSON_RE, function (m) {
      if (m[1]) return m[2] ? span('k', m[1]) + esc(m[2]) : span('s', m[1]);
      if (m[3]) return span('l', m[3]);
      return span('n', m[4]);
    });
  }
  var HTML_RE = /(<\/?)([a-zA-Z][a-zA-Z0-9]*)|([a-zA-Z-]+)(=)("[^"]*")|(\/?>)/g;
  function html(t) {
    return tokens(t, HTML_RE, function (m) {
      if (m[2]) return esc(m[1]) + span('t', m[2]);
      if (m[3]) return span('a', m[3]) + esc(m[4]) + span('s', m[5]);
      return esc(m[6]);
    });
  }
  function http(t) {
    var head = /^(GET|PUT|POST|PATCH|DELETE|HTTP\/1\.1|→)(\s+)(.*)/.exec(t);
    if (head) return span('l', head[1]) + head[2] + esc(head[3]);
    var hdr = /^([A-Za-z-]+)(:)(\s.*)/.exec(t);
    if (hdr) return span('k', hdr[1]) + esc(hdr[2]) + json(hdr[3]);
    var code = /^(\d{3})(\s.*)/.exec(t);
    if (code) return span('n', code[1]) + esc(code[2]);
    return json(t);
  }
  var GOAL_RE = /(`[^`]*`)|(\{[a-z]+\})|\b(Whenever|If|Once|Or give up|Otherwise|Until|In)\b/g;
  function goal(t) {
    return tokens(t, GOAL_RE, function (m) {
      if (m[1]) return span('m', m[1]);
      if (m[2]) return span('n', m[2]);
      return span('k', m[3]);
    });
  }
  var PAINT = { json: json, html: html, http: http, goal: goal };
  Array.prototype.forEach.call(document.querySelectorAll('pre code[data-lang]'), function (el) {
    var paint = PAINT[el.getAttribute('data-lang')] || esc;
    var lines = el.textContent.split('\n');
    if (el.classList.contains('diff')) {
      el.innerHTML = lines.map(function (line) {
        var sign = line.charAt(0), rest = line.slice(2);
        var kind = sign === '+' ? 'add' : sign === '-' ? 'del' : 'same';
        var mark = kind === 'same' ? '  ' : '<span class="sg">' + sign + '</span> ';
        return '<span class="ln ' + kind + '">' + mark + paint(rest) + '</span>';
      }).join('');
    } else {
      el.innerHTML = lines.map(paint).join('\n');
    }
  });
})();
</script>"""

val head = """<title>$TITLE</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="$FONTS">
<style>
$STYLE
</style>"""

File(out).writeText(
    """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
$head
</head><body>
$BAR
<main>
$body
</main>
$SCRIPT
</body></html>
""",
)
println("wrote $out")
if (artifact.isNotBlank()) {
    File(artifact).writeText("$head\n$BAR\n<main>\n$body\n</main>\n$SCRIPT\n")
    println("wrote $artifact")
}
