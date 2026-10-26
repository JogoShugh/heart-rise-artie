// The diagrams of "The Browser for Agents", drawn as SVG. Each diagram is a function below; this script writes one
// file per diagram into the folder given, and page.kts inlines them into the page.
//   artie run diagrams.kts out=diagrams
// Colours come from CSS custom properties (--d-ink, --d-accent...) with light fallbacks, so a page that inlines a
// diagram themes it, and the file alone still reads.
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

fun fanout() = Svg("fanout", 960, 420).apply {
    text(240, 28, "The Web: one client, any site", cls = "h")
    text(720, 28, "APIs: one client per API", cls = "h")
    card(40, 180, 170, 64, "Browser", "knows HTML", cls = "hl")
    listOf("a shop", "a bank", "a newspaper", "a council's forms", "a billion more").forEachIndexed { i, site ->
        val y = 56 + i * 64
        card(290, y, 160, 44, site, cls = if (i == 4) "ghost" else "box")
        arrow(210, 212, 288, y + 22)
    }
    line(480, 50, 480, 380)
    listOf("GitHub", "Stripe", "Jira", "your billing").forEachIndexed { i, api ->
        val y = 72 + i * 72
        card(510, y, 170, 48, "$api agent")
        card(770, y, 160, 48, "$api API")
        arrow(680, y + 24, 768, y + 24)
    }
    text(240, 404, "coupled to a media type", cls = "s")
    text(720, 404, "coupled to each API, by hand", cls = "s")
}.render(
    "One browser for every site, one agent per API",
    "On the left a single browser reaches a shop, a bank, a newspaper and a billion more sites, because it knows only " +
        "HTML. On the right each agent is wired to one API: a GitHub agent to GitHub, a Stripe agent to Stripe.",
)

fun islands() = Svg("islands", 960, 320).apply {
    line(40, 210, 920, 210, "line")
    data class Event(val x: Int, val year: String, val name: String, val sub: String, val web: Boolean = false)
    listOf(
        Event(110, "1945", "Memex", "a desk, never built"),
        Event(280, "1965", "Hypertext", "Nelson's word"),
        Event(450, "1968", "NLS", "its own terminal"),
        Event(620, "1987", "HyperCard", "its own app"),
        Event(820, "1991", "The Web", "any browser, any site", web = true),
    ).forEach { e ->
        line(e.x, 170, e.x, 210, "line")
        dot(e.x, 210, if (e.web) 9 else 6)
        text(e.x, 238, e.year, cls = "s")
        if (e.web) card(e.x - 95, 72, 190, 98, e.name, e.sub, cls = "hl")
        else card(e.x - 72, 92, 144, 78, e.name, e.sub)
    }
    text(480, 286, "Each early system came with its own client.", cls = "s")
    text(480, 306, "The Web split the client from the content, and agreed only on formats.", cls = "s")
}.render(
    "Hypertext before and after the Web",
    "A timeline: Memex in 1945, Nelson's hypertext in 1965, NLS in 1968, HyperCard in 1987, each with a client of its " +
        "own; then the Web in 1991, where any browser reads any site.",
)

fun knows() = Svg("knows", 960, 380).apply {
    box(40, 30, 560, 320)
    text(320, 66, "What a browser knows", cls = "h")
    data class Layer(val label: String, val sub: String, val hl: Boolean)
    listOf(
        Layer("What a link and a form mean", "follow it; fill it in and send it", true),
        Layer("HTML: the media type", "how a page carries its information and its controls", true),
        Layer("URIs", "how to name and reach anything", false),
        Layer("HTTP", "methods, status codes, caching", false),
    ).forEachIndexed { i, l -> card(70, 90 + i * 62, 500, 52, l.label, l.sub, cls = if (l.hl) "hl" else "box") }
    box(640, 30, 280, 320, "ghost")
    text(780, 66, "What it doesn't know", cls = "h")
    text(780, 130, "Amazon", "your bank", "any site's addresses", "any site's rules", "anything about you", cls = "s", step = 34)
}.render(
    "What a browser knows, and what it doesn't",
    "The browser knows HTTP, URIs, the HTML media type and what links and forms mean. It knows nothing about any " +
        "particular site: not Amazon, not your bank, not their addresses or rules.",
)

fun constraints() = Svg("constraints", 960, 270).apply {
    text(480, 28, "REST's four interface constraints (Fielding, 2000, §5.1.5)", cls = "h")
    listOf(
        listOf("Identification", "of resources"),
        listOf("Manipulation", "through representations"),
        listOf("Self-descriptive", "messages"),
        listOf("Hypermedia", "as the engine of", "application state"),
    ).forEachIndexed { i, words ->
        card(40 + i * 225, 60, 205, 110, words[0], *words.drop(1).toTypedArray(), cls = if (i == 3) "hl" else "box")
    }
    path("M40 190 L40 200 L695 200 L695 190", head = false)
    text(367, 228, "kept by most HTTP APIs", cls = "s")
    path("M715 190 L715 200 L920 200 L920 190", "accline", head = false)
    text(817, 228, "mostly dropped", cls = "t", first = "t")
    text(817, 250, "and the one a generic client needs", cls = "s")
}.render(
    "REST's four interface constraints",
    "Identification of resources, manipulation through representations and self-descriptive messages are kept by most " +
        "HTTP APIs. The fourth, hypermedia as the engine of application state, is mostly dropped, and it is the one a " +
        "generic client needs.",
)

fun drift() = Svg("drift", 960, 400).apply {
    line(60, 70, 920, 70, "accline")
    text(70, 56, "In the answer itself", cls = "s", anchor = "start")
    data class Era(val x: Int, val y: Int, val era: String, val where: String)
    val eras = listOf(
        Era(120, 70, "The Web", "in the page"),
        Era(300, 150, "RPC, SOAP", "in an interface file"),
        Era(480, 210, "JSON over HTTP", "in documentation"),
        Era(660, 260, "OpenAPI", "in a description of all"),
        Era(840, 310, "LLM agents", "in training data"),
    )
    path(eras.joinToString(" ") { (if (it == eras.first()) "M" else "L") + "${it.x} ${it.y}" }, "dash", head = false)
    eras.forEach { e ->
        dot(e.x, e.y)
        text(e.x, e.y + 30, e.era, e.where, cls = "s", first = "t")
    }
    add("""<text class="s" x="0" y="0" text-anchor="middle" transform="translate(30 200) rotate(-90)">further from the answer</text>""")
    path("M30 290 L30 350")
}.render(
    "Where the rules live, era by era",
    "On the Web the rules arrive in the page. With RPC they moved to interface files, with JSON APIs to documentation, " +
        "with OpenAPI to a description of every operation, and for LLM agents to training data: each step further from " +
        "the answer a client holds.",
)

fun blank() = Svg("blank", 960, 330).apply {
    box(40, 40, 460, 250)
    text(60, 76, "{ \"number\": 20,", "  \"state\": \"open\",", "  \"title\": \"Bump left-pad\",",
        "  \"mergeable_state\": \"unknown\",", "  \"url\": \"https://api.github.com/…\",", "  … }",
        cls = "m", anchor = "start", step = 30)
    card(700, 130, 220, 80, "An agent", "what can I do now?", cls = "hl")
    listOf("merge it?" to 70, "with what?" to 160, "allowed now?" to 250).forEach { (q, y) ->
        box(540, y - 20, 120, 36, "ghost", rx = 18)
        text(600, y + 3, q, cls = "s")
        path("M698 170 L662 ${y - 2}", "dash")
    }
}.render(
    "An agent facing data with no controls",
    "A pull request as JSON: its number, state, title and URLs. Nothing in it says whether it can be merged now, how, " +
        "or with what. The agent is left asking.",
)

fun mapping() = Svg("mapping", 960, 410).apply {
    text(385, 34, "HTML · for a browser", cls = "h")
    text(765, 34, "HEART · for an agent", cls = "h")
    data class Row(val label: String, val sub: String, val html: List<String>, val heart: List<String>)
    listOf(
        Row("A link", "by relation", listOf("<a rel=\"author\"", "   href=\"/users/ana\">"),
            listOf("\"_links\": { \"author\":", "  { \"href\": \"/users/ana\" } }")),
        Row("An action", "method and target", listOf("<form method=\"post\"", "  action=\"/pulls/20/merge\">"),
            listOf("\"_forms\": { \"github:merge\": {", "  \"method\": \"PUT\", … } }")),
        Row("Its inputs", "what to send", listOf("<input name=\"title\" required", "  maxlength=\"256\">"),
            listOf("\"title\": { \"type\": \"string\",", "  \"maxLength\": 256 }")),
        Row("Not possible now", "and why", listOf("(the button isn't there)"),
            listOf("(no form; hr:unavailable", " says why)")),
    ).forEachIndexed { i, r ->
        val y = 60 + i * 84
        text(40, y + 30, r.label, r.sub, cls = "s", first = "t", anchor = "start")
        box(210, y, 350, 64)
        text(226, y + 27, *r.html.toTypedArray(), cls = "m", anchor = "start")
        arrow(564, y + 32, 586, y + 32)
        box(590, y, 350, 64, "hl")
        text(606, y + 27, *r.heart.toTypedArray(), cls = "m", anchor = "start")
    }
}.render(
    "HTML and HEART, element for element",
    "An HTML link with a rel is a HEART link by relation; an HTML form is a HEART form with method and target; an " +
        "input's constraints are a JSON Schema; and where HTML shows no button, HEART offers no form and says why.",
)

fun stacks() = Svg("stacks", 960, 440).apply {
    text(250, 30, "On the Web", cls = "h")
    text(710, 30, "With HEART", cls = "h")
    val left = listOf("A person" to "chooses what to do", "A browser" to "generic: knows HTML",
        "HTML" to "the media type", "Any website" to "its own resources and rules")
    val right = listOf("A model" to "chooses among what's offered", "ARTIE" to "generic: knows HEART",
        "HEART" to "the media type", "Any HEART service" to "its own resources and rules")
    listOf(100 to left, 560 to right).forEach { (x, layers) ->
        layers.forEachIndexed { i, (t, s) ->
            val y = 50 + i * 90
            card(x, y, 300, 66, t, s, cls = if (i == 2) "hl" else "box")
            if (i < 3) arrow(x + 150, y + 66, x + 150, y + 88)
        }
    }
    (0..3).forEach { i -> text(480, 50 + i * 90 + 39, "≈", cls = "h") }
    text(480, 424, "Each client is coupled to the media type, never to a site.", cls = "s")
}.render(
    "The browser stack and the agent stack",
    "A person, a browser, HTML and any website, next to a model, ARTIE, HEART and any HEART service. The browser and " +
        "ARTIE are generic; each is coupled only to its media type.",
)

fun routine() = Svg("routine", 960, 280).apply {
    data class Step(val title: String, val sub: List<String>, val hl: Boolean = false)
    val steps = listOf(
        Step("A goal", listOf("plain words,", "written once")),
        Step("A first run", listOf("a model chooses", "among offers")),
        Step("A script", listOf("its choices, with", "the forms pinned")),
        Step("Every later run", listOf("no model; stops if", "a form changes"), hl = true),
    )
    steps.forEachIndexed { i, s ->
        val x = 40 + i * 230
        card(x, 60, 190, 100, s.title, *s.sub.toTypedArray(), cls = if (s.hl) "hl" else "box")
        if (i < 3) arrow(x + 192, 110, x + 228, 110)
    }
    path("M820 60 C820 20 900 20 900 58", "accline")
    path("M40 186 L40 196 L460 196 L460 186", head = false)
    text(250, 222, "browsing", cls = "t")
    path("M500 186 L500 196 L920 196 L920 186", "accline", head = false)
    text(710, 222, "a routine", cls = "t")
    text(480, 262, "Like a recorded macro, except every step is held to the controls it was offered.", cls = "s")
}.render(
    "From browsing to a routine",
    "A goal in plain words is browsed once, with a model choosing among the offers. Its choices become a script with " +
        "the forms pinned, and every later run needs no model and stops if a form changes.",
)

fun gateway() = Svg("gateway", 960, 300).apply {
    data class Row(val y: Int, val era: String, val client: String, val middle: String, val sub: String, val back: String)
    listOf(
        Row(50, "The 1990s", "A browser", "A web front end", "renders HTML from it", "A mainframe app"),
        Row(170, "Now", "ARTIE", "A HEART gateway", "renders HEART from it", "GitHub's REST API"),
    ).forEach { r ->
        text(40, r.y + 38, r.era, cls = "t", anchor = "start")
        card(170, r.y, 210, 76, r.client)
        arrow(382, r.y + 38, 428, r.y + 38)
        card(430, r.y, 230, 76, r.middle, r.sub, cls = "hl")
        arrow(662, r.y + 38, 708, r.y + 38)
        card(710, r.y, 210, 76, r.back)
    }
    text(480, 286, "The old system stays as it is. A new front speaks the generic client's media type.", cls = "s")
}.render(
    "A front for an old system, then and now",
    "In the 1990s a web front end put HTML in front of a mainframe application so any browser could use it. Now a " +
        "HEART gateway puts HEART in front of GitHub's REST API so ARTIE can use it.",
)

val diagrams = mapOf(
    "fanout" to ::fanout, "islands" to ::islands, "knows" to ::knows, "constraints" to ::constraints,
    "drift" to ::drift, "blank" to ::blank, "mapping" to ::mapping, "stacks" to ::stacks,
    "routine" to ::routine, "gateway" to ::gateway,
)
val folder = File(out).apply { mkdirs() }
diagrams.forEach { (name, draw) -> File(folder, "$name.svg").writeText(draw()) }
println("wrote ${diagrams.size} diagrams to ${folder.path}")
