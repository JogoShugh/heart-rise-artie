package heart.cli.goal

import heart.script.Alongside
import heart.script.Step
import org.commonmark.node.BulletList
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text

/**
 * A goal in sections (spec/ideas/goals-that-work-alongside-others.md): its brief, the paragraphs above the first
 * heading; then each section under its ## heading, each at most once: the manners in words, the work, the outcomes.
 * [nodes] are the body's blocks; [reader] reads the steps and says each rule broken, at its line.
 */
internal fun sections(nodes: List<Node>, reader: StepReader): GoalBody {
    val first = nodes.indexOfFirst { it is Heading }
    val brief = nodes.take(first).joinToString("\n") { node ->
        when (node) {
            is Paragraph -> reader.paragraph(node)
            is BulletList -> reader.fail(node, "a list goes under The work")
            else -> reader.fail(node, "above the sections go paragraphs only")
        }
    }
    val by = sectioned(nodes.drop(first), reader)
    val alongside = by[ALONGSIDE]?.let { (heading, body) -> Alongside(manners(body, reader), reader.line(heading)) }
    val (heading, work) = by[WORK] ?: reader.fail(nodes[first], "a goal in sections has The work")
    val list = work.singleOrNull() as? BulletList
        ?: reader.fail(work.firstOrNull { it !is BulletList } ?: heading, "The work is one list")
    val steps = reader.steps(list)
    val done = by[DONE]?.let { (doneHeading, body) -> done(doneHeading, body, reader) }.orEmpty()
    return GoalBody("", brief, steps, alongside, done)
}

/** [nodes], from the first heading on, as each section's heading and the blocks under it, by the section's name. */
private fun sectioned(nodes: List<Node>, reader: StepReader): Map<String, Pair<Heading, List<Node>>> {
    val split = mutableListOf<Pair<Heading, MutableList<Node>>>()
    nodes.forEach { node -> if (node is Heading) split += node to mutableListOf() else split.last().second += node }
    val by = linkedMapOf<String, Pair<Heading, List<Node>>>()
    split.forEach { (heading, body) ->
        if (heading.level != SECTION) reader.fail(heading, "a section is a ## heading")
        val said = generateSequence(heading.firstChild) { it.next }.filterIsInstance<Text>()
            .joinToString("") { it.literal }.trim()
        val name = SECTIONS.firstOrNull { it.equals(said, ignoreCase = true) }
            ?: reader.fail(heading, "no section $said: a goal has ${SECTIONS.dropLast(1).joinToString()} and $DONE")
        if (name in by) reader.fail(heading, "$name is given twice")
        by[name] = heading to body
    }
    return by
}

/** Working alongside others: paragraphs, then one list of manners, each plain words. */
private fun manners(body: List<Node>, reader: StepReader): List<String> {
    var listed = false
    return body.flatMap { node ->
        when {
            node is Paragraph && !listed -> listOf(reader.paragraph(node))
            node is BulletList && !listed -> {
                listed = true
                plain(node, "a manner") { item, what -> reader.fail(item, what) }.map { it.first }
            }
            else -> reader.fail(node, "$ALONGSIDE is words, then one list")
        }
    }
}

/** Done when: one list of facts, each its own check at its line, its braces naming inputs or names given. */
private fun done(heading: Heading, body: List<Node>, reader: StepReader): List<Step.Verify> {
    val list = body.singleOrNull() as? BulletList
        ?: reader.fail(body.firstOrNull { it !is BulletList } ?: heading, "$DONE is one list of facts")
    val braces = Braces(reader.inputs, reader.inputs + reader.named.keys, reader.named)
    return plain(list, "a fact") { item, what -> reader.fail(item, what) }.map { (fact, item) ->
        braces.check(fact) { reader.fail(item, it) }
        Step.Verify(listOf(fact), reader.line(item))
    }
}

private const val ALONGSIDE = "Working alongside others"
private const val WORK = "The work"
private const val DONE = "Done when"

/** The sections a goal may have, in the order they are named. */
private val SECTIONS = listOf(ALONGSIDE, WORK, DONE)
