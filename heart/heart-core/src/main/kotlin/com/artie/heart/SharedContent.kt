package com.artie.heart

/**
 * The content-addressed resources of every declared type, as one server
 * serves them (SPEC §6.5, §6.12): address -> body, for each kind. Types may
 * share a body, which is then held once; two different bodies with one
 * address are refused when the server starts.
 */
class SharedContent(layers: List<Layers<*, *>>) {
    val affordanceSets: Map<String, String> = named(layers.flatMap { it.affordanceSets.values })
    val schemas: Map<String, String> = named(layers.flatMap { it.schemas.values })
    val explanations: Map<String, String> = named(layers.flatMap { it.explanations.values })

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
