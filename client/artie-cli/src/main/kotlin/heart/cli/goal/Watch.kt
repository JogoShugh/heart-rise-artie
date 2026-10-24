package heart.cli.goal

import heart.script.GiveUp
import heart.script.Otherwise
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * The words of a goal that keeps watching (spec/ideas/goals-that-work-alongside-others.md, "Review, 2026-10-23"):
 * `Whenever` and `Until` open an item; `Once …,` opens a step's words; `Or give up after …` and `Otherwise, …` are
 * lines of their own under a step's words, its tail. The keywords ignore case, as the file's others do.
 */

/** One line of an item's words: its [text], and the backticks at its end. */
internal class ItemLine(val text: String, val marks: List<String>)

/** A step's tail: when its wait gives up, and what to do instead. */
internal class Tail(val giveUp: GiveUp?, val otherwise: Otherwise?)

/** Whether [text] is a line of a step's tail rather than more of its words. */
internal fun isTail(text: String): Boolean = GIVE_UP.containsMatchIn(text) || OTHERWISE.containsMatchIn(text)

/** What [text], a tail line put as a bullet, is told: it belongs under a step's words. */
internal fun asBullet(text: String): String =
    (if (GIVE_UP.containsMatchIn(text)) "Or give up" else "Otherwise") +
        " goes on the line under its step, not as a bullet"

/**
 * The tail [lines] of a step, the first at line [first]; [waits] when the step begins `Once`, the only step a
 * give-up ends. A rule a line breaks is told to [refuse], with its line.
 */
internal fun tail(lines: List<ItemLine>, first: Int, waits: Boolean, refuse: (Int, String) -> Nothing): Tail {
    var giveUp: GiveUp? = null
    var otherwise: Otherwise? = null
    lines.forEachIndexed { i, line ->
        val at = first + i
        val text = line.text.trim()
        if (line.marks.isNotEmpty()) refuse(at, "Or give up and Otherwise take no backticks")
        if (GIVE_UP.containsMatchIn(text)) {
            if (!waits) refuse(at, "Or give up ends a wait: it goes under a Once")
            if (giveUp != null) refuse(at, "Or give up is given twice")
            giveUp = GiveUp(after(text) ?: refuse(at, "Or give up after N seconds, minutes or hours"), at)
        } else {
            if (otherwise != null) refuse(at, "Otherwise is given twice")
            val words = OTHERWISE.find(text)?.let { text.substring(it.range.last + 1).trim() }
                ?: refuse(at, "under a step's words come only Or give up and Otherwise")
            otherwise = Otherwise(words.ifEmpty { refuse(at, "Otherwise, says what to do instead") }, at)
        }
    }
    return Tail(giveUp, otherwise)
}

/** `Once CONDITION, WORDS`: the condition and the words, split at the first comma outside quotes; null for none. */
internal fun once(text: String): Pair<String, String>? = opened(ONCE, text)?.let { comma(it) ?: ("" to "") }

/** `If CONDITION, WORDS`: the condition and the words, as Once splits them; null when If doesn't open [text]. */
internal fun given(text: String): Pair<String, String>? = opened(IF, text)?.let { comma(it) ?: ("" to "") }

/** [words] split at their first comma outside quotes, each side trimmed; null when they have none. */
internal fun comma(words: String): Pair<String, String>? {
    var quoted = false
    val at = words.indices.firstOrNull { i ->
        if (words[i] == '"') quoted = !quoted
        words[i] == ',' && !quoted
    }
    return at?.let { words.substring(0, it).trim() to words.substring(it + 1).trim() }
}

/** The words after `Whenever`, `Until` or `Once` opening [text]; null when another word opens it. */
internal fun opened(word: Regex, text: String): String? =
    word.find(text)?.let { text.substring(it.range.last + 1).trim() }

/** A give-up's time as the file says it, `10 minutes`, for printing. */
internal fun said(after: Duration): String {
    val (n, unit) = when {
        after.inWholeSeconds % SECONDS_AN_HOUR == 0L -> after.inWholeHours to "hour"
        after.inWholeSeconds % SECONDS_A_MINUTE == 0L -> after.inWholeMinutes to "minute"
        else -> after.inWholeSeconds to "second"
    }
    return "$n $unit" + if (n == 1L) "" else "s"
}

private fun after(text: String): Duration? = GIVE_UP_AFTER.matchEntire(text)?.destructured?.let { (n, unit) ->
    n.toLongOrNull()?.takeIf { it > 0 }?.let { count ->
        when (unit.lowercase().removeSuffix("s")) {
            "second" -> count.seconds
            "minute" -> count.minutes
            else -> count.hours
        }
    }
}

internal val WHENEVER = Regex("""^(?i)whenever\b""")
internal val UNTIL = Regex("""^(?i)until\b""")
private val ONCE = Regex("""^(?i)once\b""")
private val IF = Regex("""^(?i)if\b""")
private val GIVE_UP = Regex("""^(?i)or give up\b""")
private val OTHERWISE = Regex("""^(?i)otherwise\b,?""")
private val GIVE_UP_AFTER = Regex("""(?i)or give up after (\d+) (seconds?|minutes?|hours?)\.?""")
private const val SECONDS_A_MINUTE = 60L
private const val SECONDS_AN_HOUR = 3600L
