package heart

/**
 * SPEC §6.4 / §6.10: the rung the client asks for. [prefer] is the whole
 * Prefer header for that rung; L0 sends none. The token text lives only
 * here, so a rename of `cid` ([0.5] §6.4) is a one-line change.
 */
enum class Rung(val prefer: String?) {
    L0(null),
    L1("forms=cid"),
    L2("forms=cid, schema=cid"),
}
