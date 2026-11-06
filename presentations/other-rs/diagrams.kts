// The diagrams of "The Other R's", drawn as SVG, one file per diagram into the folder given; page.kts inlines them.
// The Svg helper is the one "The Browser for Agents" uses.
//   artie run diagrams.kts out=diagrams
import java.io.File

val out by input(default = "diagrams", question = "Which folder gets the SVG files?")


fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

val STYLE = """
.box{fill:var(--d-panel,#FFFFFF);stroke:var(--d-line,#C9C2B4);stroke-width:1.5}
.hl{fill:var(--d-accent-soft,#F6E3C2);stroke:var(--d-accent,#B86E12);stroke-width:2}
.ghost{fill:none;stroke:var(--d-line,#C9C2B4);stroke-width:1.5;stroke-dasharray:6 5}
.t{font:500 16px var(--d-font,system-ui,sans-serif);fill:var(--d-ink,#1A1F27)}
.h{font:600 18px var(--d-font,system-ui,sans-serif);fill:var(--d-ink,#1A1F27)}
.s{font:400 14px var(--d-font,system-ui,sans-serif);fill:var(--d-muted,#5A6370)}
.m{font:400 13px var(--d-mono,ui-monospace,monospace);fill:var(--d-ink,#1A1F27);white-space:pre}
.a{fill:var(--d-accent,#B86E12)}
.line{stroke:var(--d-muted,#5A6370);stroke-width:1.75;fill:none}
.dash{stroke:var(--d-muted,#5A6370);stroke-width:1.5;fill:none;stroke-dasharray:5 5}
.accline{stroke:var(--d-accent,#B86E12);stroke-width:2;fill:none}
.head{fill:var(--d-muted,#5A6370)}
""".trimIndent()

/** One diagram: shapes in drawing order, an accessible title and description, and arrowheads named after it. */
class Svg(val name: String, val w: Int, val h: Int) {
    private val parts = StringBuilder()
    fun add(s: String) { parts.append(s).append('\n') }

    fun box(x: Int, y: Int, w: Int, h: Int, cls: String = "box", rx: Int = 10) =
        add("""<rect class="$cls" x="$x" y="$y" width="$w" height="$h" rx="$rx"/>""")

    /** Lines of text, the first at [y], each [step] below the one before; the first may take its own class. */
    fun text(x: Int, y: Int, vararg lines: String, cls: String = "t", first: String = cls, anchor: String = "middle", step: Int = 20) =
        lines.forEachIndexed { i, s ->
            add("""<text class="${if (i == 0) first else cls}" x="$x" y="${y + i * step}" text-anchor="$anchor">${esc(s)}</text>""")
        }

    /** A box with its words centred in it: a heading line, then smaller lines. */
    fun card(x: Int, y: Int, w: Int, h: Int, title: String, vararg sub: String, cls: String = "box") {
        box(x, y, w, h, cls)
        val lines = 1 + sub.size
        val top = y + h / 2 - (lines - 1) * 10 + 5
        text(x + w / 2, top, title, *sub, cls = "s", first = "h")
    }

    fun arrow(x1: Int, y1: Int, x2: Int, y2: Int, cls: String = "line") =
        add("""<path class="$cls" d="M$x1 $y1 L$x2 $y2" marker-end="url(#$name-head)"/>""")

    fun path(d: String, cls: String = "line", head: Boolean = true) =
        add("""<path class="$cls" d="$d"${if (head) """ marker-end="url(#$name-head)"""" else ""}/>""")

    fun line(x1: Int, y1: Int, x2: Int, y2: Int, cls: String = "dash") =
        add("""<path class="$cls" d="M$x1 $y1 L$x2 $y2"/>""")

    fun dot(x: Int, y: Int, r: Int = 7, cls: String = "a") = add("""<circle class="$cls" cx="$x" cy="$y" r="$r"/>""")

    fun render(title: String, desc: String) = """
        |<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" role="img" aria-labelledby="$name-title $name-desc">
        |<title id="$name-title">${esc(title)}</title>
        |<desc id="$name-desc">${esc(desc)}</desc>
        |<style>$STYLE</style>
        |<defs><marker id="$name-head" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="head" d="M0 0 L10 5 L0 10 z"/></marker></defs>
        |$parts</svg>
        |""".trimMargin()
}

fun twice() = Svg("twice", 960, 400).apply {
    text(240, 30, "Ask the model every time", cls = "h")
    text(720, 30, "Decide once, replay", cls = "h")
    line(480, 20, 480, 380)
    card(30, 150, 140, 64, "Same input", "same pull request")
    listOf("merge, squash", "comment first", "merge, rebase").forEachIndexed { i, s ->
        val y = 60 + i * 100
        card(290, y, 160, 56, "Call ${i + 1}", s, cls = if (i == 1) "hl" else "box")
        arrow(170, 182, 288, y + 28)
    }
    text(240, 370, "Each call may answer differently (an illustration)", cls = "s")
    card(500, 60, 180, 60, "First time", "the model decides", cls = "hl")
    arrow(680, 90, 728, 90)
    card(730, 60, 200, 60, "Kept", "github:merge, squash")
    listOf("Pull request 2", "Pull request 3", "Look 151").forEachIndexed { i, s ->
        val y = 170 + i * 66
        card(730, y, 200, 50, s, "same decision")
        path("M830 ${y - 16} L830 $y", head = true)
    }
    text(720, 390, "The server re-checks each replay against what it offers now", cls = "s")
}.render(
    "Ask every time, or decide once and replay",
    "Asked again for the same pull request, a model may answer differently each call. Decided once and kept, the " +
        "same decision is replayed for every later pull request and look, and the server checks it each time.",
)

fun share() = Svg("share", 960, 300).apply {
    data class Bar(val label: String, val steps: Int, val calls: Int)
    listOf(
        Bar("Goal run 1, 13 minutes", 444, 13),
        Bar("Goal run 2, 10 minutes", 538, 12),
        Bar("Script, one hour", 2740, 5),
    ).forEachIndexed { i, b ->
        val y = 50 + i * 80
        text(20, y + 26, b.label, cls = "t", anchor = "start")
        val w = (b.steps / 2740.0 * 470).toInt().coerceAtLeast(40)
        box(250, y, w, 40, "box", rx = 6)
        val c = (b.calls.toDouble() / b.steps * w).toInt().coerceAtLeast(3)
        add("""<rect class="a" x="250" y="$y" width="$c" height="40" rx="3"/>""")
        text(260 + w, y + 26, "${b.steps} steps · ${b.calls} model calls", cls = "s", anchor = "start")
    }
    text(480, 290, "Orange: steps that asked the model. The rest ran on kept decisions and the server's controls.", cls = "s")
}.render(
    "How many steps asked the model",
    "Goal run 1: 444 steps, 13 model calls. Goal run 2: 538 steps, 12. The generated script over an hour: 2,740 " +
        "steps, 5 model calls, all at the line marked reasoned.",
)

fun carriers() = Svg("carriers", 960, 520).apply {
    data class Col(val who: String, val sub: String, val rs: List<String>, val hl: Boolean = false)
    listOf(
        Col("The server", "controls in the answer", listOf("Right behavior", "Refusal", "Recency", "Resilience")),
        Col("The script", "kept decisions", listOf("Repeatability", "Reliability", "Reproducibility", "Recoverability", "Restraint", "Respect", "Rate")),
        Col("The people", "goal and review", listOf("Readability", "Reviewability", "Responsibility", "Revocability")),
        Col("The model", "where judgment lives", listOf("Reasoning"), hl = true),
    ).forEachIndexed { i, c ->
        val x = 20 + i * 235
        card(x, 30, 215, 70, c.who, c.sub, cls = if (c.hl) "hl" else "box")
        c.rs.forEachIndexed { j, r ->
            val y = 120 + j * 52
            box(x + 15, y, 185, 40, if (c.hl) "hl" else "box")
            text(x + 107, y + 26, r, cls = "t")
        }
    }
    text(480, 505, "Reasoning is one R of many, and the only one that costs a model call", cls = "s")
}.render(
    "Who carries which R",
    "The server carries right behavior, refusal, recency and resilience; the script carries repeatability, " +
        "reliability, reproducibility, recoverability, restraint, respect and rate; people carry readability, reviewability, " +
        "responsibility and revocability. The model carries reasoning, and only where judgment is needed.",
)

val diagrams = mapOf("twice" to ::twice, "share" to ::share, "carriers" to ::carriers)
val folder = File(out).apply { mkdirs() }
diagrams.forEach { (name, draw) -> File(folder, "$name.svg").writeText(draw()) }
println("wrote ${diagrams.size} diagrams to ${folder.path}")
