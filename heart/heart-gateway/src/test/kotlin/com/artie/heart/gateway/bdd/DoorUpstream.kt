package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.util.concurrent.ConcurrentHashMap

/** How a door upstream says what is allowed now, one way per connector under test. */
enum class Signal(val phrase: String) {
    NAMES("a names list"),
    BOOLEANS("a boolean map"),
    LINKS("a link array"),
    ODATA("OData actions"),
    TABLE("a state table"),
    JSONATA("a JSONata file"),
    ;

    companion object {
        fun of(phrase: String) = entries.first { it.phrase == phrase }
    }
}

/**
 * A small upstream that publishes only OpenAPI: doors, OPEN, CLOSED or
 * LOCKED. A closed door can be opened or locked, an open one closed, and a
 * locked one unlocked. Each answer says what is allowed now in [signal]'s
 * way, and an act not allowed is refused, 409, after it is sent.
 */
class DoorUpstream(private val signal: Signal) {
    val doors = ConcurrentHashMap<Long, String>()

    /** Operations whose description names the HEART relation they are (`x-heart-relation`), by operationId. */
    val relations = ConcurrentHashMap<String, String>()

    /** Vault doors say so in their body (`"kind": "vault-door"`) and in their media type. */
    val vaults: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val mapper = ObjectMapper()

    val handler: HttpHandler = ServerFilters.CatchAll().then(
        routes(
            "/openapi.json" bind Method.GET to { Response(Status.OK).body(spec()) },
            "/doors/{id}" bind Method.GET to { request -> door(request) },
            "/doors/{id}/actions/{act}" bind Method.POST to { request -> act(request) },
        ),
    )

    /** The description, with each named relation added to its operation. */
    private fun spec(): String {
        val tree = mapper.readTree(SPEC)
        tree.path("paths").forEach { item ->
            item.forEach { operation ->
                relations[operation.path("operationId").asText()]?.let {
                    (operation as com.fasterxml.jackson.databind.node.ObjectNode).put("x-heart-relation", it)
                }
            }
        }
        return mapper.writeValueAsString(tree)
    }

    private fun door(request: Request): Response {
        val id = request.path("id")!!.toLong()
        val status = doors[id] ?: return Response(Status.NOT_FOUND)
        return json(body(id, status), if (id in vaults) VAULT else JSON)
    }

    private fun act(request: Request): Response {
        val id = request.path("id")!!.toLong()
        val act = request.path("act")!!
        val status = doors[id]
        return when {
            status == null -> Response(Status.NOT_FOUND)
            act !in ALLOWED.getValue(status) -> Response(Status.CONFLICT)
                .header("Content-Type", "application/problem+json")
                .body("""{"title":"Not allowed now","status":409,"detail":"$act is not allowed"}""")
            else -> {
                doors[id] = AFTER.getValue(act)
                json(body(id, doors.getValue(id)), if (id in vaults) VAULT else JSON)
            }
        }
    }

    private fun body(id: Long, status: String): Map<String, Any?> {
        val allowed = ALLOWED.getValue(status)
        val base = linkedMapOf<String, Any?>("id" to id, "status" to status)
        if (id in vaults) base["kind"] = "vault-door"
        when (signal) {
            Signal.NAMES -> base["allowedActions"] = allowed
            Signal.BOOLEANS -> base["capabilities"] =
                ACTS.associate { "can" + it.replaceFirstChar(Char::uppercase) to (it in allowed) }
            Signal.LINKS -> base["links"] =
                allowed.map { mapOf("rel" to it, "method" to "POST", "href" to "/doors/$id/actions/$it") }
            Signal.ODATA -> ACTS.forEach { base["#Doors.$it"] = if (it in allowed) emptyMap<String, Any>() else null }
            Signal.TABLE -> Unit
            Signal.JSONATA -> base["perms"] = ACTS.associate { it.take(1) to if (it in allowed) 1 else 0 }
        }
        return base
    }

    private fun json(body: Any, type: String): Response =
        Response(Status.OK).header("Content-Type", type).body(mapper.writeValueAsString(body))

    private companion object {
        const val JSON = "application/json"
        const val VAULT = "application/vnd.doors.vault+json"
        val ACTS = listOf("open", "close", "lock", "unlock")
        val ALLOWED = mapOf("OPEN" to listOf("close"), "CLOSED" to listOf("open", "lock"), "LOCKED" to listOf("unlock"))
        val AFTER = mapOf("open" to "OPEN", "close" to "CLOSED", "lock" to "LOCKED", "unlock" to "CLOSED")
        val SPEC: String = checkNotNull(DoorUpstream::class.java.getResource("/doors/openapi.json")).readText()
    }
}
