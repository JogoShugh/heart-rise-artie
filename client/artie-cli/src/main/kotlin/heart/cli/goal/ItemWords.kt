package heart.cli.goal

import heart.script.Step
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

/**
 * An item's words read for a goal that may watch: its [text] and the [marks] at its end, from the lines before its
 * tail; then the [tail] lines (`Or give up`, `Otherwise`), the first [cut] lines below the item's own.
 */
internal class ItemWords(val text: String, val marks: List<String>, val tail: List<ItemLine>, val cut: Int) {
    companion object {
        /** [lines] split at the first tail line after the first; a rule they break is told to [refuse]. */
        fun of(lines: List<ItemLine>, refuse: (String) -> Nothing): ItemWords {
            val cut = (1 until lines.size).firstOrNull { isTail(lines[it].text) } ?: lines.size
            val head = lines.take(cut)
            if (head.dropLast(1).any { it.marks.isNotEmpty() }) refuse("backticks go at an item's end")
            val text = head.joinToString(" ") { it.text }.trim()
            if (isTail(text)) refuse(asBullet(text))
            return ItemWords(text, head.last().marks, lines.drop(cut), cut)
        }
    }
}

/** An item's [words], a line each, with the backticks at each line's end; markup is told to [refuse]. */
internal fun itemLines(words: Paragraph, refuse: (String) -> Nothing): List<ItemLine> {
    val lines = mutableListOf<ItemLine>()
    var text = StringBuilder()
    var marks = mutableListOf<String>()
    generateSequence(words.firstChild) { it.next }.forEach { node ->
        when {
            node is Code -> marks += node.literal
            node is Text && marks.isNotEmpty() && node.literal.isNotBlank() -> refuse("backticks go at an item's end")
            node is Text -> if (marks.isEmpty()) text.append(node.literal)
            node is SoftLineBreak -> {
                lines += ItemLine(text.toString().trim(), marks)
                text = StringBuilder()
                marks = mutableListOf()
            }
            else -> refuse("emphasis, links and markup have no place in a step: write plain words")
        }
    }
    return lines + ItemLine(text.toString().trim(), marks)
}

/** An `Until`'s rules: said at the list's [top] level, with no [sub]-items and no [marks]; else told to [refuse]. */
internal fun until(top: Boolean, sub: BulletList?, marks: List<String>, refuse: (String) -> Nothing) {
    if (!top) refuse("Until ends the watch: it goes at the top of the list")
    if (sub != null) refuse("Until has no sub-items")
    if (marks.isNotEmpty()) refuse("Until takes no backticks")
}

/** The steps under [step]: an intent's, or a Whenever's. */
internal fun under(step: Step): List<Step> = when (step) {
    is Step.Intent -> step.children
    is Step.Whenever -> step.children
    else -> emptyList()
}

/** Whether [step], or a step under it, is a Whenever. */
internal fun watches(step: Step): Boolean = step is Step.Whenever || under(step).any(::watches)
