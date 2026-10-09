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
}
