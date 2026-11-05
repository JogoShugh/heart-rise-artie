package heart.cli.generate

import heart.cli.BadArguments

/**
 * A value a generated script reads from the resource the step that brought it into the run led to, kept by name
 * ([keptAs]) in that step's block: `val number by kept("issue") { long("number") }`, with no var to fill.
 */
class Variable(
    val name: String,
    val step: Int,
    val pointer: String,
    val kind: Kind,
    val keptAs: String,
    /** Read from whichever member a loop is at, kept by the loop's name ([keptAs]), not by the step it was read at. */
    val looped: Boolean = false,
) {
    /** How a script reads it from the kept state: `long("number")`, by member name, or by pointer for nested state. */
    val read: String
        get() {
            val member = pointer.removePrefix("/").takeIf { '/' !in it }
                ?.replace("~1", "/")?.replace("~0", "~") ?: pointer
            return "${kind.read}(${KotlinText.quoted(member)})"
        }

    /** Its declaration: read by [read] from the resource kept as [keptAs]. */
    val declared: String
        get() = "val $name by kept(${KotlinText.quoted(keptAs)}) { $read }"

    /** How a script held as data reads it: the resource kept as [keptAs], then the member at [pointer]. */
    val reference: String
        get() = reference(
            keptAs + pointer.split('/').drop(1).joinToString("") { ".${it.replace("~1", "/").replace("~0", "~")}" },
        )

    enum class Kind(val read: String) {
        WHOLE("long"), DECIMAL("double"), TEXT("text"), BOOL("bool");

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
    /** The value as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md): each value read again a `${…}` reference. */
    val value: Any? = null,
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

    /** Each input the run sent as an integer, a number or a truth, with that type: a script as data declares it. */
    val inputTypes = linkedMapOf<String, String>()

    /** The steps that visited a loop's members, by the loop's name, while the steps inside the loop are written. */
    internal var scope: Map<Int, String> = emptyMap()

    /** The reference that reads the input [name], sent as a field of [type]; the type noted, if it isn't text. */
    private fun inputReference(name: String, type: String?): String {
        if (type in setOf("integer", "number", "boolean")) inputTypes[name] = checkNotNull(type)
        return reference(name)
    }

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
        // Inside a loop, a value its member gave is read from that member, whichever the loop is at.
        val held = sources.first(from, scope.keys)
        val marked = Marked(source, step, field, why)
        val data = data(value, type)
        return when {
            input != null ->
                Argument(field, marked.input(input, inputAs(input, type)), value = inputReference(input, type))
            field in asInputs -> Argument(
                field, marked.madeInput(field, text, inputAs(field, type)), value = inputReference(field, type),
            )
            // A goal run's step words are the planner's: what the person said is the goal, its "words" source.
            if (capture.source != null) "words" in from else literal(step, field, text) ->
                Argument(field, marked.words(text, constant(value, type)), value = data)
            held != null -> variable(held.first, held.second).let { variable ->
                val tie = tie(from, held)
                val named = slotted[held] ?: variable.name
                val expression = marked.answer(variable, named, tie, variableAs(variable, type))
                Argument(field, expression, tie, mapOf(text to named), variable.reference)
            }
            "chosen" in from -> Argument(
                field,
                marked.model(constant(value, type)),
                "the reasoner chose it, asked \"${step.say}\"",
                value = data,
            )
            value is String -> names(step, text, from).let {
                val written = structured(text, type, it) ?: KotlinText.templated(text, it)
                val read = filled(text, type, it) { name -> referenceOf(name) }
                Argument(field, marked.string(written, it.values, from), named = it, value = read)
            }
            else -> Argument(field, marked.said(constant(value, type), from), value = data)
        }
    }

    /**
     * [step] kept as the reason step it was (`--keep-reason`), its words as the script wrote them: each slot an
     * earlier answer filled is a variable the script reads, as the source script did.
     */
    fun kept(step: Step): String {
        slotsRead(capture, step)
        val words = reasonedWords(capture, step)
        val call = Written(source, step).find(KEPT)
        return why.mark(why.kept(step), "act(intent = ${heart.script.kotlinString(words)})", listOf(call))
    }

    /**
     * The variable read from [pointer] in the answer of [step], made the first time it is needed; one for every member
     * of a loop, read from whichever member the loop is at.
     */
    fun variable(step: Int, pointer: String): Variable = scope[step]?.let { loop ->
        variables.values.firstOrNull { it.looped && it.keptAs == loop && it.pointer == pointer }
    } ?: variables.getOrPut(step to pointer) {
        // A loop's member is kept by the loop's name; a goal step's name names what is read from where it led.
        val loop = scope[step]
        val mark = loop ?: capture.step(step)?.let { capture.marks(it)["as"] }?.toString()
        val name = named(step, pointer, mark, loop != null)
        // The resource is kept once, by the name its goal step gave it, else by its first value's name.
        val keptAs = mark ?: variables.values.firstOrNull { it.step == step }?.keptAs ?: name
        Variable(name, step, pointer, Variable.Kind.of(capture.step(step)?.read(pointer)), keptAs, loop != null)
    }

    /**
     * The name of the variable read from [pointer] at [step]: its slot's, where a reason step named it and it is read
     * at that step alone; else [mark], the name of what it is read from, then its member's (issue's number is
     * issueNumber); else its member's name.
     */
    private fun named(step: Int, pointer: String, mark: String?, looped: Boolean): String {
        val taken = variables.values.map { it.name }.toSet() + capture.inputs.keys
        // A member named as no identifier can be (an array's index, `/topics/0`) is named with its parent's name.
        val last = camel(pointer.substringAfterLast('/'))
        val named = last.takeIf { IDENTIFIER.matches(it) } ?: camel(pointer.removePrefix("/").replace('/', '_'))
        val given = mark?.let { "$it${named.replaceFirstChar(Char::uppercase)}" }
        val base = slotted[step to pointer]?.takeUnless { looped } ?: given ?: named
        return if (base in taken) "$base$step" else base
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

    private fun constant(value: Any?, type: String?): String = when {
        value == null -> "null"
        value is Number || value is Boolean -> value.toString()
        value is String && type in setOf("integer", "number", "boolean") -> value
        value is String -> structured(value, type, emptyMap()) ?: KotlinText.quoted(value)
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

/**
 * The titles of the members of the loops [step] is inside, each with the variable that reads it from whichever member
 * its loop is at: a model's words name a member by its title (`renew this book (Matilda)`), a script by its loop. The
 * member a step is inside is its loop's last visit before it, as a run goes through one member at a time.
 */
internal fun Bindings.titles(capture: Capture, step: Step): Map<String, String> =
    scope.entries.filter { it.key < step.number }.groupBy({ it.value }, { it.key }).values.mapNotNull { visits ->
        val visit = capture.step(visits.max())
        val title = visit?.params?.get("title")?.toString()?.takeIf { it.isNotEmpty() }
        val member = visit?.state?.entries?.firstOrNull { title != null && it.value == title }?.key?.toString()
        if (visit == null || title == null || member == null) {
            null
        } else {
            title to variable(visit.number, "/" + member.replace("~", "~0").replace("/", "~1")).name
        }
    }.toMap()

/** [write], with each step of [visits] a member of the loop it names: a value read from it is read by that name. */
internal fun <T> Bindings.within(visits: Map<Int, String>, write: () -> T): T {
    val outer = scope
    scope = outer + visits
    try {
        return write()
    } finally {
        scope = outer
    }
}

/**
 * The words [step], kept as a reason step, says, with a script's slots: a goal step marked reasoned in the person's
 * words, each input in braces a slot; else the step's template, or what it said.
 */
internal fun reasonedWords(capture: Capture, step: Step): String =
    capture.marks(step)["reasoned"]?.toString()?.let { slotted(it, capture.inputs.keys) }
        ?: step.template ?: KotlinText.dollars(step.say)

/** [variable] as a field of [type] takes it. */
private fun variableAs(variable: Variable, type: String?): String = when {
    type == "string" && variable.kind != Variable.Kind.TEXT -> "\"$${variable.name}\""
    type == "integer" && variable.kind == Variable.Kind.TEXT -> "${variable.name}.toLong()"
    else -> variable.name
}

/** The input [name] as a field of [type] takes it. */
private fun inputAs(name: String, type: String?): String = when (type) {
    "integer" -> "$name.toLong()"
    "number" -> "$name.toDouble()"
    "boolean" -> "$name.toBoolean()"
    else -> name
}

/**
 * [text], the JSON a field of [type] array or object was sent as, as Kotlin: `listOf(…)`, `mapOf(…)`, each string
 * in it that is one of [names]' values that name, else a literal with them put back; null for any other type.
 */
private fun structured(text: String, type: String?, names: Map<String, String>): String? {
    if (type != "array" && type != "object") return null
    fun write(element: kotlinx.serialization.json.JsonElement): String = when (element) {
        is kotlinx.serialization.json.JsonArray -> element.joinToString(", ", "listOf(", ")") { write(it) }
        is kotlinx.serialization.json.JsonObject ->
            element.entries.joinToString(", ", "mapOf(", ")") { (k, v) -> "${KotlinText.quoted(k)} to ${write(v)}" }
        is kotlinx.serialization.json.JsonNull -> "null"
        is kotlinx.serialization.json.JsonPrimitive -> when {
            !element.isString -> element.content
            else -> names[element.content] ?: KotlinText.templated(element.content, names)
        }
    }
    return runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.getOrNull()?.let(::write)
}

/** A goal file's [words] as a literal's: `{name}` of one of [inputs] a slot, `{{` and `}}` a brace, `$` a dollar. */
private fun slotted(words: String, inputs: Set<String>): String =
    Regex("""\{\{|}}|\{([A-Za-z_][A-Za-z0-9_]*)}""").replace(KotlinText.dollars(words)) { found ->
        val name = found.groupValues[1]
        when {
            name.isEmpty() -> found.value.take(1)
            name in inputs -> "\${$name}"
            else -> found.value
        }
    }
