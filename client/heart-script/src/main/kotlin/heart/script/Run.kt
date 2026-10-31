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

/**
 * A goal run that stops for good: its planner stopped it, or it went past its limits. A watch notes a failed look
 * and looks again; this it never swallows, or a decision the planner won't make would be asked again forever.
 */
class GoalStopped(step: Int, what: String, reason: String) : ScriptStopped(step, what, reason)

/**
 * One input a script declares: [default] when not given; [question] asked of a person, if the run allows. A
 * default made fresh each run, the time now, keeps its [now] pattern, so a script made from the run makes it too.
 */
class Declared(val name: String, val default: String?, val question: String?, val now: String? = null) {
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
    /**
     * Where a watch keeps what it did, between runs: each wait that is over, each resource left. A watch run again
     * with the same file doesn't do again what it did, nor say again what it said. Null: it remembers nothing.
     */
    var remember: java.nio.file.Path? = null

    /** The time now, as `watch` and `once` read it; a test can move it with [pause] alone. Set before the run. */
    var clock: () -> java.time.Instant = java.time.Instant::now

    /**
     * How a watch's own threads wait, its listeners and its polls: on the real clock, or on a test's virtual time,
     * which [clock] and [pause] then read and move too. Set before the run.
     */
    var time: Time = RealTime()

    /** What a `watch` remembers from look to look: what each resource was, what waits, what is done. */
    internal val watching = Watching()

    /** The resources the script kept by name (`keepAs`), to read them again by name (`at`, `kept`). */
    internal val kept = java.util.concurrent.ConcurrentHashMap<String, Kept>()

    /**
     * What the reasoner chose in this run, by the words it was asked, the resource it was asked on, and what that
     * offered: the same again is the same choice, made with no model.
     */
    internal val reasoned = java.util.concurrent.ConcurrentHashMap<Triple<String, String, Set<String>>, Choice>()

    /** A resource kept by name: the address the server gave it, and its state when it was kept. */
    internal class Kept(val address: String, val state: State)

    /** Another entry than the script's: an environment, another binding. Set before the run. */
    var entry: String? = null

    /** Whether `act(intent = …)` with no relation may run: only when the runner allows it (`--reasoner`). */
    var reasoning = false

    /** Told of each step as it is traced: a runner prints it. */
    var onStep: (TraceStep) -> Unit = {}

    /** Told of each note as the script makes it: a runner prints it. */
    var onNote: (String) -> Unit = {}

    val declared = mutableListOf<Declared>()
    val notes = mutableListOf<String>()

    /** Questions answered by their defaults, unattended: the run report says so. */
    val defaultsUsed = mutableListOf<Pair<String, String>>()

    /** Every step of the run, in order, for its run report. */
    val trace = mutableListOf<TraceStep>()

    /** The script's own text, when the host gives it: a reason step's template is read from it. */
    var source: String? = null

    /**
     * For a run with no script frames (a goal, planned move by move): the line of [source] the next step stands
     * for, and the line of the block it is within, as a script's frames would say them.
     */
    var place: (() -> Pair<Int?, Int?>)? = null

    /** A goal run's goal, as written, each input a `$name`: the person's own words; a step's are the planner's. */
    var goal: String? = null

    /**
     * A goal in steps' marks on the lines of [source] (spec/PLAN-GOAL-MARKDOWN.md): `as`, the name its step gave
     * what that line's act led to; `reasoned`, that the act is for a step the model decides on every run.
     */
    val marks = sortedMapOf<Int, MutableMap<String, Any>>()

    /** A goal run's moves in words, as its planner was told them, refused tries included, for its capture. */
    var planned: List<String> = emptyList()

    /** What the run's model calls took, as its client counted them (Navigator.usage), for its capture. */
    var usage: Map<String, Long> = emptyMap()

    /** Which model took them, and where it answered (Navigator.model), for its capture. */
    var model: Map<String, String> = emptyMap()

    /**
     * A goal run's watch, as the script its decisions amount to, written when the capture is (spec/PLAN-LISTENERS.md,
     * slice 8), and the step it began at: the steps from there on are its looks.
     */
    var watchScript: (() -> String)? = null
    var watchFrom: Int? = null

    /** What the reasoner is told besides an act's words, for the act a watch is deciding; null otherwise. */
    @Volatile
    internal var besides: String? = null

    /** Each input the run read: its value, and how it was given ("given", "default" or "asked"). */
    val inputsUsed = linkedMapOf<String, Pair<String, String>>()

    internal fun traced(step: TraceStep) {
        // spec/PLAN-LINE-BY-LINE-REPORT.md: the script's own frames, innermost first, name the step's line and
        // its block's; a block's end is traced from inside the call that opened it, so it names that line.
        val lines = StackWalker.getInstance().walk { frames ->
            frames.filter { it.fileName?.endsWith(SCRIPT) == true }.map { it.lineNumber }.toList()
        }
        // A run with no script text of its own (a goal) says where each step stands itself.
        val placed = if (lines.isEmpty()) place?.invoke() else null
        step.line = lines.firstOrNull() ?: placed?.first
        step.within = lines.getOrNull(1) ?: placed?.second
        step.values = Sources.values(step, this, trace)
        if (step.asked.mode == "intent") step.reason = Sources.reason(source, step.line, step.asked.say, this, trace)
        trace += step
        onStep(step)
    }

    /** The trace as heart-run-report reads it: `{"steps": [...]}`. */
    fun traceJson(): String = kotlinx.serialization.json.JsonObject(
        mapOf(
            "inputs" to kotlinx.serialization.json.JsonObject(
                inputsUsed.mapValues { (name, used) ->
                    // As the script declared it, so a script made from the run declares it alike.
                    val declaration = declared.firstOrNull { it.name == name }
                    kotlinx.serialization.json.buildJsonObject {
                        put("value", kotlinx.serialization.json.JsonPrimitive(used.first))
                        put("given", kotlinx.serialization.json.JsonPrimitive(used.second))
                        declaration?.default?.let { put("default", kotlinx.serialization.json.JsonPrimitive(it)) }
                        declaration?.now?.let { put("now", kotlinx.serialization.json.JsonPrimitive(it)) }
                        declaration?.question?.let { put("question", kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                },
            ),
            "steps" to kotlinx.serialization.json.JsonArray(withKeeps(trace.map { it.json() })),
        ),
    ).toString()
    /** Each input's value, once read. */
    internal val values = mutableMapOf<String, String>()

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
            values[input.name] = prompt(input.name, null).also { inputsUsed[input.name] = it to ASKED }
        }
        declared.firstOrNull { it.kind == "question" && it.name !in inputs && !interactive }?.let {
            throw ScriptStopped(0, "", "${it.name} needs --interactive or a value")
        }
    }

    /** The input [name]'s value: given, asked before the run, its default, or asked when first read. */
    internal fun value(name: String): String = values.getOrPut(name) {
        val input = declared.first { it.name == name }
        val (value, given) = inputs[name]?.let { it to "given" }
            ?: input.default?.let { it to "default" }
            ?: (prompt(checkNotNull(input.question), null) to ASKED)
        inputsUsed[name] = value to given
        value
    }

    /**
     * The run's capture as a person reads it (spec/PLAN-LINE-BY-LINE-REPORT.md, "The capture is YAML"): one
     * YAML document for the run, its inputs, then one for each step.
     */
    fun traceYaml(): String {
        val trace = kotlinx.serialization.json.Json.parseToJsonElement(traceJson())
            as kotlinx.serialization.json.JsonObject
        val run = kotlinx.serialization.json.buildJsonObject {
            put("run", kotlinx.serialization.json.buildJsonObject {
                put("inputs", trace.getValue("inputs"))
                // A goal run's moves, as the script they amount to: its steps' lines are this text's.
                if (place != null) source?.let { put("source", kotlinx.serialization.json.JsonPrimitive(it)) }
                if (marks.isNotEmpty()) put("marks", element(marks.mapKeys { "${it.key}" }))
                if (planned.isNotEmpty()) put("moves", element(planned))
                if (model.isNotEmpty()) put("model", element(model))
                if (usage.isNotEmpty()) put("usage", element(usage))
                watchScript?.let { script ->
                    put("watch", element(mapOf("from" to watchFrom, "script" to script())))
                }
            })
        }
        return Yaml.stream(listOf(run) + (trace.getValue("steps") as kotlinx.serialization.json.JsonArray))
    }

    /** [steps], each with the members of its state later steps read, known only once the run is over. */
    private fun withKeeps(steps: List<kotlinx.serialization.json.JsonObject>) =
        steps.zip(Shape.keeps(steps)).map { (step, keeps) ->
            if (keeps.isEmpty()) {
                step
            } else {
                val read = keeps.mapValues { (_, by) ->
                    kotlinx.serialization.json.JsonArray(by.map { kotlinx.serialization.json.JsonPrimitive(it) })
                }
                kotlinx.serialization.json.JsonObject(step + ("keeps" to kotlinx.serialization.json.JsonObject(read)))
            }
        }

    /**
     * What the until just traced waited for, as the script wrote its condition on that line, and how many
     * [reads] it took.
     */
    internal fun waitedFor(reads: Int): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            val written = trace.last().line?.let { source?.lines()?.getOrNull(it - 1) }
            val condition = written?.substringAfter("until(", "")?.substringAfter("{", "")?.substringBeforeLast("}")
            condition?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { put("for", kotlinx.serialization.json.JsonPrimitive(it)) }
            put("reads", kotlinx.serialization.json.JsonPrimitive(reads))
        }

    internal fun ask(question: String, default: String): String =
        if (interactive) prompt(question, default) else default.also { defaultsUsed += question to it }

    companion object {
        /** The run the current script belongs to. */
        var current: Run = Run()

        /** What a script's frames are named after: its source, a .kts. */
        private const val SCRIPT = ".kts"

        /** An input a person gave when asked. */
        private const val ASKED = "asked"
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
