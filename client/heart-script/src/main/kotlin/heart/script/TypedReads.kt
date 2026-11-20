package heart.script

/*
 * A member of the held state read as a type, by the names state[...] takes, so a script carries a value from one
 * step to a later one in a typed variable, with no cast (spec/PLAN-SCRIPT-GENERATOR.md):
 * `issue = state.long("number")`. A member that isn't that type fails the script, saying what it was.
 */

/** [member] as a whole number. */
fun State.long(member: String): Long = typed(member, "a number") { it as? Long }

/** [member] as a number, whole or not. */
fun State.double(member: String): Double = typed(member, "a number") { (it as? Number)?.toDouble() }

/** [member] as text. */
fun State.text(member: String): String = typed(member, "text") { it as? String }

/** [member] as true or false. */
fun State.bool(member: String): Boolean = typed(member, "true or false") { it as? Boolean }

private fun <T> State.typed(member: String, kind: String, read: (Any?) -> T?): T {
    val (_, value) = find(member)
    return read(value) ?: error("$member is not $kind: $value")
}
