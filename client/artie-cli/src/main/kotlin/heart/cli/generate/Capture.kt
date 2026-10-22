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

    /** A goal run's moves, as the script they amount to (spec/PLAN-PLAIN-TEXT-RUN.md); null for a script's run. */
    val source: String? = (documents.firstOrNull()?.get("run") as? Map<*, *>)?.get("source")?.toString()

    /**
     * A goal in steps' marks on the lines of [source] (spec/PLAN-GOAL-MARKDOWN.md), by line: `as`, the name its
     * step gave what the act on that line led to; `reasoned`, that the act stays an intent in the script made.
     */
    private val marks: Map<Int, Map<*, *>> = (documents.firstOrNull()?.get("run") as? Map<*, *>)?.get("marks").map()
        .entries.mapNotNull { (line, mark) -> line.toString().toIntOrNull()?.let { it to mark.map() } }.toMap()

    /** The marks on [step]'s line of a goal run's script, if it has any. */
    fun marks(step: Step): Map<*, *> = step.line?.takeIf { source != null }?.let { marks[it] }.orEmpty()

    /** The step [number]; the generator only names steps the capture holds. */
    fun step(number: Int): Step? = steps.getOrNull(number - 1)

    /** The entry the run began at. */
    val entry: String
        get() = steps.firstOrNull()?.takeIf { it.say.startsWith("enter") }?.params?.get("entry")?.toString()
            ?: throw BadArguments("can't generate from $name: it doesn't begin by entering a service")

    /** Rule 8: only a run that finished is generated from. */
    fun finished() {
        steps.firstOrNull { it.outcome in UNFINISHED }?.let {
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
    val params: Map<*, *> = doc["params"].map()
    val values: Map<*, *> = doc["values"].map()
    val chose: Map<*, *> = doc["chose"].map()
    val guard: Map<*, *> = doc["guard"].map()
    val template: String? = doc["reason"].map()["template"]?.toString()

    /** What a scripted act or follow was for, in words, as its script said (`intent = …`), with values filled. */
    val intent: String? = doc["intent"]?.toString()
    val slots: Map<*, *> = doc["reason"].map()["slots"].map()
    val waitedFor: String? = doc["waited"].map()["for"]?.toString()
    val state: Map<*, *> = doc["now"].map()["state"].map()

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
            capture.steps.drop(1).filterNot { it.say.startsWith("return to") }.forEach { step ->
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
