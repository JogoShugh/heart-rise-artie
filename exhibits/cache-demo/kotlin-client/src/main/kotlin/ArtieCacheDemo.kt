import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * ARTIE cache-walk demo.
 *
 * Fetches a series of resources through three levels:
 *
 *   resource (always revalidated via ETag)
 *     -> affordance-set (content-addressed URL, immutable)
 *          -> schema x N (content-addressed URL, immutable, fetched in parallel)
 *
 * The point isn't the domain data -- it's proving that when two different
 * resources point at the *same* affordance-set or schema URL, the second
 * reference never actually hits the network. That's not asserted by
 * eyeballing client-side timing; it's asserted against the fixture
 * server's own authoritative request log via GET /__stats.
 */

// Override with ARTIE_BASE_URL when 8080 is taken locally.
val BASE_URL: String = System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080"

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class LinkRef(val href: String)

@Serializable
data class ResourceLinks(val self: LinkRef, val affordances: LinkRef)

@Serializable
data class ResourceDoc(
    val id: String,
    val state: JsonElement,
    val _links: ResourceLinks
)

@Serializable
data class AffordanceSchemaRef(val href: String)

@Serializable
data class Affordance(
    val method: String,
    val target: String,
    val schemaRef: AffordanceSchemaRef
)

@Serializable
data class StatsLogEntry(val n: Int, val path: String, val status: Int)

@Serializable
data class Stats(val counts: Map<String, Int>, val log: List<StatsLogEntry>)

suspend fun HttpClient.fetchResource(bedId: String): ResourceDoc {
    val resp = get("$BASE_URL/resources/$bedId.json")
    println("  [resource]        $bedId -> HTTP ${resp.status.value}")
    return json.decodeFromString(resp.bodyAsText())
}

suspend fun HttpClient.fetchAffordanceSet(href: String): Map<String, Affordance> {
    val resp = get("$BASE_URL$href")
    println("  [affordance-set]  $href -> HTTP ${resp.status.value}")
    return json.decodeFromString(resp.bodyAsText())
}

suspend fun HttpClient.fetchSchema(href: String): String {
    val resp = get("$BASE_URL$href")
    println("  [schema]          $href -> HTTP ${resp.status.value}")
    return resp.bodyAsText()
}

suspend fun HttpClient.fetchStats(): Stats {
    val resp = get("$BASE_URL/__stats")
    return json.decodeFromString(resp.bodyAsText())
}

/**
 * One full Sync-ish walk for a single resource: fetch its state, follow
 * the single link to its affordance-set, then fan out to every schema the
 * set references -- in parallel, since none of those leaf fetches depend
 * on each other.
 */
suspend fun syncBed(client: HttpClient, bedId: String) = coroutineScope {
    println("Syncing $bedId")
    val resource = client.fetchResource(bedId)
    val affordances = client.fetchAffordanceSet(resource._links.affordances.href)
    println("  -> ${affordances.size} affordance(s): ${affordances.keys.joinToString()}")

    val schemaFetches = affordances.values.map { aff ->
        async { client.fetchSchema(aff.schemaRef.href) }
    }
    schemaFetches.awaitAll()
    println()
}

fun main() = runBlocking {
    val client = HttpClient(CIO) {
        install(HttpCache)
    }

    // Deliberate ordering, matching fixtures/manifest.txt:
    //   bed-1, bed-2, bed-5, bed-11 all share the same 2-affordance set
    //   bed-4, bed-6, bed-10        all share the same 3-affordance set
    //   bed-3, bed-8, bed-12        all share the same 1-affordance set
    //   bed-7, bed-9                share the 10-affordance set
    // bed-1 is synced again at the end to prove resource-level ETag
    // revalidation (expect a 304, not a fresh 200, on that second call).
    val order = listOf(
        "bed-1", "bed-2", "bed-3", "bed-4", "bed-5",
        "bed-6", "bed-7", "bed-8", "bed-9", "bed-10",
        "bed-11", "bed-12", "bed-1"
    )

    for (bedId in order) {
        syncBed(client, bedId)
    }

    println("=".repeat(60))
    println("Fetching server-side request log for verification...")
    val stats = client.fetchStats()

    var failures = 0

    // Assertion 1: every content-addressed path (affordance-set or schema)
    // was actually requested at most once at the network level, no matter
    // how many resources referenced it.
    val contentAddressed = stats.counts.filterKeys {
        it.startsWith("/affordance-sets/") || it.startsWith("/schemas/")
    }
    println("\n-- content-addressed paths (expect count == 1 each) --")
    for ((path, count) in contentAddressed.toSortedMap()) {
        val ok = count == 1
        if (!ok) failures++
        println("  ${if (ok) "PASS" else "FAIL"}  $path  (requested $count time(s))")
    }

    // Assertion 2: bed-1's resource path was hit twice at the network
    // level (no-cache forces revalidation every time) but the second hit
    // should be a 304, not a full 200.
    println("\n-- resource-level revalidation for bed-1 --")
    val bed1Log = stats.log.filter { it.path == "/resources/bed-1.json" }
    val bed1Ok = bed1Log.size == 2 && bed1Log[0].status == 200 && bed1Log[1].status == 304
    if (!bed1Ok) failures++
    println(
        "  ${if (bed1Ok) "PASS" else "FAIL"}  " +
            "bed-1 resource requested ${bed1Log.size} time(s), statuses: " +
            bed1Log.joinToString { it.status.toString() } +
            "  (expected [200, 304])"
    )

    println("\n" + "=".repeat(60))
    if (failures == 0) {
        println("ALL CHECKS PASSED — content-addressed sharing worked, ETag revalidation worked.")
    } else {
        println("$failures CHECK(S) FAILED — see above.")
    }

    client.close()
}
