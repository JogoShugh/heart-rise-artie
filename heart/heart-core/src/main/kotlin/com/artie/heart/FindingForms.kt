package com.artie.heart

/**
 * A found type's search and lookup, as the service index offers them and the
 * home document names them: their targets, their schemas, and a variable for
 * each field. By default the search is by part of a name at `/{collection}`
 * and the lookup by id. A type that declares its collection path and its
 * fields ([Finding.collectionPath]) is searched by filling the collection
 * path, with its filters in the query, and looked up by filling its item path.
 */
internal class FindingForms(private val type: ResourceType<*, *>) {
    private val finding = checkNotNull(type.finding)
    private val collectionPath = finding.collectionPath
    private val filters = finding.filters(type.itemPath).map { it.name }
    private val collectionVariables = collectionPath?.let { ItemPath.variablesOf(it) }.orEmpty()

    /** Whether the type declares its collection path, so its targets have variables a HAL-FORMS target cannot. */
    val declared: Boolean = collectionPath != null

    /** The search's target: `/beds{?name}`, or `/repos/{owner}/{repo}/pulls{?state}`. */
    val searchTarget: UriTemplate = when (collectionPath) {
        null -> "/${type.collection}{?name}"
        else -> collectionPath + if (filters.isEmpty()) "" else filters.joinToString(",", "{?", "}")
    }

    /** The lookup's target: the item path. */
    val lookupTarget: UriTemplate = type.itemPath.template

    /** The search's fields; the collection path's variables are required. */
    val searchSchema: Map<String, Any> = when (collectionPath) {
        null -> schema(mapOf("name" to nameProperty()), listOf("name"))
        else -> schema(declaredProperties(collectionVariables + filters), collectionVariables)
    }

    /** The lookup's fields, all required: the item path's variables. */
    val lookupSchema: Map<String, Any> = when (collectionPath) {
        null -> schema(mapOf("id" to idProperty()), listOf("id"))
        else -> schema(declaredProperties(type.itemPath.variables), type.itemPath.variables)
    }

    /** The home document's item template and a variable for each of its fields. */
    fun item(vars: String): Pair<UriTemplate, Map<String, String>> = when (collectionPath) {
        null -> "/${type.collection}/{id}" to mapOf("id" to "$vars/${finding.noun}-id")
        else -> lookupTarget to variables(vars, type.itemPath.variables)
    }

    /** The home document's collection template and a variable for each of its fields. */
    fun collection(vars: String): Pair<UriTemplate, Map<String, String>> = when (collectionPath) {
        null -> "/${type.collection}{?name,id}" to variables(vars, listOf("name", "id"))
        else -> searchTarget to variables(vars, collectionVariables + filters)
    }

    private fun variables(vars: String, fields: List<String>): Map<String, String> =
        fields.associateWith { "$vars/${finding.noun}-$it" }

    private fun declaredProperties(fields: List<String>): Map<String, Any> = fields.associateWith { name ->
        val field = finding.field(name)
        field.schema + mapOf("title" to field.title, "description" to field.description)
    }

    /** The search form's one field by default: part of a resource's name. */
    private fun nameProperty(): Map<String, Any> = mapOf(
        "type" to "string", "minLength" to 1, "title" to "Name", "description" to finding.nameDescription,
    )

    /** The lookup form's one field by default: a resource's id. */
    private fun idProperty(): Map<String, Any> = mapOf(
        "type" to "integer", "minimum" to 1, "title" to finding.idTitle, "description" to finding.idDescription,
    )

    private companion object {
        fun schema(properties: Map<String, Any>, required: List<String>): Map<String, Any> = mapOf(
            "\$schema" to "https://json-schema.org/draft/2020-12/schema",
            "type" to "object",
            "properties" to properties,
            "required" to required,
        )
    }
}
