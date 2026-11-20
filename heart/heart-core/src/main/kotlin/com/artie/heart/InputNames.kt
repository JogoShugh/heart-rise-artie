package com.artie.heart

/** The names an OpenAPI description gives acts' inputs among its schemas. */
internal object InputNames {
    private val NOT_A_LETTER_OR_DIGIT = Regex("[^A-Za-z0-9]+")

    /**
     * An act's input's name: its input class's, else the act's own. When
     * another type gives that name a different schema, it is [qualified] and
     * prefixed with the act's type's [collection]: `dimmer-banks` makes `DimmerBanksDim`.
     */
    fun of(act: Act, collection: String, qualified: Set<String>): String {
        val name = act.input?.simpleName ?: act.name.replaceFirstChar { it.uppercase() }
        if (name !in qualified) return name
        return collection.split(NOT_A_LETTER_OR_DIGIT).joinToString("") { it.replaceFirstChar(Char::uppercase) } + name
    }
}
