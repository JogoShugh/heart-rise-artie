package com.artie.gardenbed.declaration

import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation

/**
 * The garden's vocabulary (SPEC §6.1, §8.5): six relations, served as ALPS at
 * `/profiles/garden` and named in forms by the CURIE `garden:<id>`; and why
 * each act is absent in each status (SPEC §6.12). A garden with frost
 * (garden.frost.enabled) adds a seventh, thaw, and the frozen bed's reasons.
 */
object GardenProfile {

    /** The vocabulary; without frost it is Monday's, exactly. */
    fun profile(frost: Boolean = false, change: GardenChange? = null) = Profile(
        name = "garden",
        title = "Garden beds",
        doc = "The acts and searches a garden-bed service offers. A descriptor says what a " +
            "relation means; only a resource's current manifest says whether it is offered now.",
        relations = relations.filter { frost || it.id != "thaw" }.map { change?.alter(it) ?: it },
    )

    /**
     * Why act [name] is absent in [status]. A function of status alone: no
     * instance identifiers, so every bed in one state shares it. Thaw is
     * declared only with frost, so only then is its absence asked for.
     */
    fun whyAbsent(status: GardenBedStatus, name: String): String =
        if (name == "thaw") "The bed is not frozen." else reasons.getValue(status).let { it[name] ?: it.getValue("") }

    private val relations = listOf(
        Relation(
            id = "build-bed",
            type = AlpsType.UNSAFE,
            title = "Build a bed",
            description = "Builds a new, empty bed with a name and a size in feet.",
            doc = "Offered by the service's bookmark, always. The new bed is empty and can be sown; its " +
                "name and size never change.",
        ),
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
                "harvest. Water that would raise moisture past 100 is refused; harvesting leaves the bed dry.",
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
        // Only a garden with frost declares it.
        Relation(
            id = "thaw",
            type = AlpsType.UNSAFE,
            title = "Thaw the bed",
            description = "Covers a frozen bed with fleece. The bed becomes planted again.",
            doc = "Covers the bed with fleece until it thaws. Offered only while the bed is frozen. Afterwards " +
                "the bed is planted, with its crop, and can be watered again.",
        ),
    )

    /** Each status's reasons, by act name; "" is the reason for every other act. */
    private val reasons: Map<GardenBedStatus, Map<String, String>> = mapOf(
        GardenBedStatus.EMPTY to mapOf(
            "waterCell" to "Nothing is planted yet; sow a seed first.",
            "" to "Nothing is growing yet; sow a seed and water it until it is ready.",
        ),
        GardenBedStatus.PLANTED to mapOf(
            "sowSeed" to "The bed is already planted.",
            "" to "The crop is not ready yet; it needs more water to grow.",
        ),
        GardenBedStatus.READY_TO_HARVEST to mapOf(
            "sowSeed" to "The bed is still full; harvest the crop first.",
            "" to "The crop is ready; it needs harvesting, not more water.",
        ),
        GardenBedStatus.FROZEN to mapOf(
            "waterCell" to "The bed is frozen; watering now would split the roots. Thaw it first.",
            "sowSeed" to "The bed is frozen with a crop in it; thaw it first.",
            "" to "The crop is frozen and not ready; thaw it first.",
        ),
    )
}
