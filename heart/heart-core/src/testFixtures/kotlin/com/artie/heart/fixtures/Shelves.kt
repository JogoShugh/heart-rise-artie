package com.artie.heart.fixtures

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** A shelf in a shop: a resource type with an act for each method an act may be served by. */
data class Shelf(val id: Long, val label: String, val price: Int, val tags: List<String>, val version: Int)

@Schema(title = "Restock")
data class Restock(
    @field:NotNull @field:Min(1) @field:Schema(title = "Count", description = "How many to add")
    val count: Int? = null,
)

@Schema(title = "Relabel")
data class Relabel(
    @field:NotBlank @field:Schema(title = "Label", description = "The shelf's new label")
    val label: String? = null,
)

@Schema(title = "Set the price")
data class SetPrice(
    @field:NotNull @field:Min(0) @field:Schema(title = "Price", description = "In cents")
    val price: Int? = null,
)

@Schema(title = "Remove a tag")
data class RemoveTag(
    @field:NotBlank @field:Schema(title = "Tag", description = "The tag to remove")
    val tag: String? = null,
    @field:Min(1) @field:Schema(title = "Count", description = "How many of its items to untag")
    val count: Int? = null,
)

/** Retiring a shelf takes no input. */
@Schema(title = "Retire")
class Retire

/** The shelf resource type: restock is a POST, relabel a PATCH, set-price a PUT, remove-tag and retire DELETEs. */
object Shelves {
    fun profile(name: String = "store", tryIt: AlpsType = AlpsType.UNSAFE) = Profile(
        name = name,
        title = "Shelves",
        doc = "The acts a shelf offers.",
        relations = listOf(
            Relation("restock", AlpsType.UNSAFE, "Restock", "Adds items.", "Offered while stocked."),
            Relation("relabel", AlpsType.UNSAFE, "Relabel", "Changes the label.", "Offered while stocked."),
            Relation("set-price", AlpsType.IDEMPOTENT, "Set the price", "Sets the price.", "Offered while stocked."),
            Relation("remove-tag", AlpsType.IDEMPOTENT, "Remove a tag", "Removes a tag.", "Offered while stocked."),
            Relation("retire", AlpsType.IDEMPOTENT, "Retire", "Retires the shelf.", "Offered while stocked."),
            Relation("try-it", tryIt, "Try it", "A relation a declaration test gives any type.", "Never offered."),
            Relation("search-shelves", AlpsType.SAFE, "Find shelves", "Lists shelves by label.", "A search."),
            Relation("get-shelf", AlpsType.SAFE, "Get a shelf", "Goes to one shelf.", "A lookup."),
        ),
    )

    val acts: List<Act> = listOf(
        Act.of("restock", "restock", Restock::class.java),
        Act.of("relabel", "relabel", Relabel::class.java, method = Act.PATCH),
        Act.of("setPrice", "set-price", SetPrice::class.java, method = Act.PUT),
        Act.of("removeTag", "remove-tag", RemoveTag::class.java, method = Act.DELETE),
        Act.of("retire", "retire", Retire::class.java, method = Act.DELETE),
    )

    fun type(
        profile: Profile = profile(),
        more: List<Act> = emptyList(),
        bookmarkActs: List<Act> = emptyList(),
    ): ResourceType<Shelf, String> = ResourceType(
        collection = "shelves",
        profile = profile,
        acts = acts + more,
        offers = Offers(
            keys = listOf("STOCKED"),
            offered = { acts.map { it.name } },
            whyAbsent = { _, _ -> "Never offered." },
        ),
        state = StateView(
            keyOf = { "STOCKED" },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.id, "label" to it.label, "price" to it.price, "tags" to it.tags) },
            bodyClass = Shelf::class.java,
            keyField = "label",
        ),
        bookmarkActs = bookmarkActs,
        finding = if (bookmarkActs.isEmpty()) null else com.artie.heart.Finding(
            noun = "shelf",
            search = com.artie.heart.Named("searchShelves", "search-shelves"),
            lookup = com.artie.heart.Named("getShelf", "get-shelf"),
            nameDescription = "Part of the shelf's label",
            idTitle = "Shelf id",
            idDescription = "The shelf's number",
        ),
    )

    /** Shelf [id], stocked, labelled "Tea", at 450 cents, tagged "sale". */
    fun stocked(id: Long) = Shelf(id, "Tea", 450, listOf("sale"), version = 1)
}
