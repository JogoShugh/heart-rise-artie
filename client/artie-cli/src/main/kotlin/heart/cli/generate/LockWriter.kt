package heart.cli.generate

/**
 * Rule 9: what the run was offered, pinned beside the script it gives, never in it: the rung it read at, and by
 * relation each form's own integrity, its act (the form with its target set aside), its method and every affordance
 * set it was offered in, and each link's target.
 */
class LockWriter(capture: Capture) {
    // The rung the run read at: the highest any step was answered at. A form taken at the entry and answered at L0
    // says nothing of it, since the service's root answers at L0 whatever is asked: a run that acted only there pins
    // none, and its script reads at whatever rung it is run at.
    private val rung = capture.steps.filterIndexed { i, step ->
        // A form taken at the entry: the step before it entered.
        val atEntry = i > 0 && capture.steps[i - 1].say.startsWith("enter")
        !(atEntry && step.guard["rung"] == "L0")
    }.mapNotNull { it.guard["rung"]?.toString() }.maxOrNull()
    private val forms = linkedMapOf<String, Pair<String, LinkedHashSet<String>>>()
    private val methods = mutableMapOf<String, String>()
    private val acts = mutableMapOf<String, String>()
    private val links = linkedMapOf<String, String>()

    // The digest of every form the server declared, from the entry's manifest, for look-then-leap's look at everything.
    private val digest = capture.steps.firstNotNullOfOrNull { it.state["hr:manifest"].map()["digest"]?.toString() }

    init {
        capture.steps.filter { it.relation != null }.forEach { step ->
            val relation = step.relation.orEmpty()
            val target = step.chose["target"]?.toString()
            val form = step.guard["form"]?.toString()
            when {
                // A link's target names one resource; what a script relies on is what it leaves to fill.
                step.follows && target != null ->
                    links.putIfAbsent(relation, Regex("\\{[^}]*}").findAll(target).joinToString("") { it.value })
                !step.follows && form != null -> {
                    val sets = forms.getOrPut(relation) { form to linkedSetOf() }.second
                    step.guard["set"].map()["integrity"]?.let { sets += it.toString() }
                    step.chose["method"]?.let { methods.putIfAbsent(relation, it.toString()) }
                    step.guard["act"]?.let { acts.putIfAbsent(relation, it.toString()) }
                }
            }
        }
    }

    val formCount: Int get() = forms.size
    val linkCount: Int get() = links.size

    fun write(script: String, captureName: String): String = buildString {
        appendLine(
            "# What $script was recorded against, from $captureName. " +
                "artie run holds the script to it before every act:",
        )
        appendLine(
            "# --guard lenient (the default) refuses a form no longer offered, or whose integrity changed, unless " +
                "only its",
        )
        appendLine(
            "# target did (its act, the form with its target set aside, as pinned); --guard strict refuses any " +
                "change, and",
        )
        appendLine("# one offered in a set not recorded for it.")
        rung?.let { appendLine("rung: ${scalar(it)}") }
        digest?.let { appendLine("digest: ${scalar(it)}") }
        appendLine(if (forms.isEmpty()) "forms: {}" else "forms:")
        forms.forEach { (relation, pin) ->
            appendLine("  ${scalar(relation)}:")
            appendLine("    integrity: ${scalar(pin.first)}")
            acts[relation]?.let { appendLine("    act: ${scalar(it)}") }
            methods[relation]?.let { appendLine("    method: ${scalar(it)}") }
            appendLine(if (pin.second.isEmpty()) "    sets: []" else "    sets:")
            pin.second.forEach { appendLine("      - ${scalar(it)}") }
        }
        appendLine(if (links.isEmpty()) "links: {}" else "links:")
        links.forEach { (relation, target) -> appendLine("  ${scalar(relation)}: ${scalar(target)}") }
    }
}

/** [text] plain where YAML reads it back as the same string, single-quoted where it wouldn't. */
internal fun scalar(text: String): String {
    val plain = text.isNotEmpty() && text.first() !in "{[]},&*#?|-<>=!%@`'\" " && ": " !in text &&
        " #" !in text && !text.endsWith(":")
    return if (plain) text else "'" + text.replace("'", "''") + "'"
}
