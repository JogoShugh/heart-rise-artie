package com.artie.heart

/**
 * Escaping for the HTML heart-core writes. Every word a page shows comes from a server's state, its declaration,
 * or, through a gateway, an upstream's description, so every one is written as text, never as markup.
 */
internal object Html {
    /** [value] as element text. */
    fun text(value: Any?): String = value.toString()
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** [value] as an attribute value, inside double quotes. */
    fun attr(value: Any?): String = text(value).replace("\"", "&quot;").replace("'", "&#39;")

    /** `name="value"`, escaped, or nothing when [value] is null. */
    fun attr(name: String, value: Any?): String = if (value == null) "" else " $name=\"${attr(value)}\""

    /** A token safe in an id: `home:dim` is `home-dim`. */
    fun token(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]+"), "-")
}
