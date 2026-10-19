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
class Argument(val field: String, val expression: String, val review: String? = null)

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
) {
    /** The variables, by the step and pointer they are read from, in the order they were first needed. */
    val variables = linkedMapOf<Pair<Int, String>, Variable>()

    private val sources = Sources(capture)

    /** The variable each reason slot names, by the step and pointer it came from. */
    private val slotted: Map<Pair<Int, String>, String> = capture.steps.flatMap { step ->
        step.slots.entries.mapNotNull { (name, slot) ->
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
        return when {
            input != null -> Argument(field, inputAs(input, type))
            field in asInputs -> Argument(field, inputAs(field, type))
            literal(step, text, value) -> Argument(field, constant(value, type))
            held != null -> Argument(field, variableAs(variable(held.first, held.second), type), tie(from, held))
            "chosen" in from -> Argument(field, constant(value, type), "the reasoner chose it, asked \"${step.say}\"")
            value is String -> Argument(field, KotlinText.templated(text, names(step, text, from)))
            else -> Argument(field, constant(value, type))
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
        val words = step.template ?: KotlinText.quoted(step.say).removeSurrounding("\"")
        return "reason(\"$words\")"
    }

    /** The variable read from [pointer] in the answer of [step], made the first time it is needed. */
    fun variable(step: Int, pointer: String): Variable = variables.getOrPut(step to pointer) {
        val taken = variables.values.map { it.name }.toSet() + capture.inputs.keys
        val base = slotted[step to pointer] ?: camel(pointer.substringAfterLast('/'))
        val name = if (base in taken) "$base$step" else base
        Variable(name, step, pointer, Variable.Kind.of(capture.step(step)?.read(pointer)))
    }

    /** Rule 2.2: text the script wrote literally, outside any slot of its reason template, or in its own line. */
    private fun literal(step: Step, text: String, value: Any?): Boolean {
        val words = step.template?.let(KotlinText::plain)
        val written = step.line?.let { source?.getOrNull(it - 1) }.orEmpty()
        return when {
            text.isEmpty() -> false
            words != null -> KotlinText.whole(text).containsMatchIn(words)
            value is String -> KotlinText.quoted(text) in written
            else -> Regex("""\bto\s+${Regex.escape(text)}\b""").containsMatchIn(written)
        }
    }

    /** Each value a string may hold that the script has a name for: its slots, and the parts the capture names. */
    private fun names(step: Step, text: String, from: List<*>): Map<String, String> {
        val names = linkedMapOf<String, String>()
        step.slots.forEach { (name, slot) ->
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
            sources.first(listOf(source))?.takeIf { it in slotted }
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

        fun camel(member: String): String = member.split('_', '-').filter { it.isNotEmpty() }
            .mapIndexed { i, word -> if (i == 0) word else word.replaceFirstChar(Char::uppercase) }
            .joinToString("").ifEmpty { "value" }
    }
}

internal fun Any?.list(): List<*> = this as? List<*> ?: emptyList<Any?>()
