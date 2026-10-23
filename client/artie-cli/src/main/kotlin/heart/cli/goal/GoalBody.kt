package heart.cli.goal

import heart.script.Once
import heart.script.Step
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** A goal file's body: [text], a goal in words; or the [context] paragraphs above [steps], a goal in steps. */
internal class GoalBody(val text: String, val context: String, val steps: List<Step>)

/**
 * The body of [file], its [lines] from index [from] on, read as Markdown renders it (spec/PLAN-GOAL-MARKDOWN.md):
 * paragraphs, then at most one bulleted list. Each rule it breaks stops the run, at its line.
 */
internal fun body(file: String, lines: List<String>, from: Int, inputs: List<String>): GoalBody {
    indents(file, lines, from)
    val doc = PARSER.parse(lines.drop(from).joinToString("\n"))
    val reader = StepReader(file, inputs) { node -> from + (node.sourceSpans.firstOrNull()?.lineIndex ?: 0) + 1 }
    val paragraphs = mutableListOf<Paragraph>()
    var list: BulletList? = null
    generateSequence(doc.firstChild) { it.next }.forEach { node ->
        when {
            node is Paragraph && list != null -> reader.fail(node, "a paragraph after the list: the list ends the goal")
            node is Paragraph -> paragraphs += node
            node is BulletList && list != null ->
                reader.fail(node, "a second list: a goal has one, and siblings in it use the same bullet")
            node is BulletList -> list = node
            else -> reader.fail(node, misplaced(node))
        }
    }
    val words = paragraphs.joinToString("\n") { reader.paragraph(it) }
    return list?.let { GoalBody("", words, reader.steps(it)) } ?: GoalBody(words, "", emptyList())
}

/** What a block that has no place in a goal is, and what to write instead. */
private fun misplaced(node: Node): String = when (node) {
    is OrderedList -> NUMBERED
    else -> "${kind(node)} has no place in a goal: write paragraphs, then one list"
}

private fun kind(node: Node): String = when (node) {
    is Heading -> "a heading"
    is BlockQuote -> "a quote"
    is FencedCodeBlock, is IndentedCodeBlock -> "a code block"
    is ThematicBreak -> "a rule"
    is HtmlBlock -> "HTML"
    else -> "a ${node.javaClass.simpleName.lowercase()}"
}

/** Each bullet indented by spaces, 2 a level: Markdown forgives more, and a goal file doesn't. */
private fun indents(file: String, lines: List<String>, from: Int) {
    lines.withIndex().drop(from).forEach { (i, line) ->
        val indent = BULLET.find(line)?.groupValues?.get(1) ?: return@forEach
        if ('\t' in indent) fail(file, i + 1, "indent with spaces, 2 a level")
        if (indent.length % 2 != 0) fail(file, i + 1, "indent 2 spaces a level")
    }
}

/**
 * The steps of one goal file's list, each item read as its words, its marker and its marks; the names its steps
 * give, by the line that gives them; and every brace checked against the inputs and the names given above it.
 */
private class StepReader(val file: String, val inputs: List<String>, val line: (Node) -> Int) {
    /** The names the steps give, in the order the file gives them, each with its line. */
    private val named = linkedMapOf<String, Int>()

    fun fail(node: Node, what: String): Nothing = fail(file, line(node), what)

    /** A paragraph above the list, or a goal in words: plain words, its braces naming inputs only. */
    fun paragraph(paragraph: Paragraph): String {
        val (words, marks) = inline(paragraph, "\n") { fail(paragraph, it) }
        if (marks.isNotEmpty()) fail(paragraph, "backticks mark steps: a paragraph has none")
        Braces(inputs, inputs, emptyMap()).check(words) { fail(paragraph, it) }
        return words
    }

    /** The steps of [list], read; then their braces, each against what is given above it. */
    fun steps(list: BulletList): List<Step> {
        val steps = items(list, top = true)
        steps.filterIsInstance<Step.Until>().firstOrNull()?.takeIf { steps.none(::watches) }?.let {
            fail(file, it.line, "Until ends a watch: the goal has no Whenever")
        }
        val given = inputs.toMutableList()
        fun walk(step: Step) {
            val braces = Braces(inputs, given, named)
            val check = { words: String -> braces.check(words) { fail(file, step.line, it) } }
            when (step) {
                is Step.Intent -> (listOf(step.words) + listOfNotNull(step.once?.condition, step.otherwise?.words))
                    .forEach(check)
                is Step.Wait -> check(step.condition)
                is Step.Verify -> step.facts.forEach(check)
                is Step.Whenever -> check(step.match)
                is Step.Until -> check(step.condition)
            }
            (step as? Step.Intent)?.name?.let { given += it }
            (step as? Step.Whenever)?.name?.let { given += it }
            under(step).forEach(::walk)
        }
        steps.forEach(::walk)
        return steps
    }

    private fun items(list: BulletList, top: Boolean = false): List<Step> =
        children(list).map { item(it as ListItem, top) }

    private fun item(item: ListItem, top: Boolean): Step {
        val words = item.firstChild as? Paragraph ?: fail(item, "an item holds words")
        val sub = sublist(words)
        val read = ItemWords.of(itemLines(words) { fail(words, it) }) { fail(item, it) }
        val tailOf = { waits: Boolean ->
            tail(read.tail, line(item) + read.cut, waits) { at, what -> fail(file, at, what) }
        }
        val step = MARKER.matchEntire(read.text)?.let { marked(item, it, read.marks, sub) }
            ?: opened(WHENEVER, read.text)?.let { whenever(item, it, read.marks, sub) }
            ?: opened(UNTIL, read.text)?.let { condition ->
                until(top, sub, read.marks) { fail(item, it) }
                Step.Until(condition, line(item))
            }
        return step?.also { tailOf(false) } ?: intent(item, read, sub, tailOf)
    }

    /** A step of words, maybe `Once CONDITION, WORDS`, with what it gives up after and does otherwise. */
    private fun intent(item: ListItem, read: ItemWords, sub: BulletList?, tailOf: (Boolean) -> Tail): Step {
        val once = once(read.text)
        if (once != null && once.second.isEmpty()) fail(item, "Once says what to wait for, a comma, then what to do")
        val given = Marks.of(read.marks) { fail(item, it) }
        if (given.reasoned && sub != null) fail(item, "reasoned marks a step with no sub-items")
        given.name?.let { name(it, item) }
        val tail = tailOf(once != null)
        return Step.Intent(
            once?.second ?: read.text, line(item), given.name, given.moves, given.reasoned,
            sub?.let(::items).orEmpty(), once?.let { Once(it.first, tail.giveUp) }, tail.otherwise,
        )
    }

    /** `Whenever MATCH`: the steps under it done on each change it matches; its marks name the changed resource. */
    private fun whenever(item: ListItem, match: String, marks: List<String>, sub: BulletList?): Step {
        val given = Marks.of(marks) { fail(item, it) }
        if (given.moves != null || given.reasoned) fail(item, "Whenever takes only as NAME")
        if (sub == null) fail(item, "Whenever has steps under it: what to do each time")
        given.name?.let { name(it, item) }
        return Step.Whenever(match, line(item), given.name, items(sub))
    }

    /** The one list under an item's [words], if it has one. */
    private fun sublist(words: Paragraph): BulletList? {
        var sub: BulletList? = null
        generateSequence(words.next) { it.next }.forEach { node ->
            when {
                node is BulletList && sub != null ->
                    fail(node, "${node.bulletMarker} after ${sub?.bulletMarker}: siblings use the same bullet")
                node is BulletList -> sub = node
                node is OrderedList -> fail(node, NUMBERED)
                else -> fail(node, "an item holds words, and under them one list")
            }
        }
        return sub
    }

    /** A `Wait:` or a `Verify:` item; a word before a colon that is neither is a marker misspelled. */
    private fun marked(item: ListItem, marker: MatchResult, marks: List<String>, sub: BulletList?): Step {
        val word = marker.groupValues[1]
        val rest = marker.groupValues[2].trim()
        val known = word.lowercase()
        if (known !in setOf("wait", "verify")) {
            fail(item, "\"$word:\" is no marker: write Wait: or Verify:, or put the words another way")
        }
        if (marks.isNotEmpty()) fail(item, "Wait: and Verify: take no backticks")
        if (known == "wait") {
            if (sub != null) fail(item, "Wait: has no sub-items")
            if (rest.isEmpty()) fail(item, "Wait: says what to wait for")
            return Step.Wait(rest, line(item))
        }
        val facts = sub?.let { facts(it) { node, what -> fail(node, what) } }
        if (rest.isEmpty() == (facts == null)) fail(item, "Verify: has a fact after it, or facts under it, not both")
        return Step.Verify(facts ?: listOf(rest), line(item))
    }

    /** A name an `as` gives: not an input's, not given before, nor either in another case. */
    private fun name(name: String, item: ListItem) {
        inputs.firstOrNull { it == name }?.let { fail(item, "$name is an input already") }
        named.keys.firstOrNull { it == name }?.let { fail(item, "$name is named twice") }
        (inputs + named.keys).firstOrNull { it.equals(name, ignoreCase = true) }?.let {
            fail(item, "$name differs from $it only in case")
        }
        named[name] = line(item)
    }
}

/** The facts a `Verify:` lists under it, each plain words; a rule one breaks is told to [refuse], at its node. */
private fun facts(list: BulletList, refuse: (Node, String) -> Nothing): List<String> = children(list).map { fact ->
    val words = fact.firstChild as? Paragraph ?: refuse(fact, "a fact is words")
    if (words.next != null) refuse(fact, "a fact has no sub-items")
    val (text, marks) = inline(words, " ") { refuse(words, it) }
    if (marks.isNotEmpty()) refuse(fact, "a fact takes no backticks")
    text
}

/**
 * [paragraph]'s words, line breaks put as [breaks], and the backticks at its end. Anything else Markdown would
 * render differently from the words, emphasis or a link, has no place, and is told to [refuse].
 */
private fun inline(paragraph: Paragraph, breaks: String, refuse: (String) -> Nothing): Pair<String, List<String>> {
    val words = StringBuilder()
    val marks = mutableListOf<String>()
    generateSequence(paragraph.firstChild) { it.next }.forEach { node ->
        when {
            node is Code -> marks += node.literal
            node is Text && marks.isNotEmpty() && node.literal.isNotBlank() -> refuse("backticks go at an item's end")
            node is Text -> if (marks.isEmpty()) words.append(node.literal)
            node is SoftLineBreak -> if (marks.isEmpty()) words.append(breaks)
            else -> refuse("emphasis, links and markup have no place in a step: write plain words")
        }
    }
    return words.toString().trim() to marks
}

private fun children(node: Node): List<Node> = generateSequence(node.firstChild) { it.next }.toList()

private val PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()
private val BULLET = Regex("""^([ \t]*)[-*+][ \t]""")
private val MARKER = Regex("""^([A-Za-z]+):\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
private const val NUMBERED = "a numbered list: steps are bullets, and their order is the order"
