package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/*
 * The values of a decided watch, written as Kotlin (spec/PLAN-LISTENERS.md, slice 8): a choice as the act or follow
 * that makes it again, facts as a map, and each value bound to where it came from as the expression that reads it.
 */

/**
 * [chosen], made again: a follow of its link, or an act of its form with each value read again. An act keeps its
 * words, as the script says them ([said]: `intent = "…"`, or `inferred = "…", fromIntent = "…"`); a follow is a way
 * there, and keeps none.
 */
internal fun reach(chosen: Chosen, said: String? = null): String {
    val relation = kotlinString(chosen.relation, slots = false)
    if (chosen.kind == "link") {
        return "follow($relation${chosen.title?.let { ", ${kotlinString(it, slots = false)}" }.orEmpty()})"
    }
    val fields = chosen.values.entries.joinToString("") { (field, value) ->
        ", ${kotlinString(field, slots = false)} to ${expression(value)}"
    }
    return "act($relation$fields${said?.let { ", $it" }.orEmpty()})"
}

/** How many parts of a chain an `actOnce` is written for: a read, a check on it, and the act it guards. */
internal const val ONCE_PARTS = 3

/**
 * The parts of [parts] from [at] as one `actOnce`, when they are "say it, unless it's said already": a read that
 * opens nothing, a check that what it reached shows a text, and an act that opens nothing and sends that text, alone
 * or within its words. The act then checks with its own whole value, read again as the act sends it, so the two
 * can't drift apart: a check kept with the text one run saw (a wait's reason) would miss what the act says on
 * another run, and say it again. Null when they are anything else.
 */
internal fun saidOnce(parts: List<Chain.Part>, at: Int): String? {
    val read = parts.getOrNull(at) as? Chain.Act
    val check = parts.getOrNull(at + 1) as? Chain.Check
    val act = parts.getOrNull(at + 2) as? Chain.Act
    // The text a check looks for, written alone or as a list of one.
    val kept = check?.facts?.values?.singleOrNull()?.kept
    val said = kept as? String ?: (kept as? List<*>)?.singleOrNull() as? String
    // What the act sends holds the text, alone or within its words ("Not merged: <the reason>").
    val sends = act?.chosen?.values?.values.orEmpty().mapNotNull { it.kept as? String }
    val fits = read != null && read.chosen.kind == "link" && !read.opens && act != null && !act.opens &&
        act.chosen.kind != "link" && said != null && sends.any { said in it }
    return if (fits) {
        reach(act!!.chosen).replaceFirst("act(", "actOnce(").dropLast(1) +
            ", listedIn = ${kotlinString(read!!.chosen.relation, slots = false)}) {}"
    } else {
        null
    }
}

/**
 * Facts kept with each value bound to where it came from, as the body of a facts block: a list's items each a `has`,
 * any other value an `eq`, each read again where it came from.
 */
internal fun facts(facts: Map<String, Bound>): String = facts.entries.flatMap { (member, value) ->
    val name = kotlinString(member, slots = false)
    val listed = value.kept as? List<*>
    when {
        value.items != null -> value.items.map { "$name has ${expression(it)}" }
        listed != null -> listed.map { "$name has ${literal(it)}" }
        else -> listOf("$name eq ${expression(value)}")
    }
}.joinToString("; ")

/** [value], read again where it came from: an input by its name, the member's state, the reason a wait gave up. */
internal fun expression(value: Bound): String {
    val items = value.items
    val whole = value.whole
    val template = value.template
    return when {
        items != null -> "listOf(" + items.joinToString(", ") { expression(it) } + ")"
        whole != null -> source(whole) ?: literal(value.kept)
        template != null -> filled(template, value.keys) ?: literal(value.kept)
        else -> literal(value.kept)
    }
}

/** The expression that reads [key], a source of values; null for one a script can't read again. */
private fun source(key: String): String? = when {
    key.startsWith("input:") -> key.removePrefix("input:")
    key.startsWith("member:") -> "state[${kotlinString(key.removePrefix("member:"), slots = false)}]"
    key == "why" -> "why"
    else -> null
}

/** [template] as a Kotlin string, each `{n}` the nth of [keys] read again; null when one can't be. */
private fun filled(template: String, keys: List<String>): String? {
    val reads = keys.map { source(it) ?: return null }
    val text = template.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
    return "\"" + reads.foldIndexed(text) { at, said, read -> said.replace("{$at}", "\${$read}") } + "\""
}

/** [value] as a Kotlin literal: text, a number, a truth, a list, or null. */
internal fun literal(value: Any?): String = when (value) {
    null -> "null"
    is JsonPrimitive -> if (value.isString) kotlinString(value.content, slots = false) else value.content
    is JsonArray -> "listOf(" + value.joinToString(", ") { literal(it) } + ")"
    is List<*> -> "listOf(" + value.joinToString(", ") { literal(it) } + ")"
    is Number, is Boolean -> value.toString()
    else -> kotlinString(value.toString(), slots = false)
}

/** [duration] as Kotlin: whole minutes as minutes, else seconds. */
internal fun duration(duration: Duration): String =
    if (duration.inWholeSeconds % SECONDS_A_MINUTE == 0L) {
        "${duration.inWholeMinutes}.minutes"
    } else {
        "${duration.inWholeSeconds}.seconds"
    }

private const val SECONDS_A_MINUTE = 60L
