package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.ContentAddress
import com.artie.heart.Rung
import com.artie.heart.SetRendering

/**
 * The hal-schema-forms dialect for garden beds, as the controllers and the
 * CoAP binding read it. heart-core's [com.artie.heart.Layers] renders the
 * three layers (SPEC §6.4) from the [GardenBeds] declaration; this object
 * keys its content-addressed bodies by the hex part of their address, as the
 * `/…/sha256-{hash}` routes take it. It goes when the routes come from the
 * library (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, step A5.6).
 */
object JsonSchemaDialectSupport {
    private val layers = GardenBeds.layers

    private fun byHash(bodies: Map<String, String>): Map<String, String> =
        bodies.mapKeys { (address, _) -> address.removePrefix(PREFIX) }

    // The CoAP binding reads these three by hash; over HTTP, heart-spring serves them.

    /** hash (no "sha256-" prefix) -> canonical schema body. */
    val schemasByHash: Map<String, String> = byHash(layers.schemas)

    /** hash (no "sha256-" prefix) -> canonical explanation JSON. */
    val unavailableByHash: Map<String, String> = byHash(layers.explanations)

    /** hash (no "sha256-" prefix) -> canonical set JSON. */
    val affordanceSetsByHash: Map<String, String> = byHash(layers.affordanceSets)

    /** Which acts a status offers: one source for both dialects, so they cannot drift apart. */
    fun allowedAffordances(status: GardenBedStatus): List<String> = layers.offered(status)

    fun affordanceSetHashFor(status: GardenBedStatus, rendering: SetRendering): String =
        ContentAddress.address(affordanceSetJson(status, rendering)).removePrefix(PREFIX)

    fun affordanceSetJson(status: GardenBedStatus, rendering: SetRendering): String =
        layers.affordanceSet(status, rendering)

    /** Layer 1 of a bed at [rung] (SPEC §6.4). */
    fun buildBedBody(state: GardenBedState, rung: Rung = Rung.L0): Map<String, Any?> = layers.layer1(state, rung)

    /** RFC 9530 Repr-Digest for a Layer 2 or Layer 3 body, in RFC 8941 byte-sequence syntax. */
    fun reprDigestOf(body: String): String = ContentAddress.reprDigest(body)

    private const val PREFIX = "sha256-"
}
