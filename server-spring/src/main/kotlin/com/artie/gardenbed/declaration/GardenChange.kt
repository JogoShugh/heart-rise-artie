package com.artie.gardenbed.declaration

import com.artie.heart.Relation

/**
 * Wednesday (From OpenAPI to HEART, part four): one more kind of change a real
 * API makes, released on its own from Monday's garden, chosen by
 * `garden.change` (GARDEN_CHANGE). Each changes one thing about watering.
 * With none, the declaration is Monday's.
 */
enum class GardenChange(val property: String) {
    /** The water act's target moves: `/actions/water-cell` becomes `/actions/water`. */
    MOVE_URL("move-url"),

    /** The state member `moistureLevel` is renamed `soilMoisture`. */
    RENAME_FIELD("rename-field"),

    /** The water act's relation `garden:water-cell` becomes `garden:irrigate`; its title and target stay. */
    RENAME_ACT("rename-act"),

    /** The water act's title "Water the bed" becomes "Irrigate the bed". */
    RETITLE("retitle"),

    /** Watering takes at most 500 ml per cell, not 1000. */
    TIGHTER_LIMIT("tighter-limit"),

    /** Watering also requires a method, drip or spray. */
    NEW_REQUIRED_INPUT("new-required-input"),
    ;

    /** The garden's vocabulary as this change leaves it: the water relation renamed, or retitled. */
    fun alter(relation: Relation): Relation = when {
        relation.id != "water-cell" -> relation
        this == RENAME_ACT -> relation.copy(id = "irrigate")
        this == RETITLE -> relation.copy(title = "Irrigate the bed")
        else -> relation
    }

    companion object {
        /** The change [property] names, or null for none; an unknown one stops the garden at the start. */
        fun of(property: String?): GardenChange? =
            property?.takeIf { it.isNotBlank() }?.let { name ->
                entries.firstOrNull { it.property == name } ?: throw IllegalArgumentException(
                    "unknown garden.change '$name'; known: ${entries.joinToString { it.property }}",
                )
            }
    }
}
