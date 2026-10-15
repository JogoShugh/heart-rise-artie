package com.artie.heart

/**
 * The first question a client asks of an API, "what can ever be done with
 * it?", answered once, as OpenAPI 3.1. HEART makes it answerable: every act
 * is declared, and every offer key is listed in advance (SPEC §6.9), so the
 * union of every affordance set a server could ever offer is known before
 * any request.
 *
 * The document describes the plain JSON rendering, the OpenAPI twin: one
 * operation per act, with the act's own schema in components; the state's
 * schema, with its offer key as a closed enum of every declared key; and the
 * rule about state where OpenAPI can carry it, in prose, in each act's
 * description. It is JCS canonical JSON, so the same declarations at the
 * same [version] always give the same bytes. With [affordanceLinks], each
 * answer about a resource documents the Link to its state's affordance set,
 * and the `x-heart-affordances` extension says how to use it.
 */
class OpenApiDocument(
    resources: List<HeartResource<*, *>>,
    title: String? = null,
    val version: String = DEFAULT_VERSION,
    affordanceLinks: Boolean = false,
) {
    /** The document, as the exact canonical text served. */
    val body: String

    /** The document's validator: it changes only when the body does. */
    val validator: String

    init {
        require(resources.isNotEmpty()) { "an OpenAPI description needs at least one resource type" }
        val described = resources.map { OpenApiType(it.layers.type, keepsPast = it.past != null, affordanceLinks) }
        val types = resources.map { it.layers.type }
        body = ContentAddress.canonical(
            mapOf(
                "openapi" to OPENAPI,
                "jsonSchemaDialect" to OpenApiShapes.JSON_SCHEMA_2020_12,
                "info" to mapOf(
                    "title" to (title ?: types.first().profile.title),
                    "version" to version,
                    "description" to DESCRIPTION,
                ),
                "servers" to listOf(mapOf("url" to "/")),
                "security" to OpenApiShapes.SECURITY_SCHEMES.keys.map { mapOf(it to emptyList<String>()) },
                "tags" to types.map { mapOf("name" to it.collection, "description" to it.profile.doc) },
                "paths" to described.flatMap { it.paths().entries }.associate { it.key to it.value },
                "components" to mapOf(
                    "schemas" to OpenApiShapes.SCHEMAS + schemas(described),
                    "responses" to OpenApiShapes.RESPONSES,
                    "securitySchemes" to OpenApiShapes.SECURITY_SCHEMES,
                ),
            ) + affordances(affordanceLinks),
        )
        validator = "\"openapi-${ContentAddress.address(body)}\""
    }

    /** The extension that says how to use each answer's affordance link, when there is one. */
    private fun affordances(linked: Boolean): Map<String, Any> =
        if (linked) mapOf("x-heart-affordances" to OpenApiShapes.AFFORDANCES_EXTENSION) else emptyMap()

    /** Every type's schemas by name. Two acts may share an input class, and so one schema; never one name for two. */
    private fun schemas(described: List<OpenApiType>): Map<String, Any?> =
        described.flatMap { it.schemas().entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (name, all) ->
                require(all.distinct().size == 1) { "two different schemas are named $name" }
                all.first()
            }

    companion object {
        /** Where a binding serves the document, linked from the bookmark as `service-desc` (RFC 8631). */
        const val PATH = "/openapi.json"
        const val MEDIA_TYPE = "application/vnd.oai.openapi+json;version=3.1"
        const val DEFAULT_VERSION = "1.0.0"
        private const val OPENAPI = "3.1.1"
        private const val DESCRIPTION =
            "Every operation this service could ever be asked, generated from its HEART declarations. " +
                "It describes the plain JSON rendering (Accept: application/json). Which acts a resource " +
                "allows depends on its state at the time, which this document can only describe in prose."
    }
}
