package com.artie.heart.bdd

import com.artie.heart.Embedded
import com.artie.heart.Hypermedia
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.fixtures.PullRequest
import com.artie.heart.fixtures.PullRequests
import com.fasterxml.jackson.databind.JsonNode

/** The pull request type and the pull requests of one scenario, shared by every step class. */
class PullWorld {
    lateinit var template: String
    lateinit var layers: Layers<PullRequest, String>
    var subCollections: Map<String, String> = emptyMap()
    var embedded: List<Embedded> = emptyList()

    /** Links the state itself says, by relation: href and type. */
    var stateLinks: Map<String, Map<String, Any?>> = emptyMap()

    /** Resources the state itself embeds, by relation, each already a resource object. */
    var stateEmbedded: Map<String, Any?> = emptyMap()
    val pulls = mutableListOf<PullRequest>()
    val rendered = mutableListOf<JsonNode>()
    var refusal: Throwable? = null

    /** Declares the type again from what the scenario has said so far. */
    fun declare() {
        val itemPath = ItemPath(template, PullRequests.POINTERS)
        val hypermedia = Hypermedia(stateLinks, stateEmbedded)
        layers = Layers(
            PullRequests.type(itemPath, subCollections = subCollections, embedded = embedded, hypermedia = hypermedia),
        )
    }
}
