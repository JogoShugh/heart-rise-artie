package heart.script

/**
 * Act [rel] with [values] once: only where the list [listedIn] leads to from here doesn't already show what the act
 * would send, an item there saying one of its text values. ARTIE follows [listedIn], looks, comes back, and acts or
 * notes why it didn't. The look is made with the act's own values, so a said-once act can't drift from what it
 * checks. [fields] and [block] are as [Here.act]'s.
 */
fun Here.actOnce(
    rel: String,
    vararg values: Pair<String, Any?>,
    listedIn: String,
    fields: (Fields.() -> Unit)? = null,
    block: (Here.() -> Unit)? = null,
) {
    val sent = values.toMap() + Fields().also { fields?.invoke(it) }.values
    val texts = sent.values.filterIsInstance<String>()
    var listed = false
    follow(listedIn) { listed = texts.any { holds(mapOf(ITEM to it)) } }
    if (listed) {
        note("$rel not made: $listedIn already shows it")
    } else {
        act(rel, fields = { sent.forEach { (name, value) -> name to value } }, block = block)
    }
}

private const val ITEM = "item"
