package com.artie.heart

/**
 * The content-addressed resources of every declared type, as one server
 * serves them (SPEC §6.5, §6.12): address -> body, for each kind. Types may
 * share a body, which is then held once; two different bodies with one
 * address are refused when the server starts.
 */
class SharedContent(layers: List<Layers<*, *>>) {
    /** With open offers, a set first rendered after start is found in the type that rendered it. */
    val affordanceSets: Map<String, String> =
        Growing(named(layers.flatMap { it.affordanceSets.values })) { layers.map { it.affordanceSets } }
    val schemas: Map<String, String> = named(layers.flatMap { it.schemas.values })
    val explanations: Map<String, String> =
        Growing(named(layers.flatMap { it.explanations.values })) { layers.map { it.explanations } }

    /**
     * The bodies held at start, checked for clashes then, and any each type
     * has held since. A type checks its own later bodies as it holds them.
     */
    private class Growing(
        private val atStart: Map<String, String>,
        private val now: () -> List<Map<String, String>>,
    ) : AbstractMap<String, String>() {
        override val entries: Set<Map.Entry<String, String>>
            get() = (now().fold(emptyMap<String, String>()) { all, more -> all + more } + atStart).entries

        override fun get(key: String): String? = atStart[key] ?: now().firstNotNullOfOrNull { it[key] }

        override fun containsKey(key: String): Boolean = get(key) != null
    }

    companion object {
        /**
         * [bodies] by [name], which is their address unless given. One body
         * met twice is one entry. Two different bodies with one name are
         * refused, so a clash is never a body silently dropped.
         */
        fun named(bodies: Iterable<String>, name: (String) -> String = ContentAddress::address): Map<String, String> =
            bodies.distinct().groupBy(name).mapValues { (key, same) ->
                check(same.size == 1) { "two different bodies share the name $key" }
                same.single()
            }
    }
}
