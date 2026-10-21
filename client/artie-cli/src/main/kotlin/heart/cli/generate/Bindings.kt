package heart.cli.generate

import heart.cli.BadArguments

/** A variable a generated script reads in the block of the step that brought its value into the run. */
class Variable(val name: String, val step: Int, val pointer: String, val kind: Kind) {
    /** How a script reads it: `state.long("number")`, by member name, or by pointer for nested state. */
    val read: String
        get() {
            val member = pointer.removePrefix("/").takeIf { '/' !in it }
                ?.replace("~1", "/")?.replace("~0", "~") ?: pointer
            return "state.${kind.read}(${KotlinText.quoted(member)})"
        }

    enum class Kind(val read: String, val initial: String) {
        WHOLE("long", "0L"), DECIMAL("double", "0.0"), TEXT("text", "\"\""), BOOL("bool", "false");

        companion object {
            fun of(value: Any?): Kind = when (value) {
                is Int, is Long -> WHOLE
                is Number -> DECIMAL
                is Boolean -> BOOL
                else -> TEXT
            }
        }
    }
}

/** One value a selection sends, as the script writes it, and why a person should look at it, if they should. */
class Argument(
    val field: String,
    val expression: String,
    val review: String? = null,
    /** Each text in the value an input or an earlier answer gave, with the name the script knows it by. */
    val named: Map<String, String> = emptyMap(),
)

/**
 * Each value's binding (spec/PLAN-SCRIPT-GENERATOR.md, "The rules"), in this order: an input with that value; text
 * the script wrote literally; a member of the answer of the step that brought it into the run, as a variable; the
 * words, as a constant. A value inside a string puts each slot and input back as its name. A value the reasoner
 * chose, which the words don't give, is the constant it was, marked for review.
 */
class Bindings(
    private val capture: Capture,
    private val source: List<String>?,
    /** The fields whose text is an input of the same name (`--inputs`). */
    private val asInputs: Set<String> = emptySet(),
    /** Where each binding is noted, as the script is written. */
    private val derivations: Derivations = Derivations(source),
) {
    private val why = Why(derivations)

    /** The variables, by the step and pointer they are read from, in the order they were first needed. */
    val variables = linkedMapOf<Pair<Int, String>, Variable>()

    private val sources = Sources(capture)

    /** The variable each slot names, a reason step's or a scripted step's, by the step and pointer it came from. */
    private val slotted: Map<Pair<Int, String>, String> = capture.steps.flatMap { step ->
        Written.slots(source, step).entries.mapNotNull { (name, slot) ->
            val from = sources.first(slot.map()["from"])
            if (from == null || !IDENTIFIER.matches(name.toString())) null else from to name.toString()
        }
    }.distinctBy { it.first }.toMap()

    /** What [step] sends, field by field. */
    fun arguments(step: Step): List<Argument> = step.params.entries
        .filter { it.key != "relation" && !(step.follows && it.key == "title") }
        .map { (field, value) -> argument(step, field.toString(), value) }

    private fun argument(step: Step, field: String, value: Any?): Argument {
        val info = step.values[field].map()
        val type = info["type"]?.toString()
        val from = info["from"] as? List<*> ?: emptyList<Any?>()
        val text = value?.toString().orEmpty()
        val input = from.firstNotNullOfOrNull { it.map()["input"]?.toString() }
        val held = sources.first(from)
        val marked = Marked(source, step, field, why)
        return when {
            input != null -> Argument(field, marked.input(input, inputAs(input, type)))
            field in asInputs -> Argument(field, marked.madeInput(field, text, inputAs(field, type)))
            // A goal run's step words are the planner's: what the person said is the goal, its "words" source.
            if (capture.source != null) "words" in from else literal(step, field, text) ->
                Argument(field, marked.words(text, constant(value, type)))
            held != null -> variable(held.first, held.second).let { variable ->
                val tie = tie(from, held)
                val named = slotted[held] ?: variable.name
                val expression = marked.answer(variable, named, tie, variableAs(variable, type))
                Argument(field, expression, tie, mapOf(text to named))
            }
            "chosen" in from ->
                Argument(field, marked.model(constant(value, type)), "the reasoner chose it, asked \"${step.say}\"")
            value is String -> names(step, text, from)
                .let { Argument(field, marked.string(KotlinText.templated(text, it), it.values, from), named = it) }
            else -> Argument(field, marked.said(constant(value, type), from))
        }
    }

    /**
     * [step] kept as the reason step it was (`--keep-reason`), its words as the script wrote them: each slot an
     * earlier answer filled is a variable the script reads, as the source script did.
     */
    fun kept(step: Step): String {
        step.slots.values.map { it.map()["from"] }
            .filter { from -> from.list().none { it.map()["input"] != null } }
            .forEach { from -> sources.first(from)?.let { variable(it.first, it.second) } }
        val words = step.template ?: KotlinText.dollars(step.say)
        val call = Written(source, step).find(KEPT)
        return why.mark(why.kept(step), "act(intent = ${heart.script.kotlinString(words)})", listOf(call))
    }

    /** The variable read from [pointer] in the answer of [step], made the first time it is needed. */
    fun variable(step: Int, pointer: String): Variable = variables.getOrPut(step to pointer) {
        val taken = variables.values.map { it.name }.toSet() + capture.inputs.keys
        // A member named as no identifier can be (an array's index, `/topics/0`) is named with its parent's name.
        val last = camel(pointer.substringAfterLast('/'))
        val named = last.takeIf { IDENTIFIER.matches(it) } ?: camel(pointer.removePrefix("/").replace('/', '_'))
        val base = slotted[step to pointer] ?: named
        val name = if (base in taken) "$base$step" else base
        Variable(name, step, pointer, Variable.Kind.of(capture.step(step)?.read(pointer)))
    }

    /** Rule 2.2: text the script wrote literally, outside any slot of its reason template, or in its own line. */
    private fun literal(step: Step, field: String, text: String): Boolean {
        val words = step.template?.let(KotlinText::plain)
        val written = Written(source, step).takeIf { step.mode == "script" }?.expression(field)
        return when {
            text.isEmpty() -> false
            words != null -> KotlinText.whole(text).containsMatchIn(words)
            written == null -> false
            // A string with no slot in it, as the script wrote it; or a number or a truth, written as it is.
            written.startsWith("\"") -> KotlinText.match(written, text)?.isEmpty() == true
            else -> written == text
        }
    }

    /** Each value a string may hold that the script has a name for: its slots, and the parts the capture names. */
    private fun names(step: Step, text: String, from: List<*>): Map<String, String> {
        val names = linkedMapOf<String, String>()
        Written.slots(source, step).forEach { (name, slot) ->
            // A slot whose value the text doesn't hold names nothing here, and makes no variable.
            val value = slot.map()["value"]?.toString()?.takeIf { KotlinText.whole(it).containsMatchIn(text) }
                ?: return@forEach
            val input = slot.map()["from"].list().firstNotNullOfOrNull { it.map()["input"]?.toString() }
            val held = sources.first(slot.map()["from"])?.takeIf { IDENTIFIER.matches(name.toString()) }
            when {
                input != null -> names[value] = input
                held != null -> names[value] = variable(held.first, held.second).name
            }
        }
        from.flatMap { it.map()["parts"].map().entries }.forEach { (part, source) ->
            source.map()["input"]?.let { names.putIfAbsent(part.toString(), it.toString()) }
            // A goal run has no script of its own to have named it: an earlier answer's part is its variable.
            sources.first(listOf(source))?.takeIf { it in slotted || capture.source != null }
                ?.let { names.putIfAbsent(part.toString(), variable(it.first, it.second).name) }
        }
        return names
    }

    /** Rule 5: the step that brought the value in holds it at another member too; the first is taken. */
    private fun tie(from: List<*>, held: Pair<Int, String>): String? {
        val others = sources.usable(from).filter { it.first == held.first && it != held }.map { it.second }
        return others.takeIf { it.isNotEmpty() }
            ?.let { "step ${held.first} holds it at ${held.second}, and at ${it.joinToString(", ")} too" }
    }

    private fun inputAs(name: String, type: String?): String = when (type) {
        "integer" -> "$name.toLong()"
        "number" -> "$name.toDouble()"
        "boolean" -> "$name.toBoolean()"
        else -> name
    }

    private fun variableAs(variable: Variable, type: String?): String = when {
        type == "string" && variable.kind != Variable.Kind.TEXT -> "\"$${variable.name}\""
        type == "integer" && variable.kind == Variable.Kind.TEXT -> "${variable.name}.toLong()"
        else -> variable.name
    }

    private fun constant(value: Any?, type: String?): String = when {
        value == null -> "null"
        value is Number || value is Boolean -> value.toString()
        value is String && type in setOf("integer", "number", "boolean") -> value
        value is String -> KotlinText.quoted(value)
        else -> throw BadArguments("can't write a value that is a ${value::class.simpleName} yet: $value")
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** A kept reason step's call as the script wrote it, its words a raw string or a plain one. */
        val KEPT = Regex("act\\(intent = (?:\"\"\".*?\"\"\"(?!\")|\"(?:\\\\.|[^\"\\\\])*\")\\)")

        fun camel(member: String): String = member.split('_', '-').filter { it.isNotEmpty() }
            .mapIndexed { i, word -> if (i == 0) word else word.replaceFirstChar(Char::uppercase) }
            .joinToString("").ifEmpty { "value" }
    }
}

internal fun Any?.list(): List<*> = this as? List<*> ?: emptyList<Any?>()
