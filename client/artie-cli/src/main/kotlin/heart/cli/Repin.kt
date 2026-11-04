package heart.cli

import heart.cli.generate.Capture
import heart.cli.generate.Step
import heart.cli.generate.map
import heart.cli.generate.scalar
import heart.script.FormPin
import java.nio.file.Files
import java.nio.file.Path

/** The outcomes of an act that was done. */
internal val DONE = setOf("done", "new resource")

/**
 * What a run found to re-pin for a [relation]: the [set] it was offered in, and its form when that changed, with its
 * [act]; [moved] when only its target did.
 */
internal class Found(
    val relation: String,
    val set: String?,
    val form: String? = null,
    val method: String? = null,
    val act: String? = null,
    val moved: Boolean = false,
) {
    val why: String
        get() = when {
            moved -> "moved to a new target"
            form != null -> "changed, and what the script sends still fits it"
            else -> "offered in a new set"
        }
}

/**
 * After a run, what it was newly offered, written into its lock file as the config says
 * (spec/ideas/replay-guards-lenient-strict.md, the walk's steps 3 and 4): each set a pinned form was offered in,
 * with the form's own integrity the one pinned, that the lock file didn't record; and each form that changed but
 * held by the values sent (`on-change: values`), or by its act (only its target changed), with its new integrity,
 * act, method and set; where the act was done.
 * `repin: auto` writes it, `propose` only says it, and `never`, the default, or --no-repin do neither. The lock
 * file is the one the run held to: --pins, or NAME.pins.yaml beside the script. What it says, a line for each
 * relation re-pinned, is returned.
 */
fun repin(asked: Arguments, folder: Path, script: Path, config: Config, trace: String): List<String> {
    val mode = if (asked.noRepin) "never" else config.repin ?: "never"
    val lock = folder.resolve(lockName(asked, script))
    if (mode == "never" || !Files.exists(lock)) return emptyList()
    val pinned = pins(Files.readString(lock)).forms
    val found = Capture("run", trace).steps
        .mapNotNull { step -> pinned[step.relation]?.let { found(step, it) } }
        .distinctBy { Triple(it.relation, it.set, it.form) }
    if (mode == "auto" && found.isNotEmpty()) {
        Files.writeString(lock, found.fold(Files.readString(lock), ::written))
    }
    val said = if (mode == "auto") "re-pinned" else "would re-pin"
    return found.distinctBy { it.relation }.map { "$said ${it.relation}: ${it.why}" }
}

/** What [step], whose form the lock file pins as [pin], found to re-pin; null when nothing. */
private fun found(step: Step, pin: FormPin): Found? {
    val set = step.guard["set"].map()["integrity"]?.toString()
    val form = step.guard["form"]?.toString()
    val held = step.guard["held"]
    return when {
        step.outcome !in DONE -> null
        held in setOf("values", "act") && form != null -> Found(
            step.relation.orEmpty(), set, form, step.chose["method"]?.toString(), step.guard["act"]?.toString(),
            moved = held == "act",
        )
        form == pin.integrity && set != null && set !in pin.sets -> Found(step.relation.orEmpty(), set)
        else -> null
    }
}

/** The lock file's [text] with what was [found] written in, every other line as it was. */
internal fun written(text: String, found: Found): String {
    val lines = text.lines().toMutableList()
    val at = lines.indexOf("  ${scalar(found.relation)}:")
    found.form?.let { lines[at + 1] = "    integrity: ${scalar(it)}" }
    // After the integrity, in this order: the act, then the method; each written over the one there, or added.
    listOf("act" to found.act, "method" to found.method).fold(at + 1) { after, (key, value) ->
        val there = lines[after + 1].startsWith("    $key:")
        if (value != null && there) lines[after + 1] = "    $key: ${scalar(value)}"
        if (value != null && !there) lines.add(after + 1, "    $key: ${scalar(value)}")
        if (value != null || there) after + 1 else after
    }
    found.set?.let { set ->
        val sets = (at + 1 until lines.size).first { lines[it].startsWith("    sets:") }
        var end = sets + 1
        while (end < lines.size && lines[end].startsWith("      - ")) end++
        if (lines.subList(sets + 1, end).none { it == "      - ${scalar(set)}" }) {
            lines[sets] = "    sets:"
            lines.add(end, "      - ${scalar(set)}")
        }
    }
    return lines.joinToString("\n")
}
