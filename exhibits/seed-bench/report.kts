// The seed bench as a page: a goal file run by ARTIE with Claude, the script made from that run, and the script run
// with no model. The facts live in results.yaml; this script only lays them out. The look comes from the reports'
// shared assets (report-shell.html, report-base.css, theme-switch.js), inlined.
//   artie run report.kts out=../../docs/reports/seed-bench/index.html artifact=/tmp/seed-bench.html
// out gets a whole document for GitHub Pages; artifact, when given, the same page without the document skeleton.
import java.io.File
import org.yaml.snakeyaml.Yaml

val data by input(default = "results.yaml", question = "Where are the results?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")
val source by input(default = "report.kts", question = "Where is this script, to show it on the page?")
val assets by input(default = "../../docs/reports/assets", question = "Where are the reports' shared assets?")

fun Any?.map(): Map<String, Any?> = (this as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
fun Any?.list(): List<Map<String, Any?>> = (this as? List<*>).orEmpty().map { it.map() }
fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

val yaml = Yaml().load<Any?>(File(data).readText()).map()
val runs = yaml["runs"].list()
val toneWords = mapOf("good" to "did it right", "safe" to "stopped, nothing harmed", "bad" to "told someone twice")

fun tag(tone: String) = """<span class="tag $tone">${esc(toneWords[tone] ?: tone)}</span>"""
val claudeRuns = runs.count { it.text("run").startsWith("b") }
val scriptRuns = runs.count { it.text("run").startsWith("g") }
val fixes = runs.count { it.text("fixed") != "-" }
val tiles = listOf(
    Triple("quiet", claudeRuns, "live runs with Claude deciding"),
    Triple("good", scriptRuns, "live runs of the script alone, no model"),
    Triple("safe", fixes, "things found and fixed, each with a test first"),
    Triple("good", 0, "model calls in the runs of the script"),
).joinToString("") { (tone, n, says) -> """<div class="tile $tone"><b>$n</b><span class="sub">${esc(says)}</span></div>""" }

val proof = yaml["proof"].list().mapIndexed { i, p ->
    val arrow = if (i > 0) """<div class="arrow" aria-hidden="true">↓</div>""" else ""
    """$arrow<div class="node"><b>${esc(p.text("step"))}</b><div>${esc(p.text("what"))}</div>""" +
        """<div class="outcome">${esc(p.text("showed"))}</div></div>"""
}.joinToString("")

val runRows = runs.joinToString("\n") { r ->
    val tone = r.text("tone")
    val fixed = r.text("fixed").takeIf { it != "-" }?.let { """<div class="sub">Fixed: ${esc(it)}</div>""" }.orEmpty()
    """<li class="pull $tone"><div class="head"><b class="mono">${esc(r.text("run"))}</b> ${tag(tone)}</div>""" +
        """<div>${esc(r.text("found"))}</div>$fixed</li>"""
}
val gaps = yaml["gaps"].list().joinToString("\n") { g ->
    """<li><span class="tag quiet">${esc(g.text("where"))}</span> ${esc(g.text("what"))}</li>"""
}

fun shown(name: String, path: String, open: Boolean = false) = """<details class="box"${if (open) " open" else ""}>""" +
    """<summary>$name</summary><div class="scroll"><pre class="src">${esc(File(path).readText())}</pre></div></details>"""

val style = """
section, .tiles { margin-top: 28px; }
.tile { cursor: default; }
.flow { display: grid; gap: 6px; max-width: 760px; }
.node { background: var(--panel); border: 1px solid var(--rule); border-radius: 6px; padding: 12px 14px;
  display: grid; gap: 6px; min-width: 0; }
.node .outcome { color: var(--good); }
.arrow { text-align: center; color: var(--muted); font-size: 20px; line-height: 1; }
ol.pulls { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.pull { background: var(--panel); border: 1px solid var(--rule); border-left: 4px solid var(--c, var(--rule));
  border-radius: 6px; padding: 12px 14px; display: grid; gap: 4px; min-width: 0; }
.pull .head { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
ul.gaps { margin: 0; padding-left: 18px; display: grid; gap: 8px; }
"""

val body = """
  <div class="tiles">$tiles</div>
  <section>
    <h2>The idea, in three steps</h2>
    <p>A goal file says, in plain words, what a watch over a greenhouse bench should do. Claude decides how to do
    each part once, the first time it comes up. Those decisions are written down as a script. From then on the
    script does the work by itself, with no model, and costs no tokens.</p>
    <div class="flow">$proof</div>
  </section>
  <section>
    <h2>What the goal says</h2>
    <p>Alerts from the bench sensors are answered once and handed to the grower on duty. Sowing plans are merged
    when they fit the bench, and told why when they don't. Trays are closed once transplanted. Anything a person
    labels <span class="mono">hold</span> is left alone.</p>
    ${shown("seed-bench.goal.md", yaml.text("goalFile"), open = true)}
  </section>
  <section>
    <h2>The script made from the run</h2>
    <p>What artie generate wrote from run b20. It reads values again where they came from: the grower and the hold
    label are inputs, and the reason a merge gave up is the server's, read at the time.</p>
    ${shown("generated/seed-bench.kts", yaml.text("generated"))}
  </section>
  <section>
    <h2>Every run, in order</h2>
    <p>Each run that went wrong showed one gap. Each gap was fixed with a test first, then run live again. A guard
    beside every live run stopped it if anything was told twice.</p>
    <ol class="pulls">$runRows</ol>
  </section>
  <section>
    <h2>Still missing</h2>
    <ul class="gaps">$gaps</ul>
  </section>
  <section>
    <h2>How this page is made</h2>
    ${shown("results.yaml: what happened", data)}
    ${shown("report.kts: the script that lays it out", source)}
  </section>
"""

/** The page: the reports' shared shell, filled with this page's own parts. */
val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "Seed Bench, Zero Tokens")
    .replace("@@HEADING@@", "The seed bench: decided once, then run with no model")
    .replace("@@LEDE@@", "A greenhouse bench watched through the HEART gateway on a scratch GitHub repository: " +
        "Claude decides each part once, artie generate writes those decisions as a script, and the script does " +
        "the work alone. Run ${yaml.text("asOf")}.")
    .replace("@@BASE@@", File(assets, "report-base.css").readText())
    .replace("@@THEME@@", File(assets, "theme-switch.js").readText())
    .replace("@@STYLE@@", style).replace("@@SCRIPT@@", "").replace("@@BODY@@", body)

File(out).apply { parentFile.mkdirs() }.writeText(
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"></head>" +
        "<body style=\"margin:0\">\n" + page + "</body></html>\n"
)
if (artifact.isNotBlank()) File(artifact).writeText(page)
println("wrote $out" + if (artifact.isNotBlank()) " and $artifact" else "")
