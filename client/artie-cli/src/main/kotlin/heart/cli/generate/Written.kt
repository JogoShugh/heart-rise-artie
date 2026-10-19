package heart.cli.generate

/**
 * A step the script wrote as `act(...)`, read from its whole call in the script, on as many lines as it takes: each
 * field's expression as written. A string with `$slots` is a template, as a reason step's words are; a name is a
 * variable or an input the script named.
 */
internal class Written(source: List<String>?, step: Step) {
    private val call: String = step.line?.let { line ->
        val lines = source?.drop(line - 1).orEmpty()
        val taken = StringBuilder()
        var depth = 0
        for (text in lines) {
            taken.append(text).append('\n')
            depth += text.count { it == '(' } - text.count { it == ')' }
            if (depth <= 0) break
        }
        taken.toString()
    }.orEmpty()

    private val first: Int = step.line ?: 0

    /** The expression the script wrote for [field], as written; null when the call doesn't name it. */
    fun expression(field: String): String? {
        val at = start(field) ?: return null
        return if (call.getOrNull(at) == '"') literal(at) else call.substring(at).takeWhile { it !in ",)\n" }.trim()
    }

    /** Where the expression the script wrote for [field] stands in the script. */
    fun spanOf(field: String): Span? = start(field)?.let { at -> expression(field)?.let { span(at, it) } }

    /** Everywhere [pattern] matches in the call, in the order it is written. */
    fun find(pattern: Regex): List<Span> = pattern.findAll(call).map { span(it.range.first, it.value) }.toList()

    private fun start(field: String): Int? =
        Regex("\"${Regex.escape(field)}\"\\s+to\\s+").find(call)?.range?.last?.plus(1)

    /** The text at [at] in the call, by its line and column in the script. */
    private fun span(at: Int, text: String): Span {
        val before = call.substring(0, at)
        return Span(first + before.count { it == '\n' }, at - (before.lastIndexOf('\n') + 1) + 1, text)
    }

    /** The string literal that begins at [from], to its closing quote, a `${…}` inside it skipped. */
    private fun literal(from: Int): String {
        var i = from + 1
        var depth = 0
        while (i < call.length) {
            val c = call[i]
            when {
                c == '\\' -> i++
                depth == 0 && c == '"' -> return call.substring(from, i + 1)
                call.startsWith("\${", i) -> { depth++; i++ }
                depth > 0 && c == '}' -> depth--
            }
            i++
        }
        return call.substring(from)
    }

    companion object {
        val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /**
         * The slots of [step] its script wrote, as a reason step's are: each `$name` of a string it sent, with the
         * value it held and where the capture says that came from; and each name it sent whole.
         */
        fun slots(source: List<String>?, step: Step): Map<*, *> {
            if (source == null || step.mode != "script") return step.slots
            val written = Written(source, step)
            val slots = linkedMapOf<Any?, Any?>()
            step.params.forEach { (field, value) ->
                val expression = written.expression(field.toString()) ?: return@forEach
                val from = step.values[field].map()["from"].list()
                when {
                    NAME.matches(expression) -> slots[expression] = mapOf("value" to value.toString(), "from" to from)
                    expression.startsWith("\"") ->
                        KotlinText.match(expression, value.toString())?.forEach { (name, held) ->
                            slots[name] = mapOf("value" to held, "from" to partFrom(from, held))
                        }
                }
            }
            return slots
        }

        /** Where the capture says [part] of a value came from: its parts' source, or the value's own. */
        private fun partFrom(from: List<*>, part: String): List<*> =
            from.firstNotNullOfOrNull { it.map()["parts"].map()[part] }?.let { listOf(it) } ?: from
    }
}
