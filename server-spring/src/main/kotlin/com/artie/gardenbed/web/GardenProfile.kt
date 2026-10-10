package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation

/**
 * The garden domain's link relations (SPEC §6.1, §8.5; HAL-SCHEMA-FORMS-HR
 * Rules 6 and 7), as a heart-core [Profile]. In HAL Schema Forms each form is
 * keyed by a relation, written as the CURIE `garden:<id>`, which expands to
 * `/profiles/garden#<id>`: a descriptor in this server's ALPS profile
 * document. HAL-FORMS keeps the logical names (`sowSeed`) as template names,
 * so this object also maps each logical name to its relation.
 */
object GardenProfile {
    const val PATH = "/profiles/garden"
    const val CURIE = "garden"
    const val ALPS_MEDIA_TYPE = Profile.ALPS_MEDIA_TYPE

    val profile = Profile(
        name = CURIE,
        title = "Garden beds",
        doc = "The acts and searches a garden-bed service offers. A descriptor says what a " +
            "relation means; only a resource's current manifest says whether it is offered now.",
        relations = listOf(
            Relation(
                id = "sow-seed",
                type = AlpsType.UNSAFE,
                title = "Sow a seed",
                description = "Plants a seed in an empty bed. The bed becomes planted.",
                doc = "Sows one kind of seed in the bed at an optional depth. Offered only while the bed is " +
                    "empty. Afterwards the bed is planted and can be watered.",
            ),
            Relation(
                id = "water-cell",
                type = AlpsType.UNSAFE,
                title = "Water the bed",
                description = "Pours water into every cell of a planted bed. Enough water makes it ready to harvest.",
                doc = "Adds the given millilitres of water to each cell. Offered only while the bed is planted. " +
                    "Each watering raises moisture and growth; once growth is high enough the bed is ready to " +
                    "harvest.",
            ),
            Relation(
                id = "harvest",
                type = AlpsType.UNSAFE,
                title = "Harvest the crop",
                description = "Picks the crop from a bed that is ready. The bed becomes empty again.",
                doc = "Harvests the whole crop and reports its yield. Offered only while the bed is ready to " +
                    "harvest. Afterwards the bed is empty and can be sown again.",
            ),
            Relation(
                id = "search-beds",
                type = AlpsType.SAFE,
                title = "Find beds by name",
                description = "Lists the beds whose name contains the text given, ignoring case.",
                doc = "A safe search. The result links to each matching bed under the item relation, titled " +
                    "with the bed's name, and pages long lists with next, prev, first and last.",
            ),
            Relation(
                id = "get-bed",
                type = AlpsType.SAFE,
                title = "Get a bed by its id",
                description = "Goes to one bed, given its id.",
                doc = "A safe lookup. The result is the bed itself, with the acts its current state allows.",
            ),
        ),
    )

    /** Each logical name's relation id. */
    private val ids: Map<String, String> = mapOf(
        "sowSeed" to "sow-seed",
        "waterCell" to "water-cell",
        "harvest" to "harvest",
        "searchBeds" to "search-beds",
        "getBed" to "get-bed",
    )

    /** The `_links.curies` entry every representation with relation-named forms declares. */
    val CURIES: List<Map<String, Any>> = profile.curies

    fun relation(name: String): Relation = profile.relation(ids.getValue(name))

    /** The acts a bed can offer, by logical name. */
    val BED_ACTS: List<String> = listOf("sowSeed", "waterCell", "harvest")

    /**
     * Why an act is absent in a status (SPEC §6.12). A function of status
     * alone: no instance identifiers, so every bed in one state shares it.
     */
    fun whyAbsent(status: GardenBedStatus, name: String): String =
        when (status) {
            GardenBedStatus.EMPTY -> when (name) {
                "waterCell" -> "Nothing is planted yet; sow a seed first."
                else -> "Nothing is growing yet; sow a seed and water it until it is ready."
            }
            GardenBedStatus.PLANTED -> when (name) {
                "sowSeed" -> "The bed is already planted."
                else -> "The crop is not ready yet; it needs more water to grow."
            }
            GardenBedStatus.READY_TO_HARVEST -> when (name) {
                "sowSeed" -> "The bed is still full; harvest the crop first."
                else -> "The crop is ready; it needs harvesting, not more water."
            }
        }

    /** The form's key in `_forms`: the CURIE for [name]'s relation. */
    fun key(name: String): String = profile.key(ids.getValue(name))

    /** The relation as a URI reference, resolved against the request URL (used in 409 problem bodies). */
    fun reference(name: String): String = profile.reference(ids.getValue(name))

    /** The ALPS profile document (draft-amundsen-richardson-foster-alps). */
    val alps: Map<String, Any> = profile.alps
}
