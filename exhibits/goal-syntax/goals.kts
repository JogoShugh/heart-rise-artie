// The goal syntax as a page: three goals written in it (as read, and as source), every element decided one after
// another, and what watching for a long time asks of a goal. The decisions live in syntax.yaml and the goals in
// goals/*.goal.md; this script only lays them out (spec/ideas/goals-that-work-alongside-others.md, "Review").
//   artie run goals.kts out=../../docs/reports/goal-syntax/index.html artifact=/tmp/goal-syntax.html
// out gets a whole document for GitHub Pages; artifact, when given, the same page without the document skeleton.
// The look comes from the reports' shared assets (report-shell.html, report-base.css, theme-switch.js), inlined.
import java.io.File
import org.yaml.snakeyaml.Yaml

val data by input(default = "syntax.yaml", question = "Where are the decisions?")
val goalsDir by input(default = "goals", question = "Where are the goals?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")
val source by input(default = "goals.kts", question = "Where is this script, to show it on the page?")
val assets by input(default = "../../docs/reports/assets", question = "Where are the reports' shared assets?")

fun Any?.map(): Map<String, Any?> = (this as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
fun Any?.list(): List<Any?> = (this as? List<*>).orEmpty()
fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

val yaml = Yaml().load<Any?>(File(data).readText()).map()

/** The words that open a step or a line under it, each shown as a keyword. */
val keywords = Regex("""^(In|On|Once|Whenever|Until|Otherwise,|Or give up)(?!\w)""")

/** A line of a goal as read: values as chips, names as tags, its opening keyword marked. */
fun inline(line: String): String = esc(line)
    .replace(Regex("""`as (\w+)`""")) { """<span class="as">as ${it.groupValues[1]}</span>""" }
    .replace(Regex("""\{([\w.]+)\}""")) { """<span class="val">{${it.groupValues[1]}}</span>""" }
    .replace(keywords) { """<span class="kw">${it.value}</span>""" }

/** One bullet: its depth, its first line, and the lines under it that aren't bullets ("Otherwise, …"). */
class Item(val depth: Int, val lines: MutableList<String>)

fun bullets(lines: List<String>): List<Item> {
    val items = mutableListOf<Item>()
    lines.filter { it.isNotBlank() }.forEach { line ->
        val indent = line.length - line.trimStart().length
        val text = line.trim()
        if (text.startsWith("- ")) items += Item(indent / 2, mutableListOf(text.removePrefix("- ")))
        else items.lastOrNull()?.lines?.add(text)
    }
    return items
}

fun renderItems(items: List<Item>, depth: Int = 0): String {
    val sb = StringBuilder("<ul>")
    var i = 0
    while (i < items.size) {
        val item = items[i]
        var j = i + 1
        while (j < items.size && items[j].depth > depth) j++
        val rest = item.lines.drop(1).joinToString("") { """<div class="alt">${inline(it)}</div>""" }
        sb.append("<li><div>${inline(item.lines.first())}</div>$rest")
        if (j > i + 1) sb.append(renderItems(items.subList(i + 1, j), depth + 1))
        sb.append("</li>")
        i = j
    }
    return sb.append("</ul>").toString()
}

/** A goal as read: header values, the brief, and each section, the manners and the checks set apart. */
fun renderGoal(text: String): String {
    val parts = text.split(Regex("""(?m)^---\s*$"""), limit = 3)
    val head = Yaml().load<Any?>(parts[1]).map()
    val body = parts[2].lines()
    val inputs = head["inputs"].map().entries.joinToString("") { (name, spec) ->
        val s = spec.map()
        val default = s["default"].let { d -> d.map()["now"]?.let { "now, as $it" } ?: d?.toString() ?: "–" }
        """<tr><td><span class="val">{$name}</span></td><td>${esc(s.text("question"))}</td><td>${esc(default)}</td></tr>"""
    }
    val sections = mutableListOf("" to mutableListOf<String>())
    body.forEach { line ->
        if (line.startsWith("## ")) sections += line.removePrefix("## ").trim() to mutableListOf()
        else sections.last().second += line
    }
    val brief = sections.first().second.joinToString(" ") { it.trim() }.trim()
    val rest = sections.drop(1).joinToString("") { (title, lines) ->
        val paragraphs = lines.takeWhile { !it.trimStart().startsWith("- ") }.joinToString(" ") { it.trim() }.trim()
        val list = renderItems(bullets(lines.dropWhile { !it.trimStart().startsWith("- ") }))
        val cls = when (title) { "Working alongside others" -> "manners"; "Done when" -> "done"; else -> "work" }
        """<h3>${esc(title)}</h3><div class="$cls">${if (paragraphs.isEmpty()) "" else "<p>${inline(paragraphs)}</p>"}$list</div>"""
    }
    return """<p class="kicker">Goal · starts at ${esc(head.text("start"))}""" +
        "</p>" + (if (head["manners"] != null) """<p class="sub">manners: <span class="val">${esc(head.text("manners"))}</span></p>""" else "") +
        """<div class="scroll inputs"><table><thead><tr><th>Asks for</th><th>Question</th><th>Unless given</th></tr></thead>""" +
        """<tbody>$inputs</tbody></table></div><p class="brief">${inline(brief)}</p>$rest"""
}

val words = yaml["words"].list().map { it.map() }.joinToString("") { w ->
    """<div class="tile"><b>${esc(w.text("word"))}</b><span class="sub">${esc(w.text("says"))}</span></div>"""
}

val goals = yaml["goals"].list().map { it.map() }
val tabs = goals.mapIndexed { i, g ->
    """<button type="button" data-goal="$i" aria-pressed="${i == 0}">${esc(g.text("tab"))}</button>"""
}.joinToString("")
val panels = goals.mapIndexed { i, g ->
    val text = File(goalsDir, g.text("file")).readText()
    """<article class="goal" data-goal="$i"${if (i == 0) "" else " hidden"}>
      <p class="sub">${esc(g.text("about"))} <span class="mono">goals/${esc(g.text("file"))}</span></p>
      <div class="paper read">${renderGoal(text)}</div>
      <div class="paper source"><pre class="src">${esc(text)}</pre></div>
    </article>"""
}.joinToString("\n")

val verdictTone = mapOf("keep" to "safe", "add" to "good", "drop" to "bad")
val elements = yaml["elements"].list().map { it.map() }.joinToString("\n") { e ->
    val note = e.text("note").let { if (it.isEmpty()) "" else """<p class="sub">${esc(it)}</p>""" }
    """<li class="element ${verdictTone[e.text("verdict")] ?: "quiet"}"><div class="head"><b>${esc(e.text("name"))}</b>""" +
        """<span class="tag quiet">${esc(e.text("status"))}</span><span class="tag ${verdictTone[e.text("verdict")] ?: "quiet"}">${esc(e.text("verdict"))}</span></div>""" +
        """<p class="example">${inline(e.text("example"))}</p><p>${esc(e.text("means"))}</p>$note</li>"""
}
val watching = yaml["watching"].list().joinToString("") { "<li>${esc(it.toString())}</li>" }

val style = """
.words { grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); }
.words .tile b { color: var(--accent); }
.bar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; }
.seg { display: inline-flex; border: 1px solid var(--rule); border-radius: 8px; overflow: hidden; background: var(--panel); }
.seg button { font: 500 14px var(--body); color: var(--muted); background: none; border: 0; padding: 6px 14px; cursor: pointer; }
.seg button[aria-pressed="true"] { background: var(--accent); color: var(--panel); }
.goals[data-view="read"] .source, .goals[data-view="source"] .read { display: none; }
.goal { display: grid; gap: 10px; }
.paper { background: var(--panel); border: 1px solid var(--rule); border-radius: 6px; padding: clamp(18px, 4vw, 40px); min-width: 0; }
.paper.source { padding: 0; overflow-x: auto; }
.read { font-size: 16.5px; line-height: 1.65; }
.read h3 { font: 600 13px var(--mono); letter-spacing: .08em; text-transform: uppercase; color: var(--muted);
  margin: 28px 0 10px; padding-top: 14px; border-top: 1px solid var(--rule); }
.read p { margin: 0 0 12px; }
.kicker { font: 500 12px var(--mono); letter-spacing: .06em; text-transform: uppercase; color: var(--accent); }
.inputs { margin: 6px 0 16px; } .inputs table { font-size: 14px; }
.val { font: .84em var(--mono); background: var(--safe-bg); color: var(--safe); padding: 0 3px; border-radius: 4px; white-space: nowrap; }
.as { font: 500 12px var(--body); background: var(--quiet-bg); color: var(--muted); padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.kw { font-weight: 600; color: var(--accent); }
.alt { font-style: italic; color: var(--muted); }
.read ul { margin: 0; padding-left: 1.3em; } .read li { margin: 6px 0; } .read li::marker { color: var(--muted); }
.work > ul > li { margin-top: 14px; }
.manners { background: var(--good-bg); border-left: 3px solid var(--good); padding: 14px 18px; border-radius: 0 6px 6px 0; }
.done li::marker { content: "✓  "; color: var(--good); }
ol.elements { margin: 0; padding-left: 1.6em; display: grid; gap: 12px; }
.element { background: var(--panel); border: 1px solid var(--rule); border-left: 3px solid var(--c); border-radius: 6px; padding: 12px 16px; }
.element::marker { font: 500 13px var(--mono); color: var(--muted); }
.element .head { display: flex; flex-wrap: wrap; gap: 6px 10px; align-items: baseline; margin-bottom: 6px; }
.element .head b { font: 600 17px var(--display); }
.element .head .tag { margin: 0; }
.element p { margin: 0 0 4px; }
.element .example { font-style: italic; }
ul.watching { margin: 0; padding-left: 1.3em; display: grid; gap: 6px; max-width: 80ch; }
"""

val body = """
  <section>
    <h2>Five words</h2>
    <div class="tiles words">$words</div>
    <p class="sub">Anything that needs more than these is written in the generated Kotlin, not in the goal.</p>
  </section>
  <section>
    <h2>Three goals in them</h2>
    <div class="bar">
      <div class="seg" role="group" aria-label="Goal" id="goal-tabs">$tabs</div>
      <div class="seg" role="group" aria-label="View" id="view-tabs"><button type="button" data-view="read" aria-pressed="true">As read</button><button type="button" data-view="source" aria-pressed="false">Source</button></div>
    </div>
    <div class="goals" id="goals" data-view="read">
$panels
    </div>
    <p class="sub">Chips are the run's values; tags such as <span class="as">as issue</span> name what a step made; a
    keyword opens a step; italic lines are a step's other outcome.</p>
  </section>
  <section>
    <h2>Every element, decided</h2>
    <ol class="elements">
$elements
    </ol>
  </section>
  <section>
    <h2>Watching for a long time</h2>
    <ul class="watching">$watching</ul>
  </section>
  <section>
    <h2>How this page is made</h2>
    <details class="box"><summary>syntax.yaml: the decisions</summary><div class="scroll"><pre class="src">${esc(File(data).readText())}</pre></div></details>
    <details class="box"><summary>goals.kts: the script that lays them out</summary><div class="scroll"><pre class="src">${esc(File(source).readText())}</pre></div></details>
  </section>
"""

val script = """
// The goal tabs show one goal; the view tabs switch every goal between as read and its source.
(function () {
  function group(id, pick) {
    var buttons = document.querySelectorAll('#' + id + ' button');
    buttons.forEach(function (b) {
      b.addEventListener('click', function () {
        buttons.forEach(function (o) { o.setAttribute('aria-pressed', String(o === b)); });
        pick(b);
      });
    });
  }
  group('goal-tabs', function (b) {
    document.querySelectorAll('.goal').forEach(function (g) { g.hidden = g.dataset.goal !== b.dataset.goal; });
  });
  group('view-tabs', function (b) { document.getElementById('goals').dataset.view = b.dataset.view; });
})();
"""

/** The page: the reports' shared shell, filled with this page's own parts. */
val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "Goals in Five Words")
    .replace("@@HEADING@@", "Goals in five words: On, Once, Whenever, Until, Otherwise")
    .replace("@@LEDE@@", "How a goal is written for ARTIE: in words a person would write to a colleague, that work " +
        "alongside others and can keep watching. Three goals in it, every element decided, and what watching asks. " +
        "Reviewed ${yaml.text("asOf")}.")
    .replace("@@BASE@@", File(assets, "report-base.css").readText())
    .replace("@@THEME@@", File(assets, "theme-switch.js").readText())
    .replace("@@STYLE@@", style).replace("@@SCRIPT@@", script).replace("@@BODY@@", body)

File(out).apply { parentFile.mkdirs() }.writeText(
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"></head>" +
        "<body style=\"margin:0\">\n" + page + "</body></html>\n"
)
if (artifact.isNotBlank()) File(artifact).writeText(page)
println("wrote $out" + if (artifact.isNotBlank()) " and $artifact" else "")
