package heart.script

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** Where a script stopped, and why; [step] 0 means before its first request. Add-ons stop scripts their own ways. */
open class ScriptStopped(
    val step: Int,
    val what: String,
    val reason: String,
    /** The refusal that stopped it, when HEART refused a step. */
    val refused: Answer.Refused? = null,
) : RuntimeException(
    if (step == 0) "stopped before the first request: $reason" else "stopped at step $step, $what: $reason",
)

/** One input a script declares: [default] when not given; [question] asked of a person, if the run allows. */
class Declared(val name: String, val default: String?, val question: String?) {
    val kind: String get() = when {
        default != null -> "optional"
        question != null -> "question"
        else -> "required"
    }
}

/**
 * The run a script is part of: set by whatever runs it (the `artie run`
 * command, a test), read by the DSL. [interactive] is false unless the run
 * says otherwise: then nothing ever waits on a person.
 */
class Run(
    val inputs: Map<String, String> = emptyMap(),
    val interactive: Boolean = false,
    /** Asks a person [question], showing [default]; only ever called when [interactive]. */
    val prompt: (question: String, default: String?) -> String = { q, _ -> error("no one to ask: $q") },
    /** True to list the script's inputs without running it. */
    val listing: Boolean = false,
    val navigator: () -> Navigator = NavigatorProvider::load,
    /** How the run waits between tries of `until`; a test can make it instant. */
    val pause: (kotlin.time.Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
) {
    /** Another entry than the script's: an environment, another binding. Set before the run. */
    var entry: String? = null

    /** Whether the run allows `reason(…)` steps: off unless the runner says so (`--reasoner`). */
    var reasoning = false

    /** Told of each step as it is traced: a runner prints it. */
    var onStep: (TraceStep) -> Unit = {}

    val declared = mutableListOf<Declared>()
    val notes = mutableListOf<String>()

    /** Questions answered by their defaults, unattended: the run report says so. */
    val defaultsUsed = mutableListOf<Pair<String, String>>()

    /** Every step of the run, in order, for its run report. */
    val trace = mutableListOf<TraceStep>()

    internal fun traced(step: TraceStep) {
        trace += step
        onStep(step)
    }

    /** The trace as heart-run-report reads it: `{"steps": [...]}`. */
    fun traceJson(): String = kotlinx.serialization.json.JsonObject(
        mapOf("steps" to kotlinx.serialization.json.JsonArray(trace.map { it.json() })),
    ).toString()
    private val values = mutableMapOf<String, String>()

    /** The number of the script's last step: each act, follow, sync, check or flow is one. */
    var step = 0
        internal set

    internal fun declare(input: Declared) {
        declared += input
    }

    /**
     * Before the first request: every required input given (asked now if the run is interactive), and every
     * question without a default given, unless the run can ask it later.
     */
    internal fun checkInputs() {
        declared.filter { it.kind == "required" && it.name !in inputs }.forEach { input ->
            if (!interactive) throw ScriptStopped(0, "", "missing input: ${input.name}")
            values[input.name] = prompt(input.name, null)
        }
        declared.firstOrNull { it.kind == "question" && it.name !in inputs && !interactive }?.let {
            throw ScriptStopped(0, "", "${it.name} needs --interactive or a value")
        }
    }

    /** The input [name]'s value: given, asked before the run, its default, or asked when first read. */
    internal fun value(name: String): String = values.getOrPut(name) {
        val input = declared.first { it.name == name }
        inputs[name] ?: input.default ?: prompt(checkNotNull(input.question), null)
    }

    internal fun ask(question: String, default: String): String =
        if (interactive) prompt(question, default) else default.also { defaultsUsed += question to it }

    companion object {
        /** The run the current script belongs to. */
        var current: Run = Run()
    }
}

/** Declares an input: required, or with a [default], or with a [question] asked when first read (if the run allows). */
fun input(default: String? = null, question: String? = null) = InputDeclaration(default, question)

class InputDeclaration internal constructor(private val default: String?, private val question: String?) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, String> {
        val run = Run.current
        run.declare(Declared(property.name, default, question))
        return ReadOnlyProperty { _, _ -> run.value(property.name) }
    }
}

/** A question asked in the middle of a script: its [default] answers it unless the run is interactive. */
fun ask(question: String, default: String): String = Run.current.ask(question, default)
