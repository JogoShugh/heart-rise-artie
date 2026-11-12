package heart.script

/*
 * A generated script held as data, run (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 4): its inputs declared as a Kotlin
 * script declares them, then each step made through the same functions a Kotlin script calls, so the two behave
 * alike. A step's keys are the vocabulary of spec/schemas/heart-script.draft.schema.json, and only those: a key
 * outside them stops the run before it is made. A value read again is a reference, `${title}` an input,
 * `${issue.number}` a member of a resource kept, `${state.title}` one of the state held; it is looked up by its
 * name, never evaluated.
 */

/** Runs [script], a script held as data, in the current run. */
fun runData(script: Map<*, *>) {
    val run = Run.current
    (script["inputs"] as? Map<*, *>).orEmpty().forEach { (name, input) ->
        val declared = input as? Map<*, *>
        val default = declared?.get("default")
        val now = (default as? Map<*, *>)?.get("now")?.toString()
        run.declare(
            Declared(
                name.toString(),
                // A default { now: PATTERN } is the time now in that pattern, as a generated Kotlin script's is.
                default = now?.let(::timeNow) ?: default?.takeIf { it !is Map<*, *> }?.toString(),
                question = declared?.get("question")?.toString(),
                now = now,
                type = declared?.get("type")?.toString(),
            ),
        )
    }
    val steps = script["steps"] as? List<*> ?: emptyList<Any?>()
    val start = script["start"]?.toString() ?: throw ScriptStopped(0, "", "the script names no start")
    heart(Reading(run, null).text(start)) { made(steps, null) }
}

/** The time now, as [pattern] writes it (java.time's DateTimeFormatter). */
private fun timeNow(pattern: String): String =
    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(pattern))

/** [steps], made in turn where the script stands; [why], the reason a wait gave up, in an Otherwise's steps. */
internal fun Here.made(steps: List<*>, why: String?) =
    steps.forEach { made(it as? Map<*, *> ?: stop("a step is not a map: $it"), why) }

/** [step], made by the maker its key names (DataSteps.kt), once its keys are all that maker's. */
private fun Here.made(step: Map<*, *>, why: String?) {
    val key = MAKERS.keys.firstOrNull { it in step }
    val maker = key?.let(MAKERS::getValue)
        ?: REASONED.takeIf { "intent" in step || "inferred" in step }
        ?: stop("not a step a script can make: ${step.keys.joinToString(", ")}")
    val outside = step.keys.map { it.toString() }.filter { it !in maker.keys }
    if (outside.isNotEmpty()) stop("not in a script's vocabulary: ${outside.joinToString(", ")}")
    maker.make(this, step, Reading(run, this, why))
}

/** A step's maker (DataSteps.kt): the keys it may hold, and what it does where the script stands. */
internal class Maker(val keys: Set<String>, val make: Here.(Map<*, *>, Reading) -> Unit)

/** The words of [step], each reference read. */
internal fun said(step: Map<*, *>, reading: Reading) = Said(
    intent = step["intent"]?.let { reading.text(it.toString()) },
    inferred = step["inferred"]?.let { reading.text(it.toString()) },
    fromIntent = step["from-intent"]?.let { reading.text(it.toString()) },
)

/** Stops the run at a step it can't make, before anything is sent for it. */
internal fun stop(why: String): Nothing = throw ScriptStopped(0, "", why)

/**
 * The references in a script's values, read where it stands ([here]): an input by its name; a resource kept by its
 * name, then the members under it; `state`, the state held; `why`, the reason a wait gave up ([why]).
 */
internal class Reading(private val run: Run, private val here: Here?, val why: String? = null) {
    /** The same references, read where [there] stands: a fact read on a resource kept, or on a member. */
    fun at(there: Here) = Reading(run, there, why)

    /** [value] with each reference read: a value that is one reference alone, the value it reads. */
    fun value(value: Any?): Any? = when (value) {
        is String -> REFERENCE.matchEntire(value)?.let { read(it.groupValues[1]) } ?: text(value)
        is List<*> -> value.map(::value)
        is Map<*, *> -> value.mapValues { value(it.value) }
        else -> value
    }

    /** [text] with each reference put as the text of what it reads. */
    fun text(text: String): String = REFERENCE.replace(text) { read(it.groupValues[1])?.toString().orEmpty() }

    private fun read(path: String): Any? {
        val parts = path.split('.')
        val name = parts.first()
        val members = parts.drop(1)
        return when {
            name == "why" && members.isEmpty() -> why ?: NO_REASON
            name == "state" && here != null -> here.state[pointer(members)]
            // `${name.@self}`: the address the resource kept as name was kept at, as the server handed it over.
            run.kept[name] != null && members == listOf(SELF) -> run.kept.getValue(name).address
            run.kept[name] != null -> run.kept.getValue(name).state[pointer(members)]
            members.isEmpty() && run.declared.any { it.name == name } -> typed(name)
            else -> stop("\${$path} reads nothing: no input, and nothing kept, is named $name")
        }
    }

    /** The input [name], read as the type it declares: text that isn't one stays text, for the form to refuse. */
    private fun typed(name: String): Any {
        val value = run.value(name)
        return when (run.declared.first { it.name == name }.type) {
            "integer" -> value.toLongOrNull()
            "number" -> value.toDoubleOrNull()
            "boolean" -> value.toBooleanStrictOrNull()
            else -> null
        } ?: value
    }

    private fun pointer(members: List<String>): String =
        "/" + members.joinToString("/") { it.replace("~", "~0").replace("/", "~1") }

    private companion object {
        val REFERENCE = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*(?:\.@?[A-Za-z0-9_:-]+)*)}""")
        const val SELF = "@self"
    }
}
