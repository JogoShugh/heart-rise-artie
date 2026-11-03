package heart.cli

import heart.cli.generate.Capture
import heart.cli.generate.Step
import heart.cli.generate.map
import heart.cli.generate.scalar
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

/*
 * Self-repair, kept (spec/ideas/self-repair-and-keep.md). A run of a script held as data whose act fell back to its
 * words, because its form changed, and was done that way, may write that repair into the script and its lock file,
 * so the next run needs no model. It is kept without a person only where that is safe: the run finished, the repair
 * made the act the script was making, and every value it sent is one the script sent, written as the script wrote
 * it, so an input stays an input and a value read from state stays a reference.
 */

/** A repair the run made: the act its guard refused, and the act its words made in its place. */
private class Repair(val refused: Step, val made: Step) {
    val relation: String = refused.relation.orEmpty()

    /** Each field the repair sent, with the field of the script's that sent the same value; null for none. */
    val fields: List<Pair<String, String?>> = sent(made).map { (name, value) ->
        val same = sent(refused).filter { it.second == value }.map { it.first }
        name to (same.singleOrNull() ?: name.takeIf { it in same })
    }

    /** The form the repair acted on, as the lock file pins it. */
    val pin = Found(relation, made.guard["set"].map()["integrity"]?.toString(), made.guard["form"]?.toString(),
        made.chose["method"]?.toString())

    /** What it sends now, as a person reads it: `content (was body)`. */
    val said: String get() = fields.joinToString { (name, from) -> if (from == name) name else "$name (was $from)" }
}

/** What [step] sent, field by field, each value as text. */
private fun sent(step: Step): List<Pair<String, String>> =
    step.params.filterKeys { it != "relation" }.map { (name, value) -> name.toString() to value.toString() }

/** The repairs in a run's [trace]: each act refused by its guard, then made from its words, and done. */
private fun repairs(trace: String): List<Repair> = Capture("run", trace).steps.zipWithNext()
    .filter { (refused, made) ->
        refused.mode == "script" && refused.outcome == "refused" && refused.refused["kind"] == "guard" &&
            made.mode == "intent" && made.outcome in DONE
    }
    .map { (refused, made) -> Repair(refused, made) }

/**
 * After a run of [script], held as data, each repair its [trace] holds, written into the script and its [lock] file
 * as [config]'s keep says: `auto` writes it, `propose` only says it, and `never`, the default, does neither. Nothing
 * is kept from a run that didn't [finish]. What it says, a line for each repair, is returned.
 */
fun keep(script: Path, lock: Path, config: Config, trace: String, finish: Boolean): List<String> {
    val mode = config.keep ?: "never"
    if (mode == "never" || !isData(script)) return emptyList()
    var text = Files.readString(script)
    val kept = mutableListOf<Repair>()
    val said = repairs(trace).map { repair ->
        val why = if (finish) unsafe(repair) else "the run didn't finish"
        val rewritten = if (why == null) rewritten(text, repair) else null
        when {
            why != null -> "not kept ${repair.relation}: $why"
            rewritten == null -> "not kept ${repair.relation}: no one step of the script acts on it"
            else -> {
                text = rewritten
                kept += repair
                (if (mode == "auto") "kept" else "would keep") + " ${repair.relation}: ${repair.said}"
            }
        }
    }
    if (mode == "auto" && kept.isNotEmpty()) {
        Files.writeString(script, text)
        if (Files.exists(lock)) Files.writeString(lock, kept.map { it.pin }.fold(Files.readString(lock), ::written))
    }
    return said
}

/** Why [repair] can't be kept without a person; null when it can. */
private fun unsafe(repair: Repair): String? = when {
    repair.made.relation != repair.relation -> "the repair acted on ${repair.made.relation}"
    else -> repair.fields.firstOrNull { it.second == null }?.let { "${it.first} has a value the script never sent" }
}

/**
 * The script's [text] with the one step that acts on [repair]'s relation sending the repair's fields, each written
 * as the script wrote the field it came from; every other line as it was. Null when no one step acts on it.
 */
private fun rewritten(text: String, repair: Repair): String? {
    val step = acts(Yaml().compose(StringReader(text)), repair.relation).singleOrNull() ?: return null
    val fields = step.value.firstOrNull { (it.keyNode as? ScalarNode)?.value == "fields" }
    val written = (fields?.valueNode as? MappingNode)?.value.orEmpty()
    return if (written.isEmpty()) {
        text.takeIf { repair.fields.isEmpty() }
    } else {
        val was = written.associate { (it.keyNode as ScalarNode).value to it.valueNode }
        val first = written.first().keyNode.startMark
        val last = written.last().valueNode.endMark
        val indent = " ".repeat(first.column)
        val lines = repair.fields.joinToString("\n") { (name, from) ->
            val value = was.getValue(checkNotNull(from)).let { text.substring(it.startMark.index, it.endMark.index) }
            "$indent${scalar(name)}: $value"
        }
        text.substring(0, first.index - first.column) + lines + text.substring(last.index)
    }
}

/** Every step in [node] that acts on [relation]: a map whose `act` names it. */
private fun acts(node: Node?, relation: String): List<MappingNode> = when (node) {
    is MappingNode -> listOfNotNull(node.takeIf { it.value.any { tuple -> names(tuple, relation) } }) +
        node.value.flatMap { acts(it.valueNode, relation) }
    is SequenceNode -> node.value.flatMap { acts(it, relation) }
    else -> emptyList()
}

/** Whether [tuple] is `act: relation`. */
private fun names(tuple: NodeTuple, relation: String): Boolean =
    (tuple.keyNode as? ScalarNode)?.value == "act" && (tuple.valueNode as? ScalarNode)?.value == relation
