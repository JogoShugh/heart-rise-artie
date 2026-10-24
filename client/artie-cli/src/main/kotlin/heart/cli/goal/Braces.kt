package heart.cli.goal

/**
 * The names in braces in a goal's words (spec/PLAN-GOAL-MARKDOWN.md): `{name}`, an input or a name a step gave
 * above; `{name.member}`, a member of a named resource's state; `{{` and `}}`, a brace. [inputs] are the goal's
 * inputs, [given] what may be named here, and [named] every name the steps give, by the line that gives it.
 */
internal class Braces(val inputs: Collection<String>, val given: Collection<String>, val named: Map<String, Int>) {
    /** Checks each brace of [words]; one that names nothing here is told to [refuse], with what to write. */
    fun check(words: String, refuse: (String) -> Nothing) {
        var i = 0
        while (i < words.length) {
            when {
                words.startsWith("{{", i) || words.startsWith("}}", i) -> i += 2
                words[i] == '{' -> {
                    val end = words.indexOf('}', i).takeIf { it > 0 } ?: refuse("a { with no }: write {{ for a brace")
                    name(words.substring(i + 1, end), refuse)
                    i = end + 1
                }
                words[i] == '}' -> refuse("a } with no {: write }} for a brace")
                else -> i++
            }
        }
    }

    private fun name(inside: String, refuse: (String) -> Nothing) {
        val found = NAME.matchEntire(inside) ?: refuse("{$inside} is no name: write {{ for a brace")
        val name = found.groupValues[1]
        val member = found.groupValues[2]
        when {
            name in inputs -> if (member.isNotEmpty()) refuse("{$name} is an input: it has no members")
            name in given -> Unit
            name in named -> refuse("{$name} is named below, at line ${named[name]}")
            else -> {
                given.firstOrNull { it.equals(name, ignoreCase = true) }?.let { refuse("{$name}: did you mean {$it}?") }
                refuse("{$name} is no input and no name given above")
            }
        }
    }

    private companion object {
        val NAME = Regex("""([A-Za-z_][A-Za-z0-9_]*)((?:\.[A-Za-z_][A-Za-z0-9_]*)*)""")
    }
}
