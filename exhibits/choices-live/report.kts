// Choices from state, live, as a page: who was asked for what, how, and what they said; the two dialects side
// by side; and where each client keeps to the choices. The facts live in results.yaml; this only lays them out,
// in the reports' shared shell.
//   artie run report.kts out=../../docs/reports/choices-live/index.html artifact=/tmp/choices-live.html
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
val attempts = yaml["attempts"].list()

/** A table of [rows] with these [columns], each a key and its heading; [tone] may colour a row's first cell. */
fun table(rows: List<Map<String, Any?>>, columns: List<Pair<String, String>>, tone: (Map<String, Any?>) -> String = { "" }) =
    """<div class="scroll"><table><thead><tr>""" + columns.joinToString("") { "<th>${esc(it.second)}</th>" } +
        "</tr></thead><tbody>" + rows.joinToString("") { row ->
            "<tr>" + columns.mapIndexed { i, (key, _) ->
                val cell = esc(row[key])
                if (i == 0 && tone(row).isNotEmpty()) """<td><span class="tag ${tone(row)}">$cell</span></td>""" else "<td>$cell</td>"
            }.joinToString("") + "</tr>"
        } + "</tbody></table></div>"

fun answerTone(row: Map<String, Any?>) = when {
    row["answer"].toString().startsWith("done") -> "good"
    row["answer"].toString().startsWith("422") -> "bad"
    else -> "safe"
}

fun done(row: Map<String, Any?>) = row["answer"].toString().startsWith("done")
fun kind(name: String) = attempts.filter { it["kind"] == name }
val outside = kind("outside")
val inside = kind("inside")
val through = outside.count(::done)
val tiles = """
  <div class="tile safe"><b>${outside.count { !done(it) }} / ${outside.size}</b><span class="sub">asks for someone who can't be chosen now, refused</span></div>
  <div class="tile good"><b>${inside.count(::done)} / ${inside.size}</b><span class="sub">asks for someone who can, done</span></div>
  <div class="tile ${if (through == 0) "good" else "bad"}"><b>$through</b><span class="sub">values outside the choices that got through</span></div>
  <div class="tile quiet"><b>${kind("ambiguous").size}</b><span class="sub">ask that fit two choices, answered with a question</span></div>"""

fun section(title: String, lede: String, body: String) =
    "<section><h2>${esc(title)}</h2><p>${esc(lede)}</p>$body</section>"

val askColumns = listOf("answer" to "Answer", "asked" to "Asked", "how" to "How", "said" to "Said", "run" to "Run")
val body = """
  <div class="tiles">$tiles</div>
""" + section(
    "Basketball: a player from the chosen team, still in the game",
    "record-foul's player is one of the chosen team's players who have not fouled out. Rowan fouled out with " +
        "four more fouls; then a fifth was asked for, three ways, and one on Kai.",
    table(attempts.filter { it["service"] == "basketball" }, askColumns, ::answerTone),
) + section(
    "Tickets: an agent from another resource, on duty, for the ticket's priority",
    "assign's agent is one of the desk's agents at /agents?priority=…, filled from the ticket, who are on duty. " +
        "Ticket 1 is high: Priya and Marco. Then it goes urgent: only Priya.",
    table(attempts.filter { it["service"] == "tickets" }, askColumns, ::answerTone),
) + section(
    "The same choices in the two dialects",
    "record-foul's player on the live game, after Rowan fouled out, as each dialect renders it.",
    table(yaml["dialects"].list(), listOf("dialect" to "Dialect", "says" to "Says", "values" to "Values", "shared" to "Shared")),
) + section(
    "Where the choices are kept",
    "Every client that offers the choices also refuses anything outside them, and the server refuses it again.",
    table(yaml["clients"].list(), listOf("client" to "Client", "resolves" to "Resolves", "shows" to "Shows",
        "refuses" to "Refuses", "since" to "Since")),
) + """
  <section>
    <h2>What it shows</h2>
    <ul class="gaps">${yaml["findings"].let { it as List<*> }.joinToString("") { "<li>${esc(it)}</li>" }}</ul>
  </section>
  <section>
    <h2>Not shown here</h2>
    <ul class="gaps">${yaml["gaps"].let { it as List<*> }.joinToString("") { "<li>${esc(it)}</li>" }}</ul>
  </section>
"""

val style = """
section, .tiles { margin-top: 28px; }
.tile { cursor: default; }
td { min-width: 90px; }
ul.gaps { margin: 0; padding-left: 18px; display: grid; gap: 8px; max-width: 80ch; }
"""

val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "Choices, Live")
    .replace("@@HEADING@@", "Choices from state, live: only what can be chosen now")
    .replace("@@LEDE@@", "HAL Schema Forms' hr:options on two live services, basketball and a support desk: asked by " +
        "script, in words to Claude, and past the client, to the server. One run each, ${yaml["asOf"]}.")
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
