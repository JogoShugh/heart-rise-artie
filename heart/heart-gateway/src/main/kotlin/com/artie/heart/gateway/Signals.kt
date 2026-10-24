package com.artie.heart.gateway

import com.dashjoin.jsonata.Jsonata
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.ObjectMapper

/** A name an upstream uses for an action, which may differ from its operationId. */
typealias UpstreamName = String

/** Upstream names mapped to operationIds where they differ; a name not here is its own operationId. */
typealias NameMap = Map<UpstreamName, OperationId>

private fun NameMap.operation(name: UpstreamName): OperationId = this[name] ?: name

/** A field holding the names of the actions allowed now: `"allowedActions": ["open", "lock"]`. */
class NamesList(private val at: JsonPointer, private val names: NameMap) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks) =
        Allowed(body.at(at).map { names.operation(it.asText()) }.toSet())
}

/** A map of booleans, one per action: `"capabilities": {"canOpen": true, "canLock": false}` (Google Drive, Box). */
class BooleanMap(private val at: JsonPointer, private val names: NameMap) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks) = Allowed(
        body.at(at).fields().asSequence().filter { it.value.asBoolean() }.map { names.operation(it.key) }.toSet(),
    )
}

/** An array of links, one per action allowed now, named by `rel`: `"links": [{"rel": "open", ...}]` (PayPal). */
class LinkArray(private val at: JsonPointer, private val names: NameMap) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks) =
        Allowed(body.at(at).mapNotNull { it.path("rel").asText(null) }.map { names.operation(it) }.toSet())
}

/**
 * OData 4.01's advertised actions: `"#Doors.open": {}` is available and
 * `"#Doors.lock": null` is not, for this entity, in [namespace], or in any
 * namespace when it is null. It is a standard, so the gateway also reads it
 * with no connector.
 */
class ODataActions(private val namespace: String?, private val names: NameMap = emptyMap()) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks) = Allowed(
        body.fields().asSequence()
            .filter { it.value.isObject }
            .mapNotNull { action(it.key) }
            .map { names.operation(it) }
            .toSet(),
    )

    /** Whether [body] advertises any action, available or not. */
    fun advertises(body: UpstreamBody): Boolean = body.fieldNames().asSequence().any { action(it) != null }

    /** Whether a member of the body is an advertisement: a control, not state. */
    fun isAdvertisement(member: String): Boolean = action(member) != null

    private fun action(member: String): String? {
        val match = ANNOTATION.matchEntire(member) ?: return null
        return match.groupValues[2].takeIf { namespace == null || match.groupValues[1] == namespace }
    }

    private companion object {
        /** `#Namespace.Action`, the namespace itself perhaps dotted. */
        val ANNOTATION = Regex("""^#([A-Za-z_][\w.]*)\.([A-Za-z_]\w*)$""")
    }
}

/** The same acts in every state, as for a GitHub comment, which has none; the reader's rules take some away. */
class Always(val allow: AllowedOperations) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks) = Allowed(allow)
}

/** One row of a state table: what a value of the state field allows, and why the rest are absent. */
class TableRow(val allow: AllowedOperations, val absent: AbsenceReasons = emptyMap())

/**
 * The rules written down, for an upstream that says nothing: the value of
 * one field, such as `status`, chooses a row. It is early binding, in the
 * gateway, and can drift from the upstream's own rules.
 */
class StateTable(private val at: JsonPointer, private val rows: Map<String, TableRow>) : AllowedNow {
    override fun of(body: UpstreamBody, links: UpstreamLinks): Allowed {
        val value = body.at(at).asText()
        val row = checkNotNull(rows[value]) { "the state table has no row for $at = $value" }
        return Allowed(row.allow, row.absent)
    }
}

/**
 * A JSONata expression, for a signal the others don't cover: from the
 * answer's body to the operationIds allowed now. Each evaluation is bounded
 * in time and depth, and JSONata has no I/O.
 */
class JsonataSignal(expression: String, private val mapper: ObjectMapper) : AllowedNow {
    private val compiled = Jsonata.jsonata(expression)

    override fun of(body: UpstreamBody, links: UpstreamLinks): Allowed {
        val frame = compiled.createFrame().apply { setRuntimeBounds(TIMEOUT_MILLIS, MAX_DEPTH) }
        val result = compiled.evaluate(mapper.convertValue(body, Any::class.java), frame)
        val operations = when (result) {
            null -> emptyList()
            is List<*> -> result.map { it.toString() }
            else -> listOf(result.toString())
        }
        return Allowed(operations.toSet())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 100L
        const val MAX_DEPTH = 64
    }
}
