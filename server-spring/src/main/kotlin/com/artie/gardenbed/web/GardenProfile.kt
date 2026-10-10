package com.artie.gardenbed.web

/**
 * The garden domain's link relations (SPEC §6.1, §8.5; HAL-SCHEMA-FORMS-HR
 * Rules 6 and 7). In HAL Schema Forms each form is keyed by a relation,
 * written as the CURIE `garden:<id>`, which expands to
 * `/profiles/garden#<id>`: a descriptor in this server's ALPS profile
 * document. The form carries a short title and description; the ALPS
 * descriptor says more. HAL-FORMS keeps the logical names as template names.
 *
 * One table serves the forms, the index, the ALPS document and 409 problem
 * bodies, so they cannot disagree about what a relation is called or means.
 */
object GardenProfile {
    const val PATH = "/profiles/garden"
    const val CURIE = "garden"
    const val ALPS_MEDIA_TYPE = "application/alps+json"

    /** The `_links.curies` entry every representation with relation-named forms declares. */
    val CURIES: List<Map<String, Any>> = listOf(mapOf("name" to CURIE, "href" to "$PATH#{rel}", "templated" to true))

    data class Relation(
        val id: String,
        /** ALPS descriptor type: safe, unsafe or idempotent. */
        val alpsType: String,
        val title: String,
        /** One or two sentences, shown inline on the form. */
        val description: String,
        /** The ALPS descriptor's doc: what the act does, the state it leads to, when it is offered. */
        val doc: String,
    )

    private val relations: Map<String, Relation> = mapOf(
        "sowSeed" to Relation(
            id = "sow-seed",
            alpsType = "unsafe",
            title = "Sow a seed",
            description = "Plants a seed in an empty bed. The bed becomes planted.",
            doc = "Sows one kind of seed in the bed at an optional depth. Offered only while the bed is " +
                "empty. Afterwards the bed is planted and can be watered.",
        ),
        "waterCell" to Relation(
            id = "water-cell",
            alpsType = "unsafe",
            title = "Water the bed",
            description = "Pours water into every cell of a planted bed. Enough water makes it ready to harvest.",
            doc = "Adds the given millilitres of water to each cell. Offered only while the bed is planted. " +
                "Each watering raises moisture and growth; once growth is high enough the bed is ready to " +
                "harvest.",
        ),
        "harvest" to Relation(
            id = "harvest",
            alpsType = "unsafe",
            title = "Harvest the crop",
            description = "Picks the crop from a bed that is ready. The bed becomes empty again.",
            doc = "Harvests the whole crop and reports its yield. Offered only while the bed is ready to " +
                "harvest. Afterwards the bed is empty and can be sown again.",
        ),
        "searchBeds" to Relation(
            id = "search-beds",
            alpsType = "safe",
            title = "Find beds by name",
            description = "Lists the beds whose name contains the text given, ignoring case.",
            doc = "A safe search. The result links to each matching bed under the item relation, titled " +
                "with the bed's name, and pages long lists with next, prev, first and last.",
        ),
        "getBed" to Relation(
            id = "get-bed",
            alpsType = "safe",
            title = "Get a bed by its id",
            description = "Goes to one bed, given its id.",
            doc = "A safe lookup. The result is the bed itself, with the acts its current state allows.",
        ),
    )

    fun relation(name: String): Relation = relations.getValue(name)

    /** The form's key in `_forms`: the CURIE for [name]'s relation. */
    fun key(name: String): String = "$CURIE:${relation(name).id}"

    /** The relation as a URI reference, resolved against the request URL (used in 409 problem bodies). */
    fun reference(name: String): String = "$PATH#${relation(name).id}"

    /** The ALPS profile document (draft-amundsen-richardson-foster-alps). */
    val alps: Map<String, Any> = mapOf(
        "alps" to mapOf(
            "version" to "1.0",
            "title" to "Garden beds",
            "doc" to mapOf(
                "format" to "text",
                "value" to "The acts and searches a garden-bed service offers. A descriptor says what a " +
                    "relation means; only a resource's current manifest says whether it is offered now.",
            ),
            "descriptor" to relations.values.map { r ->
                mapOf(
                    "id" to r.id,
                    "type" to r.alpsType,
                    "title" to r.title,
                    "doc" to mapOf("format" to "text", "value" to r.doc),
                )
            },
        ),
    )
}
