package heart

/**
 * SPEC §6.4 / §6.10: the rung the client asks for. [prefer] is the whole
 * Prefer header for that rung; L0 sends none. Each token asks for a layer
 * to be delivered by link.
 */
enum class Rung(val prefer: String?) {
    L0(null),
    L1(FORMS_LINK),
    L2("$FORMS_LINK, $SCHEMA_LINK"),
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
                FORMS_LINK in honoured && SCHEMA_LINK in honoured -> L2
                FORMS_LINK in honoured -> L1
                else -> L0
            }
        }
    }
}

private const val FORMS_LINK = "forms=link"
private const val SCHEMA_LINK = "schema=link"
