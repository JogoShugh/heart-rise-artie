package heart.script

import heart.script.Values.compared
import heart.script.Values.plainOf
import heart.script.Values.same
import heart.script.Values.shown
import kotlinx.serialization.json.JsonArray

/**
 * A test of one member's value, carrying its own words: [test] names it (`above 0`), and [expected] is what
 * a failure says was expected (`above 0`, `to contain bug`). [holds] gets whether the member is there at all,
 * and its value (spec/ideas/script-checks-and-reason-blocks.md).
 */
class Matcher(val test: String, val expected: String, val holds: (present: Boolean, value: Any?) -> Boolean)

/** The member equals [value]; numbers compare as numbers, whatever JSON number they arrived as. */
fun equalTo(value: Any?) = Matcher("eq ${shown(value)}", shown(value)) { present, held -> present && same(held, value) }

fun notEqualTo(value: Any?) = Matcher("isNot ${shown(value)}", "not ${shown(value)}") { _, held -> !same(held, value) }

fun above(n: Number) = Matcher("above $n", "above $n") { _, held -> (compared(held, n) ?: 0) > 0 }

fun below(n: Number) = Matcher("below $n", "below $n") { _, held -> (compared(held, n) ?: 0) < 0 }

fun atLeast(n: Number) = Matcher("atLeast $n", "at least $n") { _, held -> compared(held, n)?.let { it >= 0 } == true }

fun atMost(n: Number) = Matcher("atMost $n", "at most $n") { _, held -> compared(held, n)?.let { it <= 0 } == true }

fun between(low: Number, high: Number) = Matcher("between $low and $high", "between $low and $high") { _, held ->
    compared(held, low)?.let { it >= 0 } == true && compared(held, high)?.let { it <= 0 } == true
}

/** An array holds the item, or a text contains it. */
fun contains(item: Any?) = Matcher("has ${shown(item)}", "to contain ${shown(item)}") { _, held ->
    when (held) {
        is JsonArray -> held.any { same(plainOf(it), item) }
        is String -> item is String && item in held
        else -> false
    }
}

fun oneOf(values: Collection<Any?>): Matcher {
    val words = shown(values.toList())
    return Matcher("oneOf $words", "one of $words") { _, held -> values.any { same(held, it) } }
}

fun matching(pattern: Regex) = Matcher("matching ${pattern.pattern}", "matching ${pattern.pattern}") { _, held ->
    held is String && pattern.matches(held)
}

/** The member is there, null or not. */
val present = Matcher("present", "present") { present, _ -> present }

/** The member is not there at all; a member that is there and null is not absent. */
val absent = Matcher("absent", "absent") { present, _ -> !present }
