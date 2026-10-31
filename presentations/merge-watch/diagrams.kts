// The diagrams of "One Goal, Decided Once", drawn as SVG, one file per diagram into the folder given; page.kts
// inlines them into the page. The Svg helper is the one "The Browser for Agents" uses.
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

fun lifecycle() = Svg("lifecycle", 960, 400).apply {
    card(20, 170, 150, 64, "A bump opens", "labelled dependencies", cls = "hl")
    arrow(170, 202, 208, 202)
    card(210, 170, 160, 64, "Labelled hold?")
    arrow(290, 170, 290, 116)
    text(304, 148, "yes", cls = "s", anchor = "start")
    card(210, 50, 160, 64, "Leave it", "say so once")
    arrow(370, 202, 408, 202)
    text(389, 194, "no", cls = "s")
    card(410, 170, 170, 64, "Merge offered?", "wait up to 3 minutes")
    arrow(495, 170, 495, 116)
    text(509, 148, "yes", cls = "s", anchor = "start")
    card(410, 50, 170, 64, "Merge it", "by squashing")
    arrow(580, 202, 618, 202)
    text(599, 194, "no", cls = "s")
    card(620, 170, 150, 64, "Give up", "the server says why")
    arrow(770, 202, 798, 202)
    card(800, 170, 140, 64, "The model", "one sentence", cls = "hl")
    arrow(870, 234, 870, 278)
    card(780, 280, 160, 64, "Comment", "to its author")
    text(400, 320, "All of it again whenever a pull request changes,", cls = "s")
    text(400, 342, "until someone closes the watch's issue.", cls = "s")
}.render(
    "What the person wants done with each dependency bump",
    "A bump labelled hold is left, said once. Otherwise the watch waits up to three minutes for merge to be " +
        "offered and merges it; if it never is, the server's reason goes to the model, which writes one sentence " +
        "to the author.",
)

fun history() = Svg("history", 960, 470).apply {
    data class Mark(val year: String, val a: String, val b: String, val key: Boolean = false)
    val marks = listOf(
        Mark("1945", "Bush: memex", "trails of documents"),
        Mark("1965", "Nelson coins", "“hypertext”"),
        Mark("1968", "Engelbart: NLS", "links, one system"),
        Mark("1987", "HyperCard", "stacks, one reader"),
        Mark("1989", "Berners-Lee", "proposal at CERN"),
        Mark("1991", "The Web, public", "URI, HTTP, HTML", key = true),
        Mark("1993", "Mosaic, forms", "links and forms"),
        Mark("2000", "Fielding: REST", "dissertation", key = true),
        Mark("2008", "“must be", "hypertext-driven”"),
        Mark("2012", "HAL draft", "links in JSON"),
        Mark("2017", "OpenAPI 3.0", "rules in a spec"),
        Mark("2023", "LLM agents", "rules in training"),
        Mark("2026", "HEART + ARTIE", "controls in answers", key = true),
    )
    val y = 235
    line(40, y, 920, y, cls = "line")
    add("""<rect class="ghost" x="80" y="${y - 14}" width="232" height="28" rx="14"/>""")
    marks.forEachIndexed { i, m ->
        val x = 100 + i * 64
        dot(x, y, r = if (m.key) 9 else 6, cls = if (m.key) "a" else "head")
        val up = i % 2 == 0
        line(x, if (up) y - 12 else y + 12, x, if (up) y - 50 else y + 50)
        val top = if (up) y - 110 else y + 76
        text(x, top, m.year, m.a, m.b, cls = "s", first = "h", step = 19)
    }
    text(196, 40, "Islands: each system its own reader", cls = "s")
    text(480, 445, "The Web splits client from content; then APIs move the rules out of the answer again", cls = "s")
}.render(
    "Hypertext, 1945 to now",
    "Linked information is old: Bush's memex, Nelson's word, Engelbart's NLS and HyperCard each came with their " +
        "own reader. The Web split the generic client from the content in 1991, Fielding named its architecture in " +
        "2000, APIs moved the rules into descriptions and training, and HEART puts the controls back in the answer.",
)

fun goalmap() = Svg("goalmap", 960, 470).apply {
    text(250, 30, "The goal says", cls = "h")
    text(730, 30, "The run does", cls = "h")
    data class Row(val said: String, val does: String, val sub: String, val hl: Boolean = false)
    listOf(
        Row("Whenever a pull request labelled {label} changes", "Visit each one new or changed", "since the last look"),
        Row("If it's labelled {hold}, leave it, and say so once.", "A check: where it holds,", "leave the pull request, noted once"),
        Row("Once it can be merged, merge it by squashing.", "Wait until the server offers merge,", "then act on it"),
        Row("Or give up after 3 minutes.", "End that wait", "after 3 minutes"),
        Row("Otherwise, tell its author … `reasoned`", "Ask the model, each give-up,", "with the server's reason", hl = true),
        Row("Until someone closes {watch}", "Read the watch's issue each look;", "stop when it is closed"),
    ).forEachIndexed { i, r ->
        val y = 52 + i * 68
        box(20, y, 470, 54, if (r.hl) "hl" else "box")
        add("""<text class="m" x="36" y="${y + 32}">${esc(r.said)}</text>""")
        arrow(492, y + 27, 538, y + 27)
        box(540, y, 400, 54, if (r.hl) "hl" else "box")
        text(740, y + 23, r.does, r.sub, cls = "s", first = "t", step = 19)
    }
}.render(
    "Each line of the goal, and what the run does with it",
    "Whenever visits pull requests new or changed; If leaves held ones, saying so once; Once waits for merge to be " +
        "offered; Or give up ends the wait; Otherwise, marked reasoned, asks the model each time; Until ends the watch.",
)

fun once() = Svg("once", 960, 420).apply {
    text(30, 40, "The first time a step is needed", cls = "h", anchor = "start")
    card(30, 64, 170, 76, "The goal's words", "“merge it by squashing”")
    arrow(200, 102, 248, 102)
    card(250, 64, 190, 76, "The model", "reads what is offered", cls = "hl")
    arrow(440, 102, 488, 102)
    card(490, 64, 210, 76, "A decision", "github:merge, squash")
    arrow(700, 102, 748, 102)
    card(750, 64, 180, 76, "Kept", "in the run's capture")
    text(330, 224, "Every later time, for every pull request", cls = "h", anchor = "start")
    path("M840 140 L840 188 L300 188 L300 236", cls = "dash")
    card(200, 238, 200, 76, "Replay", "the kept decision")
    arrow(400, 276, 448, 276)
    card(450, 238, 210, 76, "The server", "still offers merge here?")
    arrow(660, 276, 708, 276)
    card(710, 238, 220, 76, "Act", "or wait, or give up")
    box(30, 238, 140, 76, "ghost")
    text(100, 270, "no model", "asked", cls = "s", first = "t")
    text(480, 380, "The server's offers check each replay: a decision is used only where its act is offered now.", cls = "s")
}.render(
    "Decided once by the model, replayed with no model",
    "The first time a step is needed, the model reads the goal's words against what the server offers and decides; " +
        "the decision is kept. Every later time it is replayed, and the server's own offers say whether it applies.",
)

fun reasoned() = Svg("reasoned", 960, 360).apply {
    card(20, 40, 200, 80, "The wait gives up", "3 minutes, no merge offered")
    arrow(220, 80, 268, 80)
    box(270, 30, 380, 100, "box")
    text(460, 60, "The server says why", cls = "h")
    add("""<text class="m" x="290" y="92">hr:unavailable → merge:</text>""")
    add("""<text class="m" x="290" y="114">“A draft can't be merged; mark it…”</text>""")
    arrow(650, 80, 698, 80)
    card(700, 40, 240, 80, "The model", "the goal's words + the reason", cls = "hl")
    arrow(820, 120, 820, 178)
    card(700, 180, 240, 80, "Picks a form", "comment, as offered")
    arrow(700, 220, 652, 220)
    box(270, 180, 380, 80, "hl")
    text(460, 212, "“…this pull request wasn't merged because it's", cls = "s")
    text(460, 234, "still a draft, so please mark it ready for review…”", cls = "s")
    text(480, 320, "Asked afresh at every give-up; nothing of it is kept or replayed.", cls = "s")
}.render(
    "The one place the model stays: the reasoned Otherwise",
    "When a wait gives up, the server's own reason for withholding merge and the goal's words go to the model, which " +
        "chooses the comment form the pull request offers and writes one sentence. It is asked every time.",
)

fun offers() = Svg("offers", 960, 400).apply {
    text(235, 34, "A pull request that can merge", cls = "h")
    text(725, 34, "A draft", cls = "h")
    box(20, 52, 430, 300, "box")
    box(510, 52, 430, 300, "box")
    box(34, 98, 400, 28, "hl")
    listOf("_forms:", "  github:merge     PUT  merge_method", "  github:comment   POST body", "  github:close", "  github:edit")
        .forEachIndexed { i, s -> add("""<text class="m" x="40" y="${90 + i * 26}">${esc(s)}</text>""") }
    listOf("_forms:", "  github:comment   POST body", "  github:close", "  github:edit")
        .forEachIndexed { i, s -> add("""<text class="m" x="530" y="${90 + i * 26}">${esc(s)}</text>""") }
    box(524, 212, 400, 112, "hl")
    listOf("hr:unavailable:", "  merge: “A draft can't be merged;", "          mark it ready for review", "          first.”")
        .forEachIndexed { i, s -> add("""<text class="m" x="530" y="${232 + i * 26}">${esc(s)}</text>""") }
    text(480, 384, "Once waits for merge to appear among the forms; the reason is the server's, in its words.", cls = "s")
}.render(
    "The same resource, offering merge or saying why not",
    "A pull request that can merge carries a merge form. A draft carries none, and its why-absent resource says, in " +
        "the server's words, that a draft can't be merged.",
)

fun lanes() = Svg("lanes", 960, 520).apply {
    val x0 = 200
    val perMin = 66.0
    fun x(sec: Int) = x0 + (sec / 60.0 * perMin).toInt()
    data class Lane(val pr: String, val what: String, val opened: Int?, val end: Int?, val kind: String)
    val lanes = listOf(
        Lane("#821", "the watch's issue", 8, 629, "close"),
        Lane("#820", "4.0.1, waiting", null, 34, "merge"),
        Lane("#805", "held", null, 40, "note"),
        Lane("#810", "held", null, 40, "note"),
        Lane("#822", "5.0.1, held", 68, 78, "note"),
        Lane("#817", "draft", null, 228, "comment"),
        Lane("#813", "conflict", null, 237, "comment"),
        Lane("#811", "draft", null, 248, "comment"),
        Lane("#806", "conflict", null, 259, "comment"),
        Lane("#803", "draft", null, 271, "comment"),
        Lane("#823", "5.0.2, draft", 136, 360, "comment"),
        Lane("#824", "5.0.3", 204, 295, "merge"),
    )
    (0..10).forEach { m ->
        line(x(m * 60), 40, x(m * 60), 470, cls = "dash")
        text(x(m * 60), 30, "${m}m", cls = "s")
    }
    lanes.forEachIndexed { i, l ->
        val y = 60 + i * 34
        text(20, y + 5, l.pr, cls = "t", anchor = "start")
        text(78, y + 5, l.what, cls = "s", anchor = "start")
        val from = l.opened ?: 0
        line(x(from), y, x(l.end ?: from), y, cls = if (l.opened == null) "dash" else "line")
        l.opened?.let { line(x(it), y - 8, x(it), y + 8, cls = "line") }
        val e = x(l.end ?: from)
        when (l.kind) {
            "merge" -> dot(e, y, r = 8)
            "note" -> add("""<circle class="hl" cx="$e" cy="$y" r="8"/>""")
            "comment" -> add("""<rect class="a" x="${e - 7}" y="${y - 7}" width="14" height="14" rx="2"/>""")
            else -> add("""<rect class="hl" x="${e - 7}" y="${y - 7}" width="14" height="14"/>""")
        }
    }
    dot(220, 496, r = 7); text(232, 501, "merged", cls = "s", anchor = "start")
    add("""<circle class="hl" cx="330" cy="496" r="7"/>"""); text(342, 501, "left, said once", cls = "s", anchor = "start")
    add("""<rect class="a" x="473" y="489" width="14" height="14" rx="2"/>"""); text(494, 501, "gave up; the model told its author", cls = "s", anchor = "start")
    line(760, 488, 760, 504, cls = "line"); text(770, 501, "opened during the run", cls = "s", anchor = "start")
}.render(
    "Live run 2, minute by minute: each pull request a lane",
    "In ten and a half minutes the watch merged two bumps, left three held ones with one note each, and gave up on " +
        "four drafts and two conflicts, each told why in one sentence. It ended when its issue was closed.",
)

fun mapping() = Svg("mapping", 960, 560).apply {
    text(230, 30, "merge.goal.md", cls = "h")
    text(715, 30, "merge.kts, made from the run", cls = "h")
    data class Row(val goal: String, val script: List<String>, val hl: Boolean = false)
    listOf(
        Row("In {owner}/{repo}", listOf("act(\"github:repos-get\", owner, repo)")),
        Row("Open an issue … `as watch`", listOf("act(\"github:open-issue\") {", "  keepAs(\"watch\") }")),
        Row("Until someone closes {watch}", listOf("watch(until = { at(\"watch\") {", "  \"state\" eq \"closed\" } })")),
        Row("Whenever … labelled {label} changes", listOf("act(\"github:pulls-list\", labels) {", "  whenever { has { labels has label }")),
        Row("If it's labelled {hold}, leave it …", listOf("leaveWhere(\"…say so once.\") {", "  \"github:labels\" has hold }")),
        Row("Once it can be merged, merge it …", listOf("once(\"github:merge\", giveUp = 3.minutes)", "  { act(\"github:merge\", squash) }")),
        Row("Otherwise, … `reasoned`", listOf("otherwise = { why ->", "  act(" + "intent = \"…\$why\") }"), hl = true),
    ).forEachIndexed { i, r ->
        val y = 50 + i * 70
        box(20, y, 400, 56, if (r.hl) "hl" else "box")
        add("""<text class="m" x="36" y="${y + 33}">${esc(r.goal)}</text>""")
        arrow(422, y + 28, 488, y + 28)
        box(490, y, 450, 56, if (r.hl) "hl" else "box")
        r.script.forEachIndexed { j, s ->
            add("""<text class="m" x="506" y="${y + (if (r.script.size == 1) 33 else 24 + j * 20)}">${esc(s)}</text>""")
        }
    }
}.render(
    "Each line of the goal, and the script line it became",
    "The goal's lines map one to one onto the generated script: named relations, checks as facts, the wait with its " +
        "give-up, and one act(" + "intent = …) where the goal said reasoned.",
)

val diagrams = mapOf(
    "lifecycle" to ::lifecycle, "history" to ::history, "goalmap" to ::goalmap, "once" to ::once,
    "reasoned" to ::reasoned, "offers" to ::offers, "lanes" to ::lanes, "mapping" to ::mapping,
)
val folder = File(out).apply { mkdirs() }
diagrams.forEach { (name, draw) -> File(folder, "$name.svg").writeText(draw()) }
println("wrote ${diagrams.size} diagrams to ${folder.path}")
