// The diagrams of "Information and Controls", drawn as SVG, one file per diagram into the folder given; page.kts
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

fun coupling() = Svg("coupling", 960, 520).apply {
    text(240, 30, "The browser", cls = "h")
    text(720, 30, "A generic API client", cls = "h")
    line(480, 20, 480, 500)
    card(160, 210, 160, 64, "Browser", "knows formats", cls = "hl")
    listOf("HTML: links, forms" to "box", "PNG, JPEG: images" to "box", "CSS: presentation" to "box", "JavaScript: code on demand" to "ghost")
        .forEachIndexed { i, (t, c) ->
            val y = 60 + i * 52
            card(20, y, 130, 40, t.substringBefore(':'), cls = c)
            line(150, y + 20, 200, 210, cls = if (c == "ghost") "dash" else "line")
        }
    text(85, 280, "optional (dashed)", cls = "s")
    listOf("a bank", "a wiki", "a shop").forEachIndexed { i, s ->
        val x = 40 + i * 140
        card(x, 400, 120, 44, s)
        path("M${x + 60} 400 L240 276", cls = "dash", head = false)
    }
    text(240, 480, "knows no site: each sends its own controls", cls = "s")
    card(640, 210, 160, 64, "ARTIE", "knows formats", cls = "hl")
    listOf("HAL: links, embeds", "HEART profile: forms", "JSON Schema: fields", "RFC 9457: refusals")
        .forEachIndexed { i, t ->
            val y = 60 + i * 52
            card(500, y, 130, 40, t.substringBefore(':'))
            line(630, y + 20, 680, 210, cls = "line")
        }
    listOf("GitHub", "a garden", "tickets").forEachIndexed { i, s ->
        val x = 520 + i * 140
        card(x, 400, 120, 44, s)
        path("M${x + 60} 400 L720 276", cls = "dash", head = false)
    }
    listOf("a person", "an agent", "a script").forEachIndexed { i, s ->
        text(830, 180 + i * 50, s, cls = "t", anchor = "start")
        arrow(826, 175 + i * 50, 802, 242)
    }
    text(720, 480, "knows no API: each sends its own controls", cls = "s")
}.render(
    "Generic clients, coupled to media types, not to sites",
    "The browser is tightly coupled to HTML, images, CSS and, optionally, JavaScript, and to no site: a bank, a " +
        "wiki and a shop each send their own controls. ARTIE is coupled the same way to HAL, the HEART profile, JSON " +
        "Schema and problem details, and to no API; a person, an agent or a script can use it.",
)

fun bag() = Svg("bag", 960, 460).apply {
    text(240, 30, "A bag of endpoints", cls = "h")
    text(720, 30, "Controls in the answer", cls = "h")
    line(480, 20, 480, 440)
    listOf(
        "GET /repos/{o}/{r}/issues?labels=",
        "GET /repos/{o}/{r}/pulls/{n}",
        "PUT /repos/{o}/{r}/pulls/{n}/merge",
        "POST /repos/{o}/{r}/issues/{n}/comments",
        "GET /repos/{o}/{r}/issues/{n}",
        "… 823 paths in all",
    ).forEachIndexed { i, s ->
        val y = 56 + i * 52
        box(20, y, 290, 34, if (i == 5) "ghost" else "box")
        add("""<text class="m" x="30" y="${y + 22}" style="font-size:11.5px">${esc(s)}</text>""")
        path("M310 ${y + 17} L338 222", cls = "dash", head = false)
    }
    card(340, 190, 125, 64, "Client", "per API")
    card(340, 330, 125, 56, "The docs", "rules", cls = "ghost")
    arrow(402, 330, 402, 256)
    text(240, 420, "The client builds each address and knows each rule", cls = "s")
    card(630, 60, 180, 50, "The bookmark", "one URI")
    arrow(720, 110, 720, 148)
    card(600, 150, 240, 64, "An answer", "state + links + forms", cls = "hl")
    arrow(720, 214, 720, 252)
    card(600, 254, 240, 64, "The next answer", "chosen from those offered", cls = "hl")
    arrow(720, 318, 720, 356)
    card(600, 358, 240, 50, "…as long as needed", cls = "ghost")
}.render(
    "A bag of endpoints, and controls in the answer",
    "A JSON API is a bag of endpoints a client is built against, with the rules in documentation. A hypermedia API " +
        "starts from one bookmark, and every next step is chosen from the controls the current answer offers.",
)

fun splinter() = Svg("splinter", 960, 360).apply {
    card(370, 140, 220, 70, "A generic API client?", "which format to read", cls = "hl")
    listOf("HAL", "HAL-FORMS", "Siren", "Collection+JSON", "JSON:API", "Hydra", "UBER", "OpenAPI (a description)")
        .forEachIndexed { i, s ->
            val left = i < 4
            val x = if (left) 40 else 700
            val y = 30 + (i % 4) * 80
            card(x, y, 220, 50, s, cls = if (i == 7) "ghost" else "box")
            path(if (left) "M260 ${y + 25} L368 175" else "M700 ${y + 25} L592 175", cls = "dash", head = false)
        }
    text(480, 340, "Many good formats, no shared one: so clients were built per API instead", cls = "s")
}.render(
    "Hypermedia formats splintered; none became the HTML of APIs",
    "HAL, HAL-FORMS, Siren, Collection+JSON, JSON:API, Hydra and UBER each offered links, and some forms; none was " +
        "adopted everywhere. OpenAPI won by describing endpoints instead, and clients were built per API.",
)

fun readers() = Svg("readers", 960, 420).apply {
    box(330, 30, 300, 150, "hl")
    text(480, 58, "One answer: pull request 825", cls = "h")
    listOf("state: open, mergeable", "_forms: merge, comment, close…", "_links: comments, commits…", "_embedded: author, labels")
        .forEachIndexed { i, s -> add("""<text class="m" x="350" y="${90 + i * 22}">${esc(s)}</text>""") }
    arrow(400, 180, 160, 240)
    arrow(480, 180, 480, 240)
    arrow(560, 180, 800, 240)
    card(40, 242, 240, 110, "A person", "sees buttons:", "[Merge] [Comment] [Close]")
    card(360, 242, 240, 110, "An agent", "chooses among offers:", "github:merge, squash")
    card(680, 242, 240, 110, "A script", "replays a kept line:", "act(\"github:merge\", …)")
    text(480, 395, "Same controls, same server rules, three kinds of reader: Fielding's “user (or automaton)”", cls = "s")
}.render(
    "One answer, three readers",
    "The same HEART answer is rendered as buttons for a person, offered as choices to an AI agent, and acted on by a " +
        "script's kept line. The server's rules hold for all three.",
)

fun walk() = Svg("walk", 960, 400).apply {
    data class Stop(val name: String, val offers: String)
    listOf(
        Stop("The bookmark", "github:pulls-list"),
        Stop("Labelled list", "item links, titled"),
        Stop("Pull request", "merge? or why not"),
    ).forEachIndexed { i, s ->
        val x = 60 + i * 300
        card(x, 80, 200, 70, s.name, s.offers, cls = if (i == 2) "hl" else "box")
        if (i < 2) arrow(x + 200, 115, x + 298, 115)
    }
    arrow(720, 150, 650, 230)
    arrow(790, 150, 830, 230)
    card(530, 232, 180, 64, "Merge", "form offered", cls = "hl")
    card(760, 232, 160, 64, "Why not", "hr:unavailable")
    text(480, 40, "Each stop offers the next: no address is built by the client", cls = "s")
    text(480, 350, "This is the path the merge watch takes, look after look", cls = "s")
}.render(
    "The walk: from the bookmark to merge, or why not",
    "From the gateway's bookmark the client is offered the pull request list, then each pull request, which offers " +
        "merge when GitHub would accept it, or says why not.",
)

val diagrams = mapOf(
    "coupling" to ::coupling, "bag" to ::bag, "splinter" to ::splinter, "readers" to ::readers, "walk" to ::walk,
)
val folder = File(out).apply { mkdirs() }
diagrams.forEach { (name, draw) -> File(folder, "$name.svg").writeText(draw()) }
println("wrote ${diagrams.size} diagrams to ${folder.path}")
