package heart

/**
 * SPEC §6.4 / §6.10: the rung the client asks for. [prefer] is the whole
 * Prefer header for that rung; L0 sends none. Each token asks for a layer
 * to be delivered by link.
 */
enum class Rung(val prefer: String?) {
    L0(null),
    L1("forms=link"),
    L2("forms=link, schema=link"),
    ;

    companion object {
        /**
         * SPEC §6.11.1: the rung a response is at, read from its
         * Preference-Applied header. No header means L0; the server honoured
         * nothing, or does not implement the ladder.
         */
        fun applied(preferenceApplied: String?): Rung {
            val honoured = preferenceApplied.orEmpty().split(',')
                .map { it.substringBefore(';').trim().lowercase().replace(" ", "") }
                .toSet()
            return when {
                "forms=link" in honoured && "schema=link" in honoured -> L2
                "forms=link" in honoured -> L1
                else -> L0
            }
        }
    }
}
