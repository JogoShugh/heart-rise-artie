package com.artie.heart

/**
 * One declared resource type in the OpenAPI description: the paths it
 * serves, the same routes every HTTP binding builds from the declaration,
 * and the schemas they name.
 */
internal class OpenApiType(
    private val type: ResourceType<*, *>,
    private val keepsPast: Boolean,
    private val affordanceLinks: Boolean = false,
) {
    private val noun = type.finding?.noun ?: type.collection
    private val stateName = noun.replaceFirstChar { it.uppercase() }

    @Suppress("UNCHECKED_CAST")
    private val offers = type.offers as Offers<Any?>

    /** Each act's input schema, under its input class's name, and the state's schema, under the type's noun. */
    fun schemas(): Map<String, Any?> =
        type.allActs.associate { inputName(it) to JsonTrees.plain(it.schema) } + (stateName to stateSchema())

    fun paths(): Map<String, Any> = buildMap {
        val collection = "/${type.collection}"
        type.finding?.let { put(collection, mapOf("get" to search(it))) }
        put("$collection/{id}", mapOf("get" to read()))
        if (keepsPast) put("$collection/{id}/history", mapOf("get" to history()))
        type.acts.forEach { act ->
            put("$collection/{id}/actions/${act.path}", mapOf("post" to act(act)))
            // A path the act moved from still answers; it is its own operation, deprecated.
            act.formerPaths.forEach { former ->
                val operation = act(act) + mapOf("operationId" to "${act.name}Former", "deprecated" to true)
                put("$collection/{id}/actions/$former", mapOf("post" to operation))
            }
        }
        type.bookmarkActs.forEach { put("$ACTIONS${it.path}", mapOf("post" to bookmarkAct(it))) }
    }

    private fun search(finding: Finding): Map<String, Any> {
        val relation = type.profile.relation(finding.search.relation)
        return mapOf(
            "operationId" to finding.search.name,
            "tags" to listOf(type.collection),
            "summary" to relation.title,
            "description" to relation.description,
            "parameters" to listOf(
                OpenApiShapes.query("name", finding.nameDescription, mapOf("type" to "string", "minLength" to 1)),
                OpenApiShapes.query("id", finding.idDescription, OpenApiShapes.ID),
                OpenApiShapes.query("page", "The page of matches, from 1", OpenApiShapes.ID),
            ),
            "responses" to mapOf(
                "200" to OpenApiShapes.json("The matches, a page at a time", OpenApiShapes.ref("SearchPage")),
                "401" to OpenApiShapes.response("Unauthorized"),
            ),
        )
    }

    private fun read(): Map<String, Any> {
        val lookup = type.finding?.lookup
        val relation = lookup?.let { type.profile.relation(it.relation) }
        val asOf = OpenApiShapes.query(
            "asOf",
            "An ISO 8601 instant: the $noun as it was then",
            mapOf("type" to "string", "format" to "date-time"),
        )
        return mapOf(
            "operationId" to (lookup?.name ?: "get$stateName"),
            "tags" to listOf(type.collection),
            "summary" to (relation?.title ?: "Get a $noun"),
            "description" to (relation?.doc ?: "One $noun, by its id."),
            "parameters" to listOfNotNull(OpenApiShapes.idParameter(noun), asOf.takeIf { keepsPast }),
            "responses" to listOfNotNull(
                "200" to OpenApiShapes.withFormsLink(
                    OpenApiShapes.json("The $noun", OpenApiShapes.ref(stateName)),
                    affordanceLinks,
                ),
                ("400" to OpenApiShapes.response("BadRequest")).takeIf { keepsPast },
                "401" to OpenApiShapes.response("Unauthorized"),
                "404" to OpenApiShapes.response("NotFound"),
            ).toMap(),
        )
    }

    private fun history(): Map<String, Any> = mapOf(
        "operationId" to "get${stateName}History",
        "tags" to listOf(type.collection),
        "summary" to "What happened to the $noun",
        "description" to "Every event in the $noun's history, in order.",
        "parameters" to listOf(OpenApiShapes.idParameter(noun)),
        "responses" to mapOf(
            "200" to OpenApiShapes.json(
                "The $noun's events, oldest first",
                mapOf("type" to "array", "items" to OpenApiShapes.ref("HistoryEvent")),
            ),
            "401" to OpenApiShapes.response("Unauthorized"),
            "404" to OpenApiShapes.response("NotFound"),
        ),
    )

    private fun act(act: Act): Map<String, Any> {
        val relation = type.profile.relation(act.relation)
        return mapOf(
            "operationId" to act.name,
            "tags" to listOf(type.collection),
            "summary" to relation.title,
            "description" to "${relation.doc}\n\n${onlyWhen(act)}",
            "parameters" to listOf(OpenApiShapes.idParameter(noun)),
            "requestBody" to OpenApiShapes.requestBody(inputName(act)),
            "responses" to mapOf(
                "200" to OpenApiShapes.withFormsLink(
                    OpenApiShapes.json("The $noun after the act", OpenApiShapes.ref(stateName)),
                    affordanceLinks,
                ),
                "401" to OpenApiShapes.response("Unauthorized"),
                "404" to OpenApiShapes.response("NotFound"),
                "409" to OpenApiShapes.response("NotOffered"),
                "422" to OpenApiShapes.response("Unprocessable"),
            ),
        )
    }

    private fun bookmarkAct(act: Act): Map<String, Any> {
        val relation = type.profile.relation(act.relation)
        val created = OpenApiShapes.withFormsLink(
            OpenApiShapes.json("The new $noun", OpenApiShapes.ref(stateName)) +
                ("headers" to mapOf("Location" to OpenApiShapes.LOCATION)),
            affordanceLinks,
        )
        return mapOf(
            "operationId" to act.name,
            "tags" to listOf(type.collection),
            "summary" to relation.title,
            "description" to relation.doc,
            "requestBody" to OpenApiShapes.requestBody(inputName(act)),
            "responses" to mapOf(
                "201" to created,
                "401" to OpenApiShapes.response("Unauthorized"),
                "422" to OpenApiShapes.response("Unprocessable"),
            ),
        )
    }

    /** The rule about state, as the only thing OpenAPI has for it: a sentence. */
    private fun onlyWhen(act: Act): String {
        val keys = offers.keys.filter { act.name in offers.offered(it) }.map { it.toString() }
        val field = type.state.keyField ?: "the state"
        return when (keys.size) {
            0 -> "Never allowed in any declared state."
            1 -> "Only allowed when $field is ${keys.single()}."
            else -> "Only allowed when $field is ${keys.dropLast(1).joinToString(", ")} or ${keys.last()}."
        }
    }

    /** The state's schema: from its body class when declared, with the offer key a closed enum of every key. */
    private fun stateSchema(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val declared = type.state.bodyClass?.let { JsonTrees.plain(InputSchemas.of(it)) as Map<String, Any?> }
            ?: mapOf("type" to "object", "properties" to mapOf("id" to OpenApiShapes.ID), "required" to listOf("id"))
        val field = type.state.keyField ?: return declared
        @Suppress("UNCHECKED_CAST")
        val properties = declared["properties"] as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val key = (properties[field] as? Map<String, Any?>).orEmpty() +
            mapOf("type" to "string", "enum" to offers.keys.map { it.toString() })
        return declared + ("properties" to properties + (field to key))
    }

    private fun inputName(act: Act): String = act.input?.simpleName ?: act.name.replaceFirstChar { it.uppercase() }
}
