package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/*
 * What a watch keeps of a decision, to make it again with no model (spec/PLAN-WATCH-GOALS.md): the reasoner's
 * choice, each value bound to where it came from, and the check a resource must pass.
 */

/**
 * Whether [held] passes every one of [facts]. A fact may name a relation [held] embeds: its value is then the names
 * of what is embedded there (each one's `name`, else its title). A list as a fact's value holds when every item is
 * among the member's values; any other value, when it equals the member's. A fact may name a relation [held] links
 * to, neither a member nor embedded: it holds when, for each of its values, a link's title says it (a comment that
 * says the reason, among the comments listed).
 */
internal fun holds(facts: Map<String, Any?>, held: Representation): Boolean = facts.all { (member, wanted) ->
    val titles = held.links[member]?.takeIf { member !in held.state && member !in held.embedded }
    val have: List<Any?>? = held.embedded[member]?.map { it.read["name"] ?: it.title }
        ?: (State(held.state)[member] as? JsonArray)?.map(Values::plainOf)
    when {
        titles != null -> (wanted as? List<*> ?: listOf(wanted)).all { item ->
            titles.any { it != null && item.toString() in it }
        }
        wanted is List<*> -> have != null && wanted.all { item -> have.any { Values.same(it, item) } }
        else -> Values.same(State(held.state)[member], wanted)
    }
}

/**
 * A step's acts, decided as a chain (spec/PLAN-LISTENERS.md, slice 0): "say why, unless it is said already" is a read,
 * a check and a write. Each [parts] in order; an act that opens leads to the parts after it; a check leaves the parts
 * after it undone where it holds. [complete] once the planner said done; until then, the chain is decided further at
 * the next resource that gets past its end.
 */
internal class Chain {
    val parts = mutableListOf<Part>()
    var complete = false

    sealed interface Part

    /** An act, made again as [chosen]; when it [opens], the parts after it are done on what it reached. */
    class Act(val chosen: Chosen, val opens: Boolean) : Part

    /** A check on what the act before it reached, each value bound to where it came from. */
    class Check(val facts: Map<String, Bound>) : Part {
        /** Whether it holds on [reached], each value read again from [sources]. */
        fun passes(sources: Map<String, Any?>, reached: Representation): Boolean =
            holds(facts.mapValues { (_, bound) -> bound.filled(sources) }, reached)
    }
}

/** [chosen], made again here, each value read again from [sources]; [block] where it led. */
internal fun Here.replay(chosen: Chosen, sources: Map<String, Any?>, block: Here.() -> Unit) {
    if (chosen.kind == "link") {
        follow(chosen.relation, chosen.title, block = block)
    } else {
        act(chosen.relation, fields = {
            chosen.values.forEach { (field, bound) -> field to bound.filled(sources) }
        }, block = block)
    }
}

/** A reasoner's choice, kept to be made again: the link or form, and each value bound to where it came from. */
internal class Chosen(val kind: String, val relation: String, val title: String?, val values: Map<String, Bound>) {
    companion object {
        /** The choice a reason step [taken] made, its values bound to [sources]; null when it chose nothing. */
        fun of(taken: TraceStep, sources: Map<String, Any?>): Chosen? {
            val params = taken.asked.params
            return (params["relation"] as? String)?.let { relation ->
                val values = params.filterKeys { it != "relation" && it != "title" }
                    .mapValues { (_, value) -> Bound.of(value, sources) }
                Chosen(taken.used?.kind ?: "form", relation, params["title"] as? String, values)
            }
        }
    }
}

/**
 * A value bound to where it came from: a [whole] source, read again as it is; text with sources in it, [template],
 * each `{n}` the nth of [keys]; or, from nowhere, the value [kept] as it was.
 */
internal class Bound(val whole: String?, val template: String?, val keys: List<String>, val kept: Any?) {
    /** The value now, from [sources] as they are now. */
    fun filled(sources: Map<String, Any?>): Any? = when {
        whole != null -> sources[whole]
        template != null -> keys.foldIndexed(template) { i, text, key -> text.replace("{$i}", sources[key].toString()) }
        else -> kept
    }

    companion object {
        /** [value] bound to the first of [sources] it equals; else to those it contains, longest first. */
        fun of(value: Any?, sources: Map<String, Any?>): Bound {
            val text = (value as? JsonPrimitive)?.content ?: value as? String ?: (value as? Number)?.toString()
            val whole = text?.let { t -> sources.entries.firstOrNull { it.value?.toString() == t }?.key }
            return when {
                text == null -> Bound(null, null, emptyList(), value)
                whole != null -> Bound(whole, null, emptyList(), value)
                else -> parts(text, value, sources)
            }
        }

        private fun parts(text: String, value: Any?, sources: Map<String, Any?>): Bound {
            val parts = sources.entries.filter { (_, v) -> v != null && v.toString().length > 1 }
                .sortedByDescending { it.value.toString().length }
            var template = text
            val keys = mutableListOf<String>()
            parts.forEach { (key, v) ->
                val found = Words.whole(v.toString())
                if (found.containsMatchIn(template)) {
                    template = found.replace(template, "{${keys.size}}")
                    keys += key
                }
            }
            return Bound(null, template.takeIf { keys.isNotEmpty() }, keys, value)
        }

        /**
         * Where a value can come from, in the order it is looked for: the run's inputs, the [member]'s state, each
         * resource a step [named], and the server's reason a wait gave up ([why]).
         */
        fun sources(
            member: Representation,
            named: Map<String, Representation>,
            run: Run,
            why: String?,
        ): Map<String, Any?> = buildMap {
            run.inputsUsed.forEach { (name, used) -> put("input:$name", used.first) }
            scalars(member).forEach { (pointer, value) -> put("member:$pointer", value) }
            named.forEach { (name, held) -> scalars(held).forEach { (pointer, v) -> put("named:$name:$pointer", v) } }
            why?.let { put("why", it) }
        }

        private fun scalars(held: Representation): List<Pair<String, Any?>> = held.state.entries
            .filter { it.value is JsonPrimitive }.map { (name, value) -> "/$name" to Values.plainOf(value) }
    }
}
