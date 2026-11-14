package heart.cli.generate

private val SLOT = Regex("""\$\{([^}]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")

/**
 * What [step] was for, as a generated script says it beside its act or follow (`intent = "…"`): the words, with
 * their slots, a literal is written from; null when the step said nothing. A reason step's words are its template
 * as the script wrote it, each `$name` the generated script also knows ([names]: its inputs and variables) kept,
 * every other slot put back as the value it held. A scripted step's are the words its intent said.
 */
internal fun intent(step: Step, names: Set<String>): String? {
    val said = if (step.mode == "intent") step.say else step.intent
    val template = step.template?.takeIf { step.mode == "intent" }
    return said?.let { template?.let { slotted(step, it, names) } ?: KotlinText.dollars(said) }
}

/** [template] with each slot [names] doesn't hold put back as its value; null when a slot has none. */
private fun slotted(step: Step, template: String, names: Set<String>): String? {
    var whole = true
    val kept = SLOT.replace(template) { slot ->
        val name = slot.groupValues[2].ifEmpty { null }
        val value = step.slots[slot.groupValues[1].ifEmpty { slot.groupValues[2] }].map()["value"]?.toString()
        when {
            name != null && name in names -> slot.value
            value != null -> KotlinText.dollars(value)
            else -> slot.value.also { whole = false }
        }
    }
    return kept.takeIf { whole }
}
