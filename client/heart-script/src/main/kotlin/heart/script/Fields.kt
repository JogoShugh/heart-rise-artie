package heart.script

/**
 * A long act's values, given in a block, one a line (`fields = { "title" to title }`), so each can carry its own
 * comment, or be left out by an `if`. Inside it, `name to value` adds the value to the act rather than making a
 * pair. The block runs before the act, where the script is: it may read the state held there.
 */
class Fields internal constructor() {
    internal val values = linkedMapOf<String, Any?>()

    /** Adds [value] under this name to the act's values. */
    infix fun String.to(value: Any?) {
        values[this] = value
    }
}
