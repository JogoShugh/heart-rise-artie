// "The Browser for Agents" as one page: essay.html with each <!--diagram:name--> replaced by diagrams/name.svg,
// inlined in a figure captioned by the diagram's own title. Run diagrams.kts first.
//   artie run page.kts out=index.html artifact=/tmp/browser-for-agents.html
// out is a whole document; artifact, if given, is the same page without the document skeleton.
import java.io.File

val essay by input(default = "essay.html", question = "Where is the essay?")
val diagrams by input(default = "diagrams", question = "Which folder holds the SVG files?")
val out by input(default = "index.html", question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")

val TITLE = "The Browser for Agents"

val STYLE = """
:root {
  --paper: #F7F3EA; --ink: #1D2128; --muted: #5B6370; --rule: #DDD5C6; --panel: #FFFDF8;
  --accent: #A45F12; --accent-soft: #F6E3C2; --code: #EFE9DC;
  --d-ink: var(--ink); --d-muted: var(--muted); --d-line: #C9C2B4; --d-panel: var(--panel);
  --d-accent: #B86E12; --d-accent-soft: var(--accent-soft);
  --d-font: 'IBM Plex Sans', system-ui, sans-serif; --d-mono: 'JetBrains Mono', ui-monospace, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --paper: #14181E; --ink: #ECE6DA; --muted: #A3ABB6; --rule: #2C333D; --panel: #1C222A;
  --accent: #E7A64A; --accent-soft: #3A2C17; --code: #1C222A;
  --d-line: #3A434F; --d-accent: #E7A64A; color-scheme: dark } }
:root[data-theme="dark"] {
  --paper: #14181E; --ink: #ECE6DA; --muted: #A3ABB6; --rule: #2C333D; --panel: #1C222A;
  --accent: #E7A64A; --accent-soft: #3A2C17; --code: #1C222A;
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
pre { background: var(--code); border-radius: 10px; padding: 1.1rem 1.3rem; overflow-x: auto; margin: 1.4rem 0; }
pre code { background: none; padding: 0; font-size: 0.85rem; line-height: 1.55; }
figure { margin: 2.2rem 0; }
figure .art { overflow-x: auto; background: var(--panel); border: 1px solid var(--rule); border-radius: 12px;
  padding: 1rem; }
figure svg { display: block; width: 100%; min-width: 560px; height: auto; }
figcaption { font: 400 0.9rem/1.5 'IBM Plex Sans', system-ui, sans-serif; color: var(--muted); margin-top: 0.6rem; }
@media (min-width: 1100px) { figure { margin-left: -6rem; margin-right: -6rem; } }
.sources { margin-top: 4rem; border-top: 1px solid var(--rule); padding-top: 1rem;
  font: 400 0.92rem/1.6 'IBM Plex Sans', system-ui, sans-serif; color: var(--muted); }
.sources h2 { font-size: 1rem; margin: 1rem 0 0.5rem; }
.sources ul { padding-left: 1.2rem; }
""".trimIndent()

val FONTS = "https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600" +
    "&family=JetBrains+Mono:wght@400&family=Source+Serif+4:ital,wght@0,400;0,600;1,400&display=swap"

fun figure(name: String): String {
    val svg = File(diagrams, "$name.svg").readText().trim()
    val caption = Regex("<title[^>]*>(.*?)</title>").find(svg)?.groupValues?.get(1) ?: name
    return """<figure id="fig-$name"><div class="art">$svg</div><figcaption>$caption</figcaption></figure>"""
}

val body = Regex("<!--diagram:([a-z-]+)-->").replace(File(essay).readText()) { figure(it.groupValues[1]) }

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
</head><body><main>
$body
</main></body></html>
""",
)
println("wrote $out")
if (artifact.isNotBlank()) {
    File(artifact).writeText("$head\n<main>\n$body\n</main>\n")
    println("wrote $artifact")
}
