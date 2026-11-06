// Listeners, live, as a page: the same watch run as three listeners and as one loop, and how long each took to
// react. The facts live in results.yaml; this script only lays them out, in the reports' shared shell.
//   artie run report.kts out=../../docs/reports/listeners-live/index.html artifact=/tmp/listeners-live.html
import java.io.File
import org.yaml.snakeyaml.Yaml

val data by input(default = "results.yaml", question = "Where are the results?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")
val assets by input(default = "../../docs/reports/assets", question = "Where are the reports' shared assets?")

fun Any?.map(): Map<String, Any?> = (this as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
fun Any?.list(): List<Map<String, Any?>> = (this as? List<*>).orEmpty().map { it.map() }
fun esc(s: Any?) = s.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

val yaml = Yaml().load<Any?>(File(data).readText()).map()
val runs = yaml["runs"].list()

fun mean(run: Map<String, Any?>) = run["reactions"].list().map { (it["seconds"] as Number).toDouble() }.average()

val tiles = runs.joinToString("") { run ->
    val tone = if (run["mode"].toString().contains("listeners")) "good" else "quiet"
    """<div class="tile $tone"><b>${"%.0f".format(mean(run))} s</b>""" +
        """<span class="sub">mean reaction, ${esc(run["mode"])}</span></div>"""
} + """<div class="tile good"><b>0</b><span class="sub">things said twice, in either run</span></div>"""

val events = runs.first()["reactions"].list().map { it["event"].toString() }
val rows = events.joinToString("") { event ->
    val cells = runs.joinToString("") { run ->
        val s = run["reactions"].list().first { it["event"] == event }["seconds"]
        """<td class="num">$s s</td>"""
    }
    "<tr><td>${esc(event)}</td>$cells</tr>"
}
val head = runs.joinToString("") { "<th>${esc(it["mode"])} (${esc(it["run"])})</th>" }
val findings = (yaml["findings"] as List<*>).joinToString("") { "<li>${esc(it)}</li>" }

val body = """
  <div class="tiles">$tiles</div>
  <section>
    <h2>The same watch, two ways</h2>
    <p>One watch over the scratch repository, through the HEART gateway: a stand-in veracode-bot's failed scan is
    held for security, each alert is answered once, and each triage comment is handled slowly, 15 seconds, as a model
    call is. The same timeline played against it twice: once as three listeners, each on its own, once as today's
    single loop, every ${esc(yaml["pace"])}.</p>
    <div class="scroll"><table><thead><tr><th>Event</th>$head</tr></thead><tbody>$rows</tbody></table></div>
  </section>
  <section>
    <h2>What it shows</h2>
    <ul class="gaps">$findings</ul>
  </section>
"""

val style = """
section, .tiles { margin-top: 28px; }
.tile { cursor: default; }
.scroll { overflow-x: auto; max-width: 100%; }
table { border-collapse: collapse; width: 100%; }
th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid var(--rule); }
td.num { font-variant-numeric: tabular-nums; }
ul.gaps { margin: 0; padding-left: 18px; display: grid; gap: 8px; }
"""

val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "Listeners, Live")
    .replace("@@HEADING@@", "Listeners, live: each on its own, against one loop")
    .replace("@@LEDE@@", "The same watch and timeline on a scratch GitHub repository, as three listeners and as one " +
        "loop, and how long each took to react, by GitHub's own times. One run each, ${yaml["asOf"]}.")
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
