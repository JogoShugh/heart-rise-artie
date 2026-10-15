package com.artie.heart

/**
 * The parts of the OpenAPI description every HEART server shares: the shapes
 * heart-core itself gives a search page, an event and a refusal, the
 * responses that name them, and the credentials every service admits.
 */
internal object OpenApiShapes {
    const val JSON_SCHEMA_2020_12 = "https://json-schema.org/draft/2020-12/schema"
    private const val JSON = "application/json"
    private const val PROBLEM_JSON = "application/problem+json"

    val ID: Map<String, Any> = mapOf("type" to "integer", "minimum" to 1)

    val LOCATION: Map<String, Any> =
        mapOf("description" to "Where the new resource is", "schema" to mapOf("type" to "string"))

    /** The Link header every plain answer about a resource carries: its state's affordance set. */
    val FORMS_LINK: Map<String, Any> = mapOf(
        "description" to "The affordance set of the state this answer shows, by the relation " +
            "$HEART_RISE_PROFILE/rels/forms: the acts it allows now (see x-heart-affordances)",
        "schema" to mapOf("type" to "string"),
    )

    /** A response about one resource: with [linked], it carries the Link to its state's affordance set. */
    fun withFormsLink(response: Map<String, Any>, linked: Boolean): Map<String, Any> {
        if (!linked) return response
        @Suppress("UNCHECKED_CAST")
        val headers = response["headers"] as? Map<String, Any> ?: emptyMap()
        return response + ("headers" to headers + ("Link" to FORMS_LINK))
    }

    /** How a client uses [FORMS_LINK]: follow it, and keep the operations whose method and path a form names. */
    val AFFORDANCES_EXTENSION: Map<String, Any> = mapOf(
        "header" to "Link",
        "rel" to "$HEART_RISE_PROFILE/rels/forms",
        "match" to "method and path template",
    )

    val SECURITY_SCHEMES: Map<String, Any> = mapOf(
        "basic" to mapOf("type" to "http", "scheme" to "basic"),
        "bearer" to mapOf("type" to "http", "scheme" to "bearer"),
    )

    val SCHEMAS: Map<String, Any> = mapOf(
        "SearchPage" to obj(
            "count" to mapOf("type" to "integer", "minimum" to 0),
            "items" to mapOf(
                "type" to "array",
                "items" to obj("id" to ID, "title" to mapOf("type" to "string")),
            ),
        ),
        "HistoryEvent" to obj(
            "sequenceNo" to ID,
            "type" to mapOf("type" to "string"),
            "occurredAt" to mapOf("type" to "string", "format" to "date-time"),
            "event" to mapOf("type" to "object"),
        ),
        // RFC 9457, with HEART's members: the relation of an act not offered, and each failing field.
        "Problem" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "type" to mapOf("type" to "string", "format" to "uri"),
                "title" to mapOf("type" to "string"),
                "status" to mapOf("type" to "integer"),
                "detail" to mapOf("type" to "string"),
                "instance" to mapOf("type" to "string"),
                "rel" to mapOf("type" to "string"),
                "violations" to mapOf(
                    "type" to "array",
                    "items" to obj("pointer" to mapOf("type" to "string"), "message" to mapOf("type" to "string")),
                ),
            ),
        ),
        "Error" to obj("error" to mapOf("type" to "string")),
    )

    val RESPONSES: Map<String, Any> = mapOf(
        "BadRequest" to content("The request could not be read", JSON, ref("Error")),
        "Unauthorized" to content("A Basic or Bearer credential the service admits is needed", PROBLEM_JSON),
        "NotFound" to content("No resource with that id", JSON, ref("Error")),
        "NotOffered" to content("The act is not allowed in the resource's current state", PROBLEM_JSON),
        "Unprocessable" to content(
            "The body breaks the act's schema, or a rule of the resource refuses it",
            PROBLEM_JSON,
        ),
    )

    fun ref(name: String): Map<String, Any> = mapOf("\$ref" to "#/components/schemas/$name")

    fun response(name: String): Map<String, Any> = mapOf("\$ref" to "#/components/responses/$name")

    fun json(description: String, schema: Map<String, Any>): Map<String, Any> = content(description, JSON, schema)

    fun query(name: String, description: String, schema: Map<String, Any>): Map<String, Any> =
        mapOf("name" to name, "in" to "query", "required" to false, "description" to description, "schema" to schema)

    fun idParameter(noun: String): Map<String, Any> =
        mapOf("name" to "id", "in" to "path", "required" to true, "description" to "The $noun's id", "schema" to ID)

    fun requestBody(schema: String): Map<String, Any> =
        mapOf("required" to true, "content" to mapOf(JSON to mapOf("schema" to ref(schema))))

    private fun content(description: String, mediaType: String, schema: Map<String, Any> = ref("Problem")) =
        mapOf("description" to description, "content" to mapOf(mediaType to mapOf("schema" to schema)))

    /** An object whose every member is required. */
    private fun obj(vararg properties: Pair<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties.toMap(),
        "required" to properties.map { it.first },
    )
}
