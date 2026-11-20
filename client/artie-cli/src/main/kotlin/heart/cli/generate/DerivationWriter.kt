package heart.cli.generate

/**
 * NAME.derivation.yaml: how the generator derived each value and line of a script, numbered in the order the script
 * first shows it, with the rule that bound it, and where it stands in the run's script and in the generated one;
 * and where each step of the run begins in both, so a report can show them side by side, row by row.
 */
class DerivationWriter(private val derivations: Derivations) {

    val count: Int get() = derivations.shown.size

    fun write(script: String, sourceName: String?, captureName: String): String = buildString {
        appendLine(
            "# How $script was derived from $captureName" + (sourceName?.let { ", the run of $it" } ?: "") +
                ": each derivation, numbered",
        )
        appendLine(
            "# in the order $script first shows it, with the rule of spec/PLAN-SCRIPT-GENERATOR.md that bound it, " +
                "and where it",
        )
        appendLine(
            "# stands in both scripts (line and column, from 1); rows: where each step of the run begins in both.",
        )
        appendLine("script: ${quoted(script)}")
        sourceName?.let { appendLine("source: ${quoted(it)}") }
        appendLine("capture: ${quoted(captureName)}")
        appendLine("rows:")
        derivations.rows.forEach { (at, source, generated) ->
            appendLine("  - {at: ${quoted(at)}, source: ${source ?: "null"}, generated: $generated}")
        }
        appendLine(if (derivations.shown.isEmpty()) "derivations: []" else "derivations:")
        derivations.shown.forEach { derivation ->
            appendLine("  - n: ${derivation.n}")
            appendLine("    kind: ${derivation.kind}")
            appendLine("    rule: ${quoted(derivation.rule)}")
            appendLine("    title: ${quoted(derivation.title)}")
            appendLine("    why: ${quoted(derivation.why)}")
            spans("source", derivation.source.sortedWith(compareBy({ it.line }, { it.column })))
            spans("generated", derivation.generated)
        }
    }

    private fun StringBuilder.spans(name: String, spans: List<Span>) {
        appendLine(if (spans.isEmpty()) "    $name: []" else "    $name:")
        spans.forEach { appendLine("      - {line: ${it.line}, column: ${it.column}, text: ${quoted(it.text)}}") }
    }

    /** [text] single-quoted, as YAML reads it back whatever it holds. */
    private fun quoted(text: String): String = "'" + text.replace("'", "''") + "'"
}
