package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Checks every fact against the state held now (spec/ideas/script-checks-and-reason-blocks.md). A fact is a
 * [Fact] (`"moisture" above 0`), or a pair of a member and a value (equality) or a [Matcher]
 * (`"closed_at" to present`), mixed as a script likes. When any fails, the script stops, saying for each
 * failed fact what was expected and what was held. The trace keeps each fact as data.
 */
fun Here.check(vararg facts: Any) = checkFacts(facts.map(::fact))

/**
 * Whether every one of [facts] holds on the held resource, as a watch's checks hold (spec/PLAN-LISTENERS.md, slice
 * 8): a fact names a member, or a relation embedded, its value among the names of what is embedded, or a relation
 * linked to, its value words a link's title says. Unlike [check], it stops nothing.
 */
fun Here.holds(facts: Map<String, Any?>): Boolean = holds(facts, held)

/** Facts as a map, from data the script already has: each value a value (equality) or a [Matcher]. */
fun Here.check(facts: Map<String, Any?>) = checkFacts(facts.map { (member, value) -> fact(member to value) })

private fun Here.checkFacts(facts: List<Fact>) {
    val step = ++run.step
    val tested = facts.map { fact ->
        val (present, value) = state.find(fact.member)
        Tested(fact, if (present) Values.shown(value) else "absent", fact.matcher.holds(present, value))
    }
    val record = tested.map {
        mapOf("member" to it.fact.member, "test" to it.fact.matcher.test, "held" to it.held, "holds" to it.holds)
    }
    val failed = tested.filterNot { it.holds }
    run.record(TraceStep.Asked("check", mapOf("facts" to record)), if (failed.isEmpty()) "held" else "failed", held)
    if (failed.isNotEmpty()) {
        val said = failed.joinToString("; ") {
            "${it.fact.member}: expected ${it.fact.matcher.expected}, was ${it.held}"
        }
        throw ScriptStopped(step, "\"check\"", "\"$said\"")
    }
}

private class Tested(val fact: Fact, val held: String, val holds: Boolean)

private fun fact(given: Any): Fact = when {
    given is Fact -> given
    given is Pair<*, *> && given.first is String ->
        Fact(given.first as String, given.second as? Matcher ?: equalTo(given.second))
    else -> throw IllegalArgumentException("a check takes facts, not $given")
}

/** How a check compares and shows values: numbers as numbers, arrays as their items. */
internal object Values {
    fun same(held: Any?, value: Any?): Boolean =
        if (held is Number && value is Number) decimal(held).compareTo(decimal(value)) == 0 else held == value

    fun compared(held: Any?, n: Number): Int? = (held as? Number)?.let { decimal(it).compareTo(decimal(n)) }

    fun plainOf(element: Any?): Any? = when {
        element is JsonPrimitive && element.isString -> element.content
        element is JsonPrimitive -> element.content.let { it.toLongOrNull() ?: it.toDoubleOrNull() ?: it }
        else -> element
    }

    /** A value as a failure shows it: an array as its items, `[herb, sunny]`. */
    fun shown(value: Any?): String = when (value) {
        is JsonArray -> value.joinToString(", ", "[", "]") { plainOf(it).toString() }
        is List<*> -> value.joinToString(", ", "[", "]")
        else -> value.toString()
    }

    private fun decimal(n: Number): BigDecimal = BigDecimal(n.toString())
}
