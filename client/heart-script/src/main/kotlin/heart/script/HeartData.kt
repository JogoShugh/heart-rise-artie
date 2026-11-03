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
        run.declare(
            Declared(
                name.toString(),
                default = (default as? String) ?: default?.takeIf { it !is Map<*, *> }?.toString(),
                question = declared?.get("question")?.toString(),
                now = (default as? Map<*, *>)?.get("now")?.toString(),
            ),
        )
    }
    val steps = script["steps"] as? List<*> ?: emptyList<Any?>()
    val start = script["start"]?.toString() ?: throw ScriptStopped(0, "", "the script names no start")
    heart(Reading(run, null).text(start)) { made(steps) }
}

/** [steps], made in turn where the script stands. */
internal fun Here.made(steps: List<*>) = steps.forEach { made(it as? Map<*, *> ?: stop("a step is not a map: $it")) }

private fun Here.made(step: Map<*, *>) {
    val reading = Reading(run, this)
    val then: (Here.() -> Unit)? = (step["then"] as? List<*>)?.let { inner -> { made(inner) } }
    when {
        "act" in step -> {
            only(step, ACT)
            // Read where the script stands, before the act, as a Kotlin script's fields block is.
            val values = (step["fields"] as? Map<*, *>).orEmpty().map { (k, v) -> k.toString() to reading.value(v) }
            val words = said(step, reading)
            act(
                step["act"].toString(),
                intent = words.intent, inferred = words.inferred, fromIntent = words.fromIntent,
                fields = { values.forEach { (name, value) -> name to value } },
                block = then,
            )
        }
        "keep-as" in step -> {
            only(step, setOf("keep-as"))
            keepAs(step["keep-as"].toString())
        }
        else -> stop("a step this run can't make yet: ${step.keys.joinToString(", ")}")
    }
}

/** The words of [step], each reference read. */
private fun said(step: Map<*, *>, reading: Reading) = Said(
    intent = step["intent"]?.let { reading.text(it.toString()) },
    inferred = step["inferred"]?.let { reading.text(it.toString()) },
    fromIntent = step["from-intent"]?.let { reading.text(it.toString()) },
)

/** Stops the run at a step it can't make, before anything is sent for it. */
private fun stop(why: String): Nothing = throw ScriptStopped(0, "", why)

/** A step's keys must be [keys]'s: anything else is outside the vocabulary. */
private fun only(step: Map<*, *>, keys: Set<String>) {
    val outside = step.keys.map { it.toString() }.filter { it !in keys }
    if (outside.isNotEmpty()) stop("not in a script's vocabulary: ${outside.joinToString(", ")}")
}

private val ACT = setOf("act", "fields", "intent", "inferred", "from-intent", "then")

/**
 * The references in a script's values, read where it stands ([here]): an input by its name; a resource kept by its
 * name, then the members under it; `state`, the state held; `why`, the reason a wait gave up ([why]).
 */
internal class Reading(private val run: Run, private val here: Here?, private val why: String? = null) {
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
            run.kept[name] != null -> run.kept.getValue(name).state[pointer(members)]
            members.isEmpty() && run.declared.any { it.name == name } -> run.value(name)
            else -> stop("\${$path} reads nothing: no input, and nothing kept, is named $name")
        }
    }

    private fun pointer(members: List<String>): String =
        "/" + members.joinToString("/") { it.replace("~", "~0").replace("/", "~1") }

    private companion object {
        val REFERENCE = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_:-]+)*)}""")
    }
}
