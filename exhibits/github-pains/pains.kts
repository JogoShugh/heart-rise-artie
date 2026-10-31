// What people struggle with when they automate GitHub through its API, and where ARTIE and HEART answer it, as a page.
// The findings live in pains.yaml; this script only lays them out. The look comes from the reports' shared assets
// (report-shell.html, report-base.css, theme-switch.js), inlined, so the page needs nothing else at runtime.
//   artie run pains.kts out=../../docs/reports/github-pains/index.html artifact=/tmp/github-pains.html
// out gets a whole document for GitHub Pages; artifact, when given, the same page without the document skeleton.
import java.io.File
import org.yaml.snakeyaml.Yaml

val data by input(default = "pains.yaml", question = "Where are the findings?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")
val source by input(default = "pains.kts", question = "Where is this script, to show it on the page?")
val assets by input(default = "../../docs/reports/assets", question = "Where are the reports' shared assets?")

fun Any?.map(): Map<String, Any?> = (this as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
fun Any?.list(): List<Any?> = (this as? List<*>).orEmpty()
fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()

class Source(val label: String, val url: String, val confidence: String, val says: String)
class Pain(
    val id: String, val title: String, val fit: String, val hit: String, val sources: List<Source>,
    val ours: String, val heart: String, val missing: String, val seen: Boolean,
)
class Workflow(val name: String, val pains: List<String>, val why: String, val today: String)

val yaml = Yaml().load<Any?>(File(data).readText()).map()
val pains = yaml["pains"].list().map { it.map() }.map { p ->
    Pain(p.text("id"), p.text("title"), p.text("fit"), p.text("hit"),
        p["sources"].list().map { it.map() }.map { Source(it.text("label"), it.text("url"), it.text("confidence"), it.text("says")) },
        p.text("ours"), p.text("heart"), p.text("missing"), p["seen"] == true)
}
val workflows = yaml["workflows"].list().map { it.map() }.map { w ->
    Workflow(w.text("name"), w["pains"].list().map { it.toString() }, w.text("why"), w.text("today"))
}
val byId = pains.associateBy { it.id }

/** The fits in the order the page shows them: what each means, and the shared tone that colours it. */
class Fit(val means: String, val tone: String)
val fits = linkedMapOf(
    "strong" to Fit("HEART answers it directly", "good"),
    "medium" to Fit("HEART helps; the pain stays", "safe"),
    "against" to Fit("HEART makes it worse today", "bad"),
    "none" to Fit("not HEART's to answer", "quiet"),
)
val confidences = linkedMapOf(
    "documented" to "GitHub's own docs",
    "probed" to "we tried it against GitHub",
    "reported" to "a public thread or issue",
    "secondhand" to "a third party's account, not checked against GitHub",
)

fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun tone(fit: String) = fits[fit]?.tone ?: "quiet"
fun fitTag(fit: String) = """<span class="tag ${tone(fit)}">${esc(fit)}</span>"""
fun conf(c: String) = """<span class="conf $c">$c</span>"""

val tiles = fits.entries.joinToString("") { (fit, f) ->
    """<button class="tile ${f.tone}" type="button" data-fit="$fit" aria-pressed="false"><b>${pains.count { it.fit == fit }}</b>""" +
        """<span class="name">$fit</span><span class="sub">${esc(f.means)}</span></button>"""
}

val summaryRows = pains.joinToString("\n") { p ->
    """<tr data-fit="${p.fit}"><td><a href="#${p.id}">${esc(p.title)}</a></td><td>${fitTag(p.fit)}</td>""" +
        """<td>${if (p.seen) "yes" else """<span class="none">no</span>"""}</td>""" +
        """<td>${p.sources.joinToString(" ") { conf(it.confidence) }}</td></tr>"""
}

val cards = pains.joinToString("\n") { p ->
    val sources = p.sources.joinToString("") { s ->
        """<li><a href="${esc(s.url)}" rel="noopener" target="_blank">${esc(s.label)}</a> ${conf(s.confidence)}""" +
            """<div class="says">${esc(s.says)}</div></li>"""
    }
    val missing = if (p.missing == "-") "" else """<div class="row"><h4>Still to build</h4><p>${esc(p.missing)}</p></div>"""
    """<article class="pain ${tone(p.fit)}" id="${p.id}" data-fit="${p.fit}">
      <header><h3>${esc(p.title)}</h3>${fitTag(p.fit)}</header>
      <div class="row"><h4>What people hit</h4><p>${esc(p.hit)}</p></div>
      <div class="row"><h4>Our evidence</h4><p>${esc(p.ours)}</p></div>
      <div class="row"><h4>How HEART answers</h4><p>${esc(p.heart)}</p></div>
      $missing
      <details><summary>Sources (${p.sources.size})</summary><ul class="sources">$sources</ul></details>
    </article>"""
}

val workflowRows = workflows.joinToString("\n") { w ->
    val chips = w.pains.joinToString(" ") { id ->
        byId[id]?.let { """<a class="tag ${tone(it.fit)}" href="#$id">${esc(it.title)}</a>""" } ?: esc(id)
    }
    """<tr><td><b>${esc(w.name)}</b><div class="sub">${esc(w.why)}</div></td><td>$chips</td>""" +
        """<td class="num">${w.pains.count { byId[it]?.fit == "strong" }}</td><td class="sub">${esc(w.today)}</td></tr>"""
}

val confidenceKey = confidences.entries.joinToString(" ") { (c, means) -> "${conf(c)} ${esc(means)}" }

val style = """
.conf { font: 11px var(--mono); padding: 0 6px; border-radius: 4px; border: 1px solid var(--rule); color: var(--muted); white-space: nowrap; }
.conf.documented, .conf.probed { color: var(--good); border-color: var(--good); }
.conf.secondhand { border-style: dashed; }
td:first-child { min-width: 220px; }
.pains { display: grid; gap: 14px; }
.pain { background: var(--panel); border: 1px solid var(--rule); border-left: 4px solid var(--c); border-radius: 6px;
  padding: 16px 18px; display: grid; gap: 12px; scroll-margin-top: 16px; }
.pain > header { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; justify-content: space-between; }
.pain .row { display: grid; gap: 2px; }
.pain details summary { cursor: pointer; font-size: 14px; color: var(--muted); }
.sources { margin: 8px 0 0; padding-left: 18px; display: grid; gap: 8px; font-size: 14px; }
.says { color: var(--muted); max-width: 72ch; }
"""

val body = """
  <div class="tiles">$tiles</div>
  <div class="chipbar" id="showing" hidden><span id="showing-text"></span><button type="button" id="clear">clear</button></div>
  <section>
    <h2>At a glance</h2>
    <div class="scroll"><table>
      <thead><tr><th>Pain</th><th>Fit</th><th>Seen in our runs</th><th>Sources</th></tr></thead>
      <tbody>
$summaryRows
      </tbody></table></div>
    <p class="sub">$confidenceKey</p>
  </section>
  <section>
    <h2>The pains</h2>
    <div class="pains">
$cards
    </div>
  </section>
  <section>
    <h2>Workflows to prove it on</h2>
    <p>Real things people automate, with the pains each meets and how many of them are a strong fit. The first is
    the one to build: it meets the most, and it is the goal note's "Once…" in one sentence.</p>
    <div class="scroll"><table>
      <thead><tr><th>Workflow</th><th>Pains it meets</th><th class="num">Strong</th><th>What people use today</th></tr></thead>
      <tbody>
$workflowRows
      </tbody></table></div>
  </section>
  <section>
    <h2>How this page is made</h2>
    <details class="box"><summary>pains.yaml: the findings</summary><div class="scroll"><pre class="src">${esc(File(data).readText())}</pre></div></details>
    <details class="box"><summary>pains.kts: the script that lays them out</summary><div class="scroll"><pre class="src">${esc(File(source).readText())}</pre></div></details>
  </section>
"""

val script = """
// The fit tiles: one pressed shows only its pains and rows; pressing it again, or clear, shows all.
(function () {
  var tiles = document.querySelectorAll('.tile[data-fit]'), bar = document.getElementById('showing');
  function show(fit) {
    tiles.forEach(function (t) { t.setAttribute('aria-pressed', String(t.dataset.fit === fit)); });
    var shown = 0, all = 0;
    document.querySelectorAll('.pain[data-fit], tbody tr[data-fit]').forEach(function (el) {
      el.hidden = !!fit && el.dataset.fit !== fit;
      if (el.classList.contains('pain')) { all++; if (!el.hidden) shown++; }
    });
    bar.hidden = !fit;
    document.getElementById('showing-text').textContent = fit ? 'showing ' + shown + ' of ' + all + ' pains: ' + fit : '';
  }
  tiles.forEach(function (t) {
    t.addEventListener('click', function () { show(t.getAttribute('aria-pressed') === 'true' ? '' : t.dataset.fit); });
  });
  document.getElementById('clear').addEventListener('click', function () { show(''); });
})();
"""

/** The page: the reports' shared shell, filled with this page's own parts. */
val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "GitHub Automation Pains")
    .replace("@@HEADING@@", "Automating GitHub through its API: what hurts, and where HEART helps")
    .replace("@@LEDE@@", "${pains.size} pains people meet when scripts, bots and agents drive GitHub, each with its " +
        "sources, what our own runs showed, how HEART answers it, and what we would still have to build. Researched " +
        "${yaml.text("asOf")}; a tile filters the pains by fit.")
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
