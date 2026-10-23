// The care dial as a page: every arm of the ladder from most careful to least, with what it cost on a calm GitHub
// (runs/ladder.tsv) and what it did when a teammate moved the world under it (runs/meddle.tsv, meddler.py).
//   artie run care-dial.kts runs=runs out=../../docs/reports/care-dial/index.html artifact=/tmp/care-dial.html
// out gets a whole document for GitHub Pages; artifact, when given, the same page without the document skeleton.
import java.io.File
import java.time.LocalDate

val runs by input(default = "runs", question = "Where are ladder.tsv and meddle.tsv?")
val out by input(question = "Where to write the page?")
val artifact by input(default = "", question = "Where to write the artifact copy, if anywhere?")

/** One arm of the ladder, in the dial's order: its name in the tables, what it is, and how it reaches GitHub. */
class Arm(val name: String, val label: String, val what: String, val kind: String, val note: String = "")

val arms = listOf(
    Arm("artie-goal", "artie-goal", "ARTIE with the goal script: acts only on what the resource still offers", "heart"),
    Arm("careful-heart", "careful-heart", "the generated script, guard strict, validators from the last answer, " +
        "the gateway taking every care", "heart"),
    Arm("heart_every", "heart:every", "the generated script off the rails, the gateway taking every care", "heart"),
    Arm("heart_no-reread", "heart:no-reread", "as heart:every, the gateway not reading again before a write", "heart"),
    Arm("heart_no-race", "heart:no-race", "as heart:every, the gateway not checking for a race", "heart"),
    Arm("heart_no-offers", "heart:no-offers", "as heart:every, the gateway not working out what is offered", "heart"),
    Arm("heart_trust-copies_60", "heart:trust-copies=60", "as heart:every, the gateway trusting its copies " +
        "for 60 s", "heart"),
    Arm("heart_off-the-rails", "heart:off-the-rails", "every care off, client and gateway, still HEART", "heart"),
    Arm("careful-via", "careful-via", "Claude's careful Python, through the gateway passed through", "python"),
    Arm("careful-direct", "careful-direct", "Claude's careful Python, straight to GitHub", "python"),
    Arm("py-via", "py-via", "Claude's plain Python, through the gateway passed through", "python"),
    Arm("artie-bare", "artie-bare", "the careful run brought down to its GitHub calls by artie generate --bare, " +
        "sent by ARTIE with no checks", "bare",
        "It never writes the crash issue's state itself, so M1 had nothing to overwrite. Under M4 it stopped only " +
            "because the merge, retried 30 times a second apart, kept getting 405 \"still a draft\"."),
    Arm("py-direct", "py-direct", "Claude's plain Python, straight to GitHub", "python"),
)

/** The moves meddler.py makes, as it says them. */
val moves = mapOf(
    "M1" to "closes the crash issue as not planned",
    "M2" to "locks the crash issue's conversation as off-topic",
    "M3" to "closes the duplicate as completed: a different crash",
    "M4" to "converts the pull request to a draft",
    "M5" to "adds a reviewer's note to the pull request",
)

/** How bad a verdict is, from best to worst, and the class that colours it. */
val verdicts = listOf(
    "succeeded" to "good",
    "stopped safely" to "safe",
    "exit 0, task left undone" to "warn",
    "damaged, then failed" to "warn",
    "off the rails: exit 0 over damage" to "bad",
)

class Calm(val arm: String, val stamp: String, val rc: Int, val seconds: Double, val held: Int, val of: Int)
class Meddled(
    val arm: String, val stamp: String, val moves: String, val rc: Int, val seconds: Double,
    val verdict: String, val damage: List<String>, val undone: List<String>,
)

fun rows(name: String) = File(runs, name).readLines().filter { it.isNotBlank() }.map { it.split('\t') }

val calm = rows("ladder.tsv").map { c ->
    val (held, of) = c[4].split('/').let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    Calm(c[0], c[1], c[2].toInt(), c[3].toDouble(), held, of)
}
val meddled = rows("meddle.tsv").map { c ->
    val said = c.getOrElse(8) { "-" }.split("; ").filter { it != "-" && it.isNotBlank() }
    Meddled(c[0], c[1], c[2], c[3].toInt(), c[4].toDouble(), c[5],
        said.filterNot { it.startsWith("undone: ") }, said.filter { it.startsWith("undone: ") }.map { it.removePrefix("undone: ") })
}

fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun median(xs: List<Double>) = xs.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
fun s(x: Double) = "%.1f".format(x)
fun tone(verdict: String) = verdicts.firstOrNull { it.first == verdict }?.second ?: "warn"
fun worst(list: List<Meddled>) = list.maxByOrNull { m -> verdicts.indexOfFirst { it.first == m.verdict } }

/** A cell of verdict counts, worst first: "4 × exit 0 over damage". */
fun verdictCell(list: List<Meddled>): String {
    if (list.isEmpty()) return """<span class="none">not run</span>"""
    return verdicts.reversed().mapNotNull { (v, t) ->
        list.count { it.verdict == v }.takeIf { it > 0 }?.let { """<span class="v $t">$it × ${esc(v)}</span>""" }
    }.joinToString(" ")
}

/** The distinct harms across a set of runs, each with how many runs did it. */
fun harmCell(list: List<Meddled>): String {
    val harms = list.flatMap { it.damage }.groupingBy { it }.eachCount()
    if (list.isEmpty()) return ""
    if (harms.isEmpty()) return """<span class="none">none</span>"""
    return harms.entries.joinToString("") { (h, n) -> """<div class="harm">${esc(h)} <span class="n">$n/${list.size}</span></div>""" }
}

val storm = "M1,M2,M3,M5"
val maxCalm = calm.filter { a -> arms.any { it.name == a.arm } }.maxOf { it.seconds }.coerceAtMost(100.0)
val maxStorm = meddled.maxOf { it.seconds }

fun strip(xs: List<Double>, max: Double, cls: String) =
    """<div class="strip $cls">""" + xs.joinToString("") { x ->
        val left = (x.coerceAtMost(max) / max * 100)
        """<i style="left:${"%.1f".format(left)}%" title="${s(x)} s"></i>"""
    } + "</div>"

val dialRows = arms.joinToString("\n") { a ->
    val c = calm.filter { it.arm == a.name }
    val st = meddled.filter { it.arm == a.name && it.moves == storm }
    val m4 = meddled.filter { it.arm == a.name && it.moves == "M4" }
    val calmCell = if (c.isEmpty()) """<span class="none">in the storm only</span>""" else
        """<b>${s(median(c.map { it.seconds }))} s</b> <span class="sub">${s(c.minOf { it.seconds })}–${s(c.maxOf { it.seconds })}, ${c.size} runs, """ +
            """${c.count { it.held == it.of && it.of > 0 }} whole</span>""" + strip(c.map { it.seconds }, maxCalm, a.kind)
    val stormTime = if (st.isEmpty()) "" else """<div class="sub">${s(median(st.map { it.seconds }))} s median</div>"""
    """<tr class="${a.kind}"><th scope="row"><code>${esc(a.label)}</code><div class="what">${esc(a.what)}</div></th>""" +
        """<td>$calmCell</td><td>${verdictCell(st)}$stormTime</td><td>${harmCell(st)}${if (a.note.isEmpty()) "" else """<div class="note">${esc(a.note)}</div>"""}</td>""" +
        """<td>${verdictCell(m4)}</td></tr>"""
}

val allRuns = meddled.sortedBy { it.stamp }.joinToString("\n") { m ->
    val label = arms.firstOrNull { it.name == m.arm }?.label ?: m.arm
    """<tr><td class="mono">${esc(m.stamp)}</td><td><code>${esc(label)}</code></td><td class="mono">${esc(m.moves)}</td>""" +
        """<td class="num">${m.rc}</td><td class="num">${s(m.seconds)}</td><td><span class="v ${tone(m.verdict)}">${esc(m.verdict)}</span></td>""" +
        """<td>${(m.damage.map { esc(it) } + m.undone.map { "left undone: " + esc(it) }).joinToString("<br>").ifEmpty { "–" }}</td></tr>"""
}

val calmRuns = calm.filter { a -> arms.any { it.name == a.arm } }.sortedBy { it.stamp }.joinToString("\n") { c ->
    val label = arms.first { it.name == c.arm }.label
    """<tr><td class="mono">${esc(c.stamp)}</td><td><code>${esc(label)}</code></td><td class="num">${c.rc}</td>""" +
        """<td class="num">${s(c.seconds)}</td><td class="num">${c.held}/${c.of}</td></tr>"""
}

val bare = meddled.filter { it.arm == "artie-bare" && it.moves == storm }
val py = meddled.filter { it.arm == "py-direct" && it.moves == storm }
val goal = meddled.filter { it.arm == "artie-goal" && it.moves == storm }
val bareCalm = calm.filter { it.arm == "artie-bare" }.map { it.seconds }
val goalStorm = goal.map { it.seconds }

fun tile(big: String, label: String, sub: String, cls: String) =
    """<div class="tile $cls"><span class="name">$label</span><b>$big</b><span class="sub">$sub</span></div>"""

val tiles = listOf(
    tile(if (bareCalm.isEmpty()) "–" else "${s(median(bareCalm))} s", "artie-bare, calm", "median of ${bareCalm.size} runs, no checks", "bare"),
    tile(worst(bare)?.verdict?.let { esc(it) } ?: "not run", "artie-bare, storm",
        "${bare.count { it.damage.isNotEmpty() }} of ${bare.size} runs did harm", worst(bare)?.verdict?.let { tone(it) } ?: ""),
    tile(worst(py)?.verdict?.let { esc(it) } ?: "not run", "py-direct, storm",
        "${py.count { it.damage.isNotEmpty() }} of ${py.size} runs did harm", worst(py)?.verdict?.let { tone(it) } ?: ""),
    tile(if (goalStorm.isEmpty()) "–" else "${s(median(goalStorm))} s", "artie-goal, storm",
        "${goal.count { it.verdict == "succeeded" }} of ${goal.size} succeeded, no harm", "good"),
).joinToString("")

val moveList = moves.entries.joinToString("") { (k, v) -> "<li><b>$k</b> ${esc(v)}</li>" }

val page = """<title>Care Dial</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Condensed:wght@500;600&family=IBM+Plex+Sans:wght@400;500&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
/* Layout: one column; the dial as one table, most careful at the top; every run below behind disclosures. */
:root {
  --bg: #f6f7f9; --panel: #ffffff; --ink: #1b2330; --muted: #5d6879; --rule: #dde2ea; --track: #eef1f5;
  --heart: #4b4fc4; --python: #b8650b; --bare: #0f7d76;
  --good: #1f7a3a; --good-bg: #e3f3e7; --safe: #2b5fa8; --safe-bg: #e3ecf8;
  --warn: #8a5a00; --warn-bg: #fbf0d9; --bad: #b3261e; --bad-bg: #fbe4e2;
  --display: "IBM Plex Sans Condensed", "Arial Narrow", system-ui, sans-serif;
  --body: "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, Menlo, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #12161d; --panel: #1a2029; --ink: #e5e9f0; --muted: #9aa5b6; --rule: #2c3442; --track: #232a35;
  --heart: #8f93f2; --python: #e4953b; --bare: #3cc1b6;
  --good: #6fd08c; --good-bg: #183223; --safe: #8fb6f0; --safe-bg: #1a2a42;
  --warn: #f0c060; --warn-bg: #3a2e12; --bad: #ff8a80; --bad-bg: #42201d; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #12161d; --panel: #1a2029; --ink: #e5e9f0; --muted: #9aa5b6; --rule: #2c3442; --track: #232a35;
  --heart: #8f93f2; --python: #e4953b; --bare: #3cc1b6;
  --good: #6fd08c; --good-bg: #183223; --safe: #8fb6f0; --safe-bg: #1a2a42;
  --warn: #f0c060; --warn-bg: #3a2e12; --bad: #ff8a80; --bad-bg: #42201d; color-scheme: dark }
body { background: var(--bg); color: var(--ink); font: 15px/1.55 var(--body); }
.wrap { max-width: 1120px; margin: 0 auto; padding-block: 28px 56px;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  display: grid; grid-template-columns: minmax(0, 1fr); gap: 26px; }
h1, h2 { font-family: var(--display); font-weight: 600; text-wrap: balance; margin: 0; }
h1 { font-size: 30px; line-height: 1.15; } h2 { font-size: 20px; }
.lede { color: var(--muted); max-width: 70ch; margin: 6px 0 0; }
section { display: grid; gap: 12px; min-width: 0; }
.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; }
.tile { background: var(--panel); border: 1px solid var(--rule); border-top: 3px solid var(--c, var(--rule));
  border-radius: 6px; padding: 12px 16px; display: grid; gap: 2px; }
.tile .name { font: 500 13px var(--mono); color: var(--muted); }
.tile b { font: 600 22px/1.2 var(--display); color: var(--c, var(--ink)); }
.tile.good { --c: var(--good); } .tile.safe { --c: var(--safe); } .tile.warn { --c: var(--warn); }
.tile.bad { --c: var(--bad); } .tile.bare { --c: var(--bare); }
.sub, .none { color: var(--muted); font-size: 13px; }
.scroll { overflow-x: auto; background: var(--panel); border: 1px solid var(--rule); border-radius: 6px; }
table { border-collapse: collapse; width: 100%; font-size: 14px; }
th, td { text-align: left; vertical-align: top; padding: 10px 12px; border-bottom: 1px solid var(--rule); }
thead th { font: 500 12px var(--mono); letter-spacing: .06em; text-transform: uppercase; color: var(--muted); white-space: nowrap; }
tbody tr:last-child > * { border-bottom: 0; }
tbody th { font-weight: 400; min-width: 220px; border-left: 4px solid var(--k); }
tr.heart { --k: var(--heart); } tr.python { --k: var(--python); } tr.bare { --k: var(--bare); }
tbody th code { font: 500 14px var(--mono); color: var(--k); }
.what { color: var(--muted); font-size: 13px; margin-top: 2px; max-width: 34ch; }
.dial td:nth-child(2) { min-width: 190px; }
.dial td:nth-child(3), .dial td:nth-child(5) { min-width: 170px; }
.dial td:nth-child(4) { min-width: 240px; }
.v { display: inline-block; font-size: 13px; padding: 1px 8px; border-radius: 10px; margin: 0 4px 4px 0; white-space: nowrap; }
.v.good { color: var(--good); background: var(--good-bg); } .v.safe { color: var(--safe); background: var(--safe-bg); }
.v.warn { color: var(--warn); background: var(--warn-bg); } .v.bad { color: var(--bad); background: var(--bad-bg); }
.harm { font-size: 13px; } .note { font-size: 12px; color: var(--muted); margin-top: 6px; max-width: 36ch; } .harm .n { font: 12px var(--mono); color: var(--muted); }
.strip { position: relative; height: 10px; margin-top: 6px; background: var(--track); border-radius: 5px; }
.strip i { position: absolute; top: 1px; width: 8px; height: 8px; margin-left: -4px; border-radius: 50%; background: var(--k); opacity: .8; }
.mono, .num { font-family: var(--mono); font-size: 13px; } .num { text-align: right; font-variant-numeric: tabular-nums; }
ul.moves { margin: 0; padding-left: 20px; display: grid; gap: 2px; } ul.moves b { font-family: var(--mono); }
details { background: var(--panel); border: 1px solid var(--rule); border-radius: 6px; }
details > summary { cursor: pointer; padding: 10px 14px; font-weight: 500; }
details > .scroll { border: 0; border-top: 1px solid var(--rule); border-radius: 0; }
.key { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px; color: var(--muted); }
.key span::before { content: ""; display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; background: var(--k); }
</style>

<div class="wrap">
  <header>
    <h1>The care dial: what each safeguard costs, and what it saves</h1>
    <p class="lede">The same GitHub triage (3 issues, a branch, 2 commits, a pull request merged, 23 checks) by every
    arm of the ladder, most careful at the top. Calm: GitHub left alone. Storm: meddler.py moves the world under the run
    as a teammate would, and checker.py judges what the run kept of the teammate's moves. From runs/ladder.tsv and
    runs/meddle.tsv, ${LocalDate.now()}.</p>
  </header>
  <div class="tiles">$tiles</div>
  <section>
    <h2>The dial</h2>
    <div class="key"><span style="--k:var(--heart)">HEART, through the gateway</span><span style="--k:var(--bare)">bare ARTIE, straight to GitHub</span><span style="--k:var(--python)">Claude's Python</span></div>
    <div class="scroll"><table class="dial">
      <thead><tr><th>Arm</th><th>Calm: time, whole runs</th><th>Storm ($storm)</th><th>Harm done in the storm</th><th>M4 alone</th></tr></thead>
      <tbody>
$dialRows
      </tbody>
    </table></div>
    <p class="sub">"Whole" counts the calm runs where all of checker.py's checks held. The early part-runs (3/7, 9/22) are
    GitHub's merge lag from before the scripts waited for it. A dot is one run, drawn to one scale up to ${s(maxCalm)} s.</p>
  </section>
  <section>
    <h2>The teammate's moves</h2>
    <ul class="moves">$moveList</ul>
    <p class="sub">Each move is made on the state the teammate reads first, once the run's pull request is open. A move the
    run had already overtaken is "late" and says nothing about the run. Verdicts: <span class="v good">succeeded</span>
    finished and kept every move; <span class="v safe">stopped safely</span> stopped before overwriting any;
    <span class="v warn">damaged, then failed</span> overwrote one, then stopped; <span class="v bad">off the rails: exit 0 over damage</span>
    overwrote and reported success.</p>
  </section>
  <section>
    <h2>Every run</h2>
    <details><summary>Storm and M4 runs (${meddled.size})</summary><div class="scroll"><table>
      <thead><tr><th>Stamp</th><th>Arm</th><th>Moves</th><th class="num">Exit</th><th class="num">Seconds</th><th>Verdict</th><th>Harm and left undone</th></tr></thead>
      <tbody>
$allRuns
      </tbody></table></div></details>
    <details><summary>Calm runs (${calm.count { a -> arms.any { it.name == a.arm } }})</summary><div class="scroll"><table>
      <thead><tr><th>Stamp</th><th>Arm</th><th class="num">Exit</th><th class="num">Seconds</th><th class="num">Checks</th></tr></thead>
      <tbody>
$calmRuns
      </tbody></table></div></details>
  </section>
</div>
"""

File(out).apply { parentFile.mkdirs() }.writeText(
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"></head>" +
        "<body style=\"margin:0\">\n" + page + "</body></html>\n"
)
if (artifact.isNotBlank()) File(artifact).writeText(page)
println("wrote $out" + if (artifact.isNotBlank()) " and $artifact" else "")
