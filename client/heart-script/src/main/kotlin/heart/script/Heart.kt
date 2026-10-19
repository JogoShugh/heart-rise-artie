package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@DslMarker
annotation class HeartDsl

/**
 * A script's run against a HEART service, from its [entry]: the only address
 * it knows. The entry's scheme chooses the client's binding. The [block] is
 * the Reason step written in advance (spec/PLAN-ARTIE-SCRIPTS.md, "The DSL").
 */
fun heart(entry: String, block: Here.() -> Unit) {
    val run = Run.current
    if (run.listing) return
    run.checkInputs()
    val navigator = TracingNavigator(run.navigator(), run)
    val at = run.entry ?: entry
    val entered = arrived(0, at, navigator.enter(at))
    run.trace.lastOrNull()?.opens = true
    Here(navigator, run, entered).block()
}

/**
 * Where a script is: the one representation it holds. Every selection is made
 * on it; a block after a selection is somewhere else, and an inner block can't
 * select on an outer representation (@DslMarker), so a stale view is never used.
 */
@HeartDsl
class Here internal constructor(
    internal val navigator: Navigator,
    internal val run: Run,
    held: Representation,
) {
    /** The representation the script holds now. */
    var held: Representation = held
        internal set

    /** The state, read by JSON Pointer: a missing member is null, never an error. */
    val state: State get() = State(held.state)

    /** Whether the manifest offers the act [rel] now. */
    fun offers(rel: String): Boolean = rel in held.offered

    /** The titles of the links under [rel] (null for a link with none). */
    fun links(rel: String): List<String?> = held.links[rel].orEmpty()
    /** Why [rel] is not offered, as the server says; null when it says nothing. */
    fun why(rel: String): String? = navigator.why(rel)

    /** What [rel] means, from its profile. */
    fun describe(rel: String): String? = navigator.describe(rel)

    /** A remark, kept in the run's record. */
    fun note(text: String) {
        run.notes += text
    }

    /**
     * An assertion: when it fails, the script stops here, with [message]. In a script host with Power-assert
     * (heart-script-host), the message's first line is the script's own words, and the lines after it draw the
     * condition with the value of each part; the trace keeps that drawing.
     */
    fun check(condition: Boolean, message: () -> String = { "check failed" }) {
        val step = ++run.step
        if (condition) {
            run.record(TraceStep.Asked("check", emptyMap()), "held", held)
            return
        }
        val said = message().trimEnd().lines()
        val words = said.first().ifBlank { "check failed" }
        val drawing = said.drop(1).joinToString("\n")
        val params = if (drawing.isEmpty()) emptyMap() else mapOf("drawing" to drawing)
        run.record(TraceStep.Asked("check", params), "failed", held)
        throw ScriptStopped(step, "\"check\"", "\"$words\"")
    }

    /** Read the held resource again. */
    fun sync() {
        held = arrived(++run.step, "sync", navigator.visit(held.address))
    }

    /**
     * Select the form [rel] with [fields]. An act the manifest does not offer is refused here, by the
     * client, before anything is sent. With a [block], the script visits what the act led to, then
     * returns to where it was, read again. [intent] says, in words, what the act is for; the trace keeps
     * it. With no [rel], the words are handed to the client's reasoner, which chooses one selection from
     * what is offered here (a form with its fields, or a link); a run must allow it (`--reasoner`). A long act
     * gives its values in [fields], a block, one a line: `fields = { "title" to title }`, after any in [values].
     */
    fun act(
        rel: String? = null,
        vararg values: Pair<String, Any?>,
        intent: String? = null,
        fields: (Fields.() -> Unit)? = null,
        block: (Here.() -> Unit)? = null,
    ) {
        val step = ++run.step
        when {
            rel != null -> acted(step, rel, values.toMap() + Fields().also { fields?.invoke(it) }.values, intent, block)
            intent != null -> reasoned(step, intent, block)
            else -> throw ScriptStopped(step, "\"act\"", "\"act needs a relation, or an intent\"")
        }
    }


    /**
     * Select the link [rel] titled [title], or the resource embedded under [rel] with that title, which
     * fetches it; with a [block], visit it and return. [intent] says, in words, what the follow is for; the
     * trace keeps it.
     */
    fun follow(rel: String, title: String? = null, intent: String? = null, block: (Here.() -> Unit)? = null) {
        val step = ++run.step
        if (title !in links(rel) && title !in embedded(rel).map { it.title }) {
            val detail = "no \"$rel\" link" + (title?.let { " titled \"$it\"" } ?: "")
            val refusal = Answer.Refused(Refusal.NotOffered, Side.CLIENT, detail)
            run.record(followAsked(rel, title), "refused", held, refusal)
            run.trace.lastOrNull()?.intent = intent
            throw refused(step, "\"$rel\"", refusal)
        }
        val answer = navigator.follow(rel, title)
        run.trace.lastOrNull()?.intent = intent
        go(step, "\"$rel\"", answer, block)
    }

    internal fun go(step: Int, what: String, answer: Answer, block: (Here.() -> Unit)?) {
        val now = arrived(step, what, answer)
        if (block == null) {
            held = now
            return
        }
        val from = held.address
        // The step just traced is this selection's: it opens the block.
        run.trace.lastOrNull()?.opens = true
        Here(navigator, run, now).block()
        held = arrived(step, what, navigator.visit(from))
    }
}

/** What the client holds after [answer]; a refusal stops the script at [step]. */
internal fun arrived(step: Int, what: String, answer: Answer): Representation = when (answer) {
    is Answer.Done -> answer.now
    is Answer.NewResource -> answer.now
    is Answer.Refused -> throw refused(step, what, answer)
}

internal fun refused(step: Int, what: String, answer: Answer.Refused) =
    ScriptStopped(step, what, "${answer.kind.words}, refused by ${answer.by.words}: \"${answer.detail}\"", answer)

/**
 * A representation's state, read by member: a name is one member, as it is (`state_reason`, even `a/b`);
 * a leading slash makes it a JSON Pointer (RFC 6901), for nested state (`/head/sha`). A missing member is
 * null, never an error.
 */
class State internal constructor(private val root: JsonObject) {
    operator fun get(member: String): Any? = plain(node(member))

    /** Whether [member] is there at all, null or not, and its value. */
    internal fun find(member: String): Pair<Boolean, Any?> = node(member).let { (it != null) to plain(it) }

    private fun node(member: String): JsonElement? {
        if (!member.startsWith("/")) return root[member]
        val tokens = member.removePrefix("/").split('/').filter { it.isNotEmpty() }
            .map { it.replace("~1", "/").replace("~0", "~") }
        return tokens.fold(root as JsonElement?) { node, key -> child(node, key) }
    }

    private fun child(node: JsonElement?, key: String): JsonElement? = when (node) {
        is JsonObject -> node[key]
        is JsonArray -> key.toIntOrNull()?.let { node.getOrNull(it) }
        else -> null
    }

    private fun plain(node: JsonElement?): Any? = when (node) {
        null, is JsonNull -> null
        is JsonPrimitive -> if (node.isString) node.content else scalar(node)
        else -> node
    }

    private fun scalar(node: JsonPrimitive): Any? = node.booleanOrNull ?: node.longOrNull ?: node.doubleOrNull
}
