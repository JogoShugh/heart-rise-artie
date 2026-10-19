package heart.cli

import heart.cli.generate.Capture
import heart.cli.generate.map
import heart.cli.generate.scalar
import java.nio.file.Files
import java.nio.file.Path

/** The outcomes of an act that was done. */
private val DONE = setOf("done", "new resource")

/**
 * After a run, what it was newly offered, written into its lock file as the config says
 * (spec/ideas/replay-guards-lenient-strict.md, the walk's step 3): each set a pinned form was offered in, with the
 * form's own integrity the one pinned, that the lock file didn't record, where the act was done. `repin: auto`
 * writes it beside the sets already pinned, `propose` only says it, and `never`, the default, or --no-repin do
 * neither. The lock file is the one the run held to: --pins, or NAME.pins.yaml beside the script. What it says,
 * a line for each relation re-pinned, is returned.
 */
fun repin(asked: Arguments, folder: Path, script: Path, config: Config, trace: String): List<String> {
    val mode = if (asked.noRepin) "never" else config.repin ?: "never"
    val lock = folder.resolve(asked.pins ?: (script.fileName.toString().substringBeforeLast('.') + ".pins.yaml"))
    if (mode == "never" || !Files.exists(lock)) return emptyList()
    val pinned = pins(Files.readString(lock)).forms
    val found = Capture("run", trace).steps.mapNotNull { step ->
        val pin = pinned[step.relation] ?: return@mapNotNull null
        val set = step.guard["set"].map()["integrity"]?.toString()?.takeIf { it !in pin.sets }
        val same = step.guard["form"]?.toString() == pin.integrity && step.outcome in DONE
        if (same && set != null) step.relation.orEmpty() to set else null
    }.distinct()
    if (mode == "auto" && found.isNotEmpty()) {
        Files.writeString(lock, found.fold(Files.readString(lock)) { text, (rel, set) -> added(text, rel, set) })
    }
    val said = if (mode == "auto") "re-pinned" else "would re-pin"
    return found.map { it.first }.distinct().map { "$said $it: offered in a new set" }
}

/** The lock file's [text] with [set] added to the sets pinned for [relation], every other line as it was. */
private fun added(text: String, relation: String, set: String): String {
    val lines = text.lines().toMutableList()
    val at = lines.indexOf("  ${scalar(relation)}:")
    val sets = (at + 1 until lines.size).first { lines[it].startsWith("    sets:") }
    var end = sets + 1
    while (end < lines.size && lines[end].startsWith("      - ")) end++
    lines[sets] = "    sets:"
    lines.add(end, "      - ${scalar(set)}")
    return lines.joinToString("\n")
}
