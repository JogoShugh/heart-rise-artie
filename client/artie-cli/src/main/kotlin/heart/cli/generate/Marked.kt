package heart.cli.generate

/**
 * One value of [step] under [field], its expression marked with its derivation, and where the run's script wrote
 * it: a scripted step's expression as written, else where its words name the value.
 */
internal class Marked(source: List<String>?, private val step: Step, field: String, private val why: Why) {
    private val written = Written(source, step)
    private val scripted = written.spanOf(field)?.takeIf { step.mode == "script" }

    fun input(name: String, expression: String) = mark(why.input(name), expression, listOf(slot(name)))

    fun madeInput(name: String, text: String, expression: String) = mark(why.input(name, text), expression)

    fun words(text: String, expression: String) =
        mark(why.words(expression, step.mode == "script"), expression, listOf(written.find(KotlinText.whole(text))))

    /** A variable, named in the words as [named]. */
    fun answer(variable: Variable, named: String, tie: String?, expression: String) =
        mark(why.answer(variable, tie), expression, listOf(slot(named)))

    fun model(expression: String) = mark(why.model(expression, step), expression)

    /** A string: a template of [names], or, with none, the words said again or a constant. */
    fun string(expression: String, names: Collection<String>, from: List<*>): String {
        val derivation = if (names.isEmpty()) why.said(expression, from, step.say) else why.template(expression)
        return mark(derivation, expression, names.map(::slot))
    }

    fun said(expression: String, from: List<*>) = mark(why.said(expression, from, step.say), expression)

    private fun mark(derivation: Derivation, expression: String, found: List<List<Span>> = emptyList()): String =
        why.mark(derivation, expression, scripted?.let { listOf(listOf(it)) } ?: found)

    /** Where the call names [name] as a slot: `$name` or `${name}`. */
    private fun slot(name: String): List<Span> =
        written.find(Regex("\\$(\\{${Regex.escape(name)}}|${Regex.escape(name)}(?![A-Za-z0-9_]))"))
}
