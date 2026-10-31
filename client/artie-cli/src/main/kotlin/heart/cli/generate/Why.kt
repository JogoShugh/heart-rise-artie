package heart.cli.generate

/**
 * The derivations of spec/PLAN-SCRIPT-GENERATOR.md's rules, one kind each, with the words a person reads in the
 * derivation report: what was bound, the rule, and why.
 */
internal class Why(private val derivations: Derivations) {

    fun mark(derivation: Derivation, text: String, at: List<List<Span>> = emptyList()): String =
        derivations.mark(derivation, text, at)

    /** An input the run read; or, made from the [text] the run sent under [name], one `--inputs` asked for. */
    fun input(name: String, text: String? = null): Derivation = if (text == null) {
        derivations.of(
            "input:$name", "input", "1, 2.1", "$name, an input",
            "Declared as the run's script declared it (rule 1); each value the run took from it is sent from it " +
                "(rule 2.1).",
        )
    } else {
        derivations.of(
            "input:$name", "input", "4a", "$name, made an input",
            "--inputs $name: the text the run sent under $name is an input, ${KotlinText.quoted(text)} its default.",
        )
    }

    fun answer(variable: Variable, tie: String?): Derivation = derivations.of(
        "answer:${variable.name}", "answer", "2.3", "${variable.name}, read from step ${variable.step}'s answer",
        "Step ${variable.step} brought ${variable.pointer} into the run: the resource it led to is kept as " +
            "\"${variable.keptAs}\" in that step's block, the value declared once and read from it with " +
            "${variable.kind.read}, and sent from it wherever the run sent its value." +
            (tie?.let { " Marked for review: $it (rule 5)." } ?: ""),
    )

    /** The relation [step] selected, quoted; marked when the reasoner chose it, where the script said `reason`. */
    fun relation(step: Step, source: List<String>?): String {
        val quoted = KotlinText.quoted(step.relation.orEmpty())
        if (step.mode != "intent") return quoted
        val derivation = derivations.of(
            "relation", "relation", "9", "The relation each reason step chose, named",
            "Where the run's script reasoned, the reasoner chose among the forms and links offered. The generated " +
                "script names the relation it chose; the lock file pins what was offered with it.",
        )
        return mark(derivation, quoted, listOf(Written(source, step).find(Regex("\\bact\\b")).take(1)))
    }

    fun words(expression: String, scripted: Boolean): Derivation = derivations.of(
        "words:$expression", "words", "2.2",
        "$expression, as the ${if (scripted) "script" else "words"} wrote it",
        if (scripted) "Written literally in the script's own call: a constant." else
            "Written in the words, outside any slot: a constant, never read from an answer.",
    )

    fun template(expression: String): Derivation = derivations.of(
        "template:$expression", "template", "3", "A string, its values put back as their names",
        "Each value inside the string that an input or an earlier answer gave is put back as that name.",
    )

    fun said(expression: String, from: List<*>, say: String): Derivation = when {
        "paraphrase" in from -> derivations.of(
            "paraphrase:$expression", "paraphrase", "4", "$expression, the words said again",
            "The words, \"$say\", said another way: a constant with no mark, since nothing was judged.",
        )
        "words" in from -> words(expression, scripted = false)
        else -> derivations.of(
            "constant:$expression", "constant", "2.4", "$expression, a constant",
            "No input, no earlier answer and no slot holds it: the value the run sent, as it was.",
        )
    }

    fun model(expression: String, step: Step): Derivation = derivations.of(
        "model:${step.number}:$expression", "model", "4", "$expression, the reasoner's own choice",
        "The words, \"${step.say}\", don't give it: frozen as the constant it was, and marked for review.",
    )

    fun kept(step: Step): Derivation = derivations.of(
        "kept:${step.number}", "kept", "4", "A reason step, kept",
        "--keep-reason: the reasoner chose a value the words don't give, so the step stays a reason step and " +
            "the script needs a reasoner.",
    )

    fun copied(say: String, fromScript: Boolean): Derivation = derivations.of(
        say, say, "7", if (say == "until") "Waits, as the run waited" else "Checks, as the run checked",
        if (fromScript) "Copied from the run's script, as written." else
            "Written from what the capture kept: the condition waited for, the facts checked.",
    )
}
