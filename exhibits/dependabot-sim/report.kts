// The Dependabot simulation as a page: what the watch and GitHub's auto-merge did with the same kinds of pull
// request, and what is still missing. The facts live in results.yaml; this script only lays them out. The look comes
// from the reports' shared assets (report-shell.html, report-base.css, theme-switch.js), inlined.
//   artie run report.kts out=../../docs/reports/dependabot-sim/index.html artifact=/tmp/dependabot-sim.html
// out gets a whole document for GitHub Pages; artifact, when given, the same page without the document skeleton.
import java.io.File
import org.yaml.snakeyaml.Yaml

val data by input(default = "results.yaml", question = "Where are the results?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")
val source by input(default = "report.kts", question = "Where is this script, to show it on the page?")
val watcher by input(default = "watch.kts", question = "Where is the watch, to show it on the page?")
val assets by input(default = "../../docs/reports/assets", question = "Where are the reports' shared assets?")
val repo by input(default = "https://github.com/JogoShugh/heart-github-test", question = "Which repository ran it?")

fun Any?.map(): Map<String, Any?> = (this as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
fun Any?.list(): List<Map<String, Any?>> = (this as? List<*>).orEmpty().map { it.map() }
fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

val yaml = Yaml().load<Any?>(File(data).readText()).map()
val rounds = yaml["rounds"].list()
val pulls = rounds.flatMap { it["pulls"].list() }

fun pr(number: Any?) = """<a class="mono" href="$repo/pull/$number" rel="noopener" target="_blank">#$number</a>"""
fun tag(tone: String, words: String) = """<span class="tag $tone">${esc(words)}</span>"""
val outcomeWords = mapOf("good" to "right", "safe" to "stopped, said why", "bad" to "harm", "quiet" to "nothing")
val runWords = mapOf("good" to "finished", "safe" to "did the work, then failed a check", "bad" to "stopped")

fun count(by: String, tone: String) = pulls.count { it.text("by") == by && it.text("tone") == tone }
val tiles = listOf(
    Triple("good", count("watch", "good"), "the watch did the right thing: merged, or left a held one alone"),
    Triple("safe", count("watch", "safe"), "the watch gave up and said why, in the gateway's words"),
    Triple("bad", count("watch", "bad"), "the watch did harm"),
    Triple("bad", count("rival", "bad"), "GitHub's auto-merge merged over a person's hold"),
).joinToString("") { (tone, n, says) ->
    """<div class="tile $tone"><b>$n</b><span class="sub">${esc(says)}</span></div>"""
}

val timelines = yaml["timelines"].list().joinToString("\n") { t ->
    val events = t["events"].list().joinToString("") { e ->
        val tone = e.text("tone").ifEmpty { "quiet" }
        """<li class="$tone"><span class="at">${esc(e.text("at"))}</span><span>${esc(e.text("what"))}</span></li>"""
    }
    """<div class="timeline"><h3>${pr(t["number"])} with ${esc(t.text("by"))}</h3><ol>$events</ol></div>"""
}

val roundSections = rounds.joinToString("\n") { r ->
    val rows = r["pulls"].list().joinToString("\n") { p ->
        val tone = p.text("tone")
        val who = if (p.text("by") == "rival") "GitHub's auto-merge" else "the watch"
        """<li class="pull $tone"><div class="head">${pr(p["number"])} ${tag(tone, outcomeWords[tone] ?: tone)}""" +
            """<span class="sub">${esc(who)}</span></div><div>${esc(p.text("what"))}</div>""" +
            """<div class="outcome">${esc(p.text("outcome"))}</div></li>"""
    }
    """<section><h2>${esc(r.text("name"))}</h2><p class="sub">${esc(r.text("setting"))} """ +
        """Output: <span class="mono">${esc(r.text("out"))}</span>.</p><ol class="pulls">$rows</ol></section>"""
}

val words = yaml["words"].list().joinToString("\n") { w ->
    """<li><b>${esc(w.text("word"))}</b><code>${esc(w.text("code"))}</code></li>"""
}
val fixed = yaml["fixed"].list().joinToString("\n") { f ->
    """<li><div>${esc(f.text("what"))}</div><div class="outcome">${esc(f.text("fix"))}</div>""" +
        """<div class="sub mono">${esc(f.text("out"))}</div></li>"""
}
val goalText = File(yaml.text("goalFile")).readText()
val became = yaml["became"].list().joinToString("\n") { b ->
    """<li><b>${esc(b.text("part"))}</b><div class="outcome">${esc(b.text("as"))}</div></li>"""
}
/** Each live run of a goal file, as a row: what it found, and what was fixed. */
fun runs(key: String) = yaml[key].list().joinToString("\n") { r ->
    val tone = r.text("tone")
    val fixed = r.text("fixed").takeIf { it != "-" }?.let { """<div class="sub">Fixed: ${esc(it)}</div>""" }.orEmpty()
    """<li class="pull $tone"><div class="head"><b class="mono">${esc(r.text("run"))}</b> ${tag(tone, runWords[tone] ?: tone)}</div>""" +
        """<div>${esc(r.text("found"))}</div>$fixed</li>"""
}
val goalRuns = runs("goalRuns")
val goalRunCount = yaml["goalRuns"].list().size
val scanText = File(yaml.text("scanGoalFile")).readText()
val scanRuns = runs("scanRuns")

val gaps = yaml["gaps"].list().joinToString("\n") { g ->
    """<li>${tag("quiet", g.text("where"))} ${esc(g.text("what"))}</li>"""
}

fun shown(name: String, path: String) = """<details class="box"><summary>$name</summary>""" +
    """<div class="scroll"><pre class="src">${esc(File(path).readText())}</pre></div></details>"""

val style = """
section, .tiles { margin-top: 28px; }
.tile { cursor: default; }
.headline { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; }
.timeline { background: var(--panel); border: 1px solid var(--rule); border-radius: 6px; padding: 14px 16px;
  min-width: 0; }
.timeline h3 { margin: 0 0 10px; }
.timeline ol { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.timeline li { display: grid; grid-template-columns: 70px 1fr; gap: 10px; padding-left: 10px;
  border-left: 3px solid var(--c, var(--rule)); }
.timeline li.quiet { --c: var(--rule); }
.timeline .at { font: 12.5px var(--mono); color: var(--muted); }
ol.pulls, ol.plain { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.pull, ol.plain > li { background: var(--panel); border: 1px solid var(--rule);
  border-left: 4px solid var(--c, var(--rule));
  border-radius: 6px; padding: 12px 14px; display: grid; gap: 4px; min-width: 0; }
.pull .head { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
.outcome { color: var(--c, var(--ink)); }
ol.plain > li .outcome { color: var(--good); }
ol.words { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
ol.words li { display: grid; gap: 4px; background: var(--panel); border: 1px solid var(--rule); border-radius: 6px;
  padding: 12px 14px; min-width: 0; }
ol.words code { font: 12.5px/1.5 var(--mono); color: var(--muted); overflow-wrap: anywhere; }
ul.gaps { margin: 0; padding-left: 18px; display: grid; gap: 8px; }
"""

val body = """
  <div class="tiles">$tiles</div>
  <section>
    <h2>A person says no</h2>
    <p>Two bumps, each labelled <span class="mono">hold</span> by a person after it was taken up, with the required
    check passing on both. The watch reads the pull request again at each look, and acts only on what it holds now.
    GitHub's auto-merge decided once, when it was turned on.</p>
    <div class="headline">$timelines</div>
    <p class="sub">Times are GitHub's own (UTC), from each pull request's timeline and check runs.</p>
  </section>
$roundSections
  <section>
    <h2>The goal file, run by ARTIE itself</h2>
    <p>Then ARTIE learned the words: <span class="mono">artie run dependabot-sim.goal.md --reasoner claude</span>
    runs the goal file below as written. Claude decides each part once, the first time the run needs it, on a real
    pull request; every later pull request and every later look replays the decision, with no model.</p>
    <details class="box" open><summary>dependabot-sim.goal.md</summary><div class="scroll"><pre class="src">${esc(goalText)}</pre></div></details>
    <h3>What each part became, in the last run</h3>
    <ol class="words">$became</ol>
    <h3>$goalRunCount live runs, each finding something, each fixed with a test first</h3>
    <ol class="pulls">$goalRuns</ol>
  </section>
  <section>
    <h2>Two Whenevers at once: Dependabot, and a security scan</h2>
    <p>The same watch, with a second Whenever beside it: each issue a security scan opens, labelled
    <span class="mono">scan</span>, is answered once. Whenevers side by side are one watch, each a listener on a
    thread of its own, with a client of its own, so a slow read under one holds up only that one. Each one's
    first look is made in turn, as the goal lists them, so Claude decides in the goal's order; after that, every
    look replays.</p>
    <details class="box"><summary>dependabot-scan.goal.md</summary><div class="scroll"><pre class="src">${esc(scanText)}</pre></div></details>
    <h3>Seven live runs, to every give-up saying why in the gateway's own words</h3>
    <ol class="pulls">$scanRuns</ol>
  </section>
  <section>
    <h2>The goal words, as the code they came down to</h2>
    <p>Before ARTIE read the words, <span class="mono">watch.kts</span> wrote each by hand in heart-script,
    through the HEART gateway; heart-script's <span class="mono">watch</span>, <span class="mono">whenever</span>
    and <span class="mono">once</span> now keep them, and a goal run decides them.</p>
    <ol class="words">$words</ol>
  </section>
  <section>
    <h2>Found and fixed on the way</h2>
    <ol class="plain">$fixed</ol>
  </section>
  <section>
    <h2>Still missing</h2>
    <ul class="gaps">$gaps</ul>
  </section>
  <section>
    <h2>How this page is made</h2>
    ${shown("results.yaml: what happened", data)}
    ${shown("watch.kts: the watch", watcher)}
    ${shown("report.kts: the script that lays it out", source)}
  </section>
"""

/** The page: the reports' shared shell, filled with this page's own parts. */
val page = File(assets, "report-shell.html").readText()
    .replace("@@TITLE@@", "Dependabot, Simulated")
    .replace("@@HEADING@@", "Dependabot, simulated: a watch that can be told no")
    .replace("@@LEDE@@", "Dependabot-style pull requests opened with our own account on a scratch repository, merged " +
        "by a watch through the HEART gateway, beside GitHub's own auto-merge; then by the goal file itself, run by " +
        "ARTIE, each step decided once by Claude. Run ${yaml.text("asOf")}.")
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
