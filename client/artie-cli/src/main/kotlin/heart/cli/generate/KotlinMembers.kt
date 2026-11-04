package heart.cli.generate

import heart.script.kotlinString

/** How much deeper a case's statement is than its whenever: within whenOffered, then within its case. */
internal const val CASE_DEPTH = 3

/**
 * A list's members (Members.kt) as Kotlin, at [depth]: a whenever over the list, and in it whenOffered with [cases],
 * each a relation and its statement's lines, in the order the run first made them; [words], the step's, for a member
 * offering an act the run never met; otherwise a note. The script held as data writes the same as when-offered.
 */
internal fun overMembers(depth: Int, words: String?, cases: List<Pair<String, List<String>>>): List<String> {
    val pad = INDENT.repeat(depth)
    val inner = pad + INDENT
    val arguments = listOfNotNull(
        words?.let { "intent = ${kotlinString(it)}," },
        "otherwise = { note(\"none of these acts is offered here\") },",
    )
    val written = cases.flatMap { (rel, lines) ->
        listOf("$inner${INDENT}case(${KotlinText.quoted(rel)}) {") + lines + "$inner$INDENT}"
    }
    return listOf("${pad}whenever {", "${inner}whenOffered(") + arguments.map { "$inner$INDENT$it" } + "$inner) {" +
        written + "$inner}" + "$pad}"
}

private const val INDENT = "    "
