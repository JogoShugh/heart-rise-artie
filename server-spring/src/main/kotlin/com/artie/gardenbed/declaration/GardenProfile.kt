package com.artie.gardenbed.declaration

import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation

/**
 * The garden's vocabulary (SPEC §6.1, §8.5): five relations, served as ALPS at
 * `/profiles/garden` and named in forms by the CURIE `garden:<id>`; and why
 * each act is absent in each status (SPEC §6.12).
 */
object GardenProfile {

    val profile = Profile(
        name = "garden",
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

    /**
     * Why act [name] is absent in [status]. A function of status alone: no
     * instance identifiers, so every bed in one state shares it.
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
}
