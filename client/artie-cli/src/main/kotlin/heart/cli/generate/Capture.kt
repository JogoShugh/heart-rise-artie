package heart.cli.generate

import heart.cli.BadArguments
import org.yaml.snakeyaml.Yaml

/**
 * A run's capture, as artie run writes it (spec/PLAN-LINE-BY-LINE-REPORT.md): the run's own document, its inputs,
 * then one document per step, numbered from 1 as the value sources number them.
 */
class Capture(val name: String, text: String) {
    private val documents = Yaml().loadAll(text).map { it as? Map<*, *> ?: emptyMap<Any?, Any?>() }.toList()

    /** Each input the run read, by name: its value, how it was given, and its default and question if declared. */
    val inputs: Map<String, Map<*, *>> = (documents.firstOrNull()?.get("run") as? Map<*, *>)
        ?.get("inputs").map().entries.associate { (name, input) -> name.toString() to input.map() }

    val steps: List<Step> = documents.drop(1).mapIndexed { i, doc -> Step(i + 1, doc) }

    /** The input the run was given [value] by, if any: a step that used the value names the input instead. */
    fun inputGiven(value: String): String? =
        inputs.entries.firstOrNull { (_, input) -> input["value"]?.toString() == value }?.key

    /** A goal run's moves, as the script they amount to (spec/PLAN-PLAIN-TEXT-RUN.md); null for a script's run. */
    val source: String? = (documents.firstOrNull()?.get("run") as? Map<*, *>)?.get("source")?.toString()

    /**
     * A goal in steps' marks on the lines of [source] (spec/PLAN-GOAL-MARKDOWN.md), by line: `as`, the name its
     * step gave what the act on that line led to; `reasoned`, that the act stays an intent in the script made.
     */
    private val marks: Map<Int, Map<*, *>> = (documents.firstOrNull()?.get("run") as? Map<*, *>)?.get("marks").map()
        .entries.mapNotNull { (line, mark) -> line.toString().toIntOrNull()?.let { it to mark.map() } }.toMap()

    private val watch = (documents.firstOrNull()?.get("run") as? Map<*, *>)?.get("watch").map()

    /** A goal run's watch, as the script its decisions amount to (spec/PLAN-LISTENERS.md, slice 8); else null. */
    val watchScript: String? = watch["script"]?.toString()

    /** The same watch as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 1); null for a capture made before it. */
    val watchSteps: List<*>? = watch["steps"] as? List<*>

    /** The step a goal run's watch began at: the steps from there on are its looks, which its script makes again. */
    val watchFrom: Int? = (watch["from"] as? Number)?.toInt()

    /** The steps before a watch, if the run had one: the steps the script made from it makes one by one. */
    val stepped: List<Step> get() = watchFrom?.let { from -> steps.filter { it.number < from } } ?: steps

    /** The marks on [step]'s line of a goal run's script, if it has any. */
    fun marks(step: Step): Map<*, *> = step.line?.takeIf { source != null }?.let { marks[it] }.orEmpty()

    /** The step [number]; the generator only names steps the capture holds. */
    fun step(number: Int): Step? = steps.getOrNull(number - 1)

    /** The entry the run began at. */
    val entry: String
        get() = steps.firstOrNull()?.takeIf { it.say.startsWith("enter") }?.params?.get("entry")?.toString()
            ?: throw BadArguments("can't generate from $name: it doesn't begin by entering a service")

    /** Rule 8: only a run that finished is generated from; a watch's refusals, while it decided, are its own. */
    fun finished() {
        // A refusal its step said to expect (`refused-as`, goal-scenarios.feature) is the step done as meant; so is
        // one a script a goal's step ran expected, its own (goal-rise.feature).
        val meant = { step: Step ->
            marks(step)["refused-as"] != null || step.expected != null && step.expected == step.refused["kind"]
        }
        stepped.firstOrNull { it.outcome in UNFINISHED && !meant(it) }?.let {
            throw BadArguments("can't generate from $name: step ${it.number} was ${it.outcome}")
        }
    }

    private companion object {
        val UNFINISHED = setOf("refused", "failed")
    }
}

/** One step of a capture. */
class Step(val number: Int, private val doc: Map<*, *>) {
    val mode: String = doc["mode"]?.toString().orEmpty()
    val say: String = doc["say"]?.toString().orEmpty()
    val line: Int? = (doc["line"] as? Number)?.toInt()
    val within: Int? = (doc["within"] as? Number)?.toInt()
    val opens: Boolean = doc["opens"] == true
    val outcome: String = doc["outcome"]?.toString().orEmpty()

    /** The refusal the step expected, by its words (`stale`); null when it expected to be done. */
    val expected: String? = doc["expect"]?.toString()

    /** Where the client was after the step: what its act led to (goal-rise.feature). */
    val address: String? = doc["address"]?.toString()
    val params: Map<*, *> = doc["params"].map()
    val values: Map<*, *> = doc["values"].map()
    val chose: Map<*, *> = doc["chose"].map()
    val guard: Map<*, *> = doc["guard"].map()

    /** Why an act wasn't made: its kind (a guard, the server) and its detail; empty for one that was. */
    val refused: Map<*, *> = doc["refused"].map()
    val template: String? = doc["reason"].map()["template"]?.toString()

    /** What a scripted act or follow was for, in words, as its script said (`intent = …`), with values filled. */
    val intent: String? = doc["intent"]?.toString()

    /** What an act or follow was for, in words a model inferred (`inferred = …`), as its script said. */
    val inferred: String? = doc["inferred"]?.toString()

    /** The person's words a model's inferred ones serve (`fromIntent = …`), as its script said. */
    val fromIntent: String? = doc["fromIntent"]?.toString()
    val slots: Map<*, *> = doc["reason"].map()["slots"].map()
    val waitedFor: String? = doc["waited"].map()["for"]?.toString()
    val state: Map<*, *> = doc["now"].map()["state"].map()

    /** The titles of the items the step's answer linked (RFC 6573 `item`), in order: a list's members. */
    val items: List<String> = doc["now"].map()["links"].map()["item"].list().map { it.toString() }

    /** The traceparent of each request the step sent, when the run kept its exchanges (`--exchanges`). */
    val traceparents: List<String> = (doc["exchanges"] as? List<*>).orEmpty()
        .mapNotNull { it.map()["request"].map()["headers"].map()["traceparent"]?.toString() }

    /** The relation a selection selected; null for a step that selected nothing. */
    val relation: String? get() = params["relation"]?.toString()

    /** Whether the reasoner chose a value of this step that its words don't give. */
    val judged: Boolean get() = values.values.any { "chosen" in it.map()["from"].list() }

    /** Whether this selection followed a link, rather than acting on a form. */
    val follows: Boolean get() = chose["kind"] == "link" || (mode == "script" && say.startsWith("follow"))

    /** The value of [pointer] in this step's state (RFC 6901). */
    fun read(pointer: String): Any? = pointer.removePrefix("/").split('/')
        .map { it.replace("~1", "/").replace("~0", "~") }
        .fold(state as Any?) { node, key ->
            when (node) {
                is Map<*, *> -> node[key]
                is List<*> -> key.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            }
        }
}

/** A step and the steps nested in its block, as the run nested them. */
class Node(val step: Step) {
    val children = mutableListOf<Node>()

    companion object {
        /**
         * The run's steps as a tree under its entry, by each step's line and the line of the block it is within; a
         * block end's re-read is no step of its own in a script.
         */
        fun of(capture: Capture): Node {
            val root = Node(capture.steps.first())
            val open = ArrayDeque(listOf(root))
            // A wait on a reply reads it at its address: a step of its own, the block the goal's step opened.
            // Its first read only: the wait's reads again, and the way back, are the scope's own.
            val opened = mutableSetOf<Int?>()
            val kept = { step: Step -> SCOPES.any { it in capture.marks(step) } && opened.add(step.line) }
            // A goal run's steps each stand for a line of its script: a script a step ran is that step's own.
            val nested = { step: Step -> capture.source != null && step.line == null }
            val skipped = { step: Step -> step.say.startsWith("return to") && !kept(step) || nested(step) }
            capture.stepped.drop(1).filterNot(skipped).forEach { step ->
                while (open.size > 1 && open.last().step.line != step.within) open.removeLast()
                val node = Node(step)
                open.last().children += node
                if (step.opens) open.addLast(node)
            }
            return root
        }
    }
}

internal fun Any?.map(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any?, Any?>()
