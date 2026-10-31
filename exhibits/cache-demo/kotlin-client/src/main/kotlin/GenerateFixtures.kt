import com.apicatalog.jcs.JsonCanonicalizer
import jakarta.json.Json
import jakarta.json.JsonValue
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * GenerateFixtures.kt
 *
 * Kotlin port of generate_fixtures.sh. Builds the same three-level fixture
 * set:
 *
 *   resources/<bed>.json          -- state + a single link to its affordance-set
 *   affordance-sets/sha256-*.json -- named affordances, each pointing at a schema
 *   schemas/sha256-*.json         -- immutable schema bodies
 *
 * Per SPEC.md §6.5, Layer 2 (affordance-sets) and Layer 3 (schemas) content
 * addresses are SHA-256 over the JCS (RFC 8785) canonical form of the full
 * JSON body -- not over whatever bytes happen to come out of a string
 * template. So those two layers are built here as plain Kotlin object
 * trees (Map/List/String/Number/Boolean/null), converted to a Jakarta
 * JSON-P `JsonValue` tree (what com.apicatalog:titanium-jcs's
 * `JsonCanonicalizer` actually operates on), and canonicalized -- which
 * sorts keys recursively and strips insignificant whitespace before we
 * hash. The bytes we hash are exactly the bytes we write to disk, so the
 * filename is always a true content address of the file's own content.
 *
 * Layer 1 (resources) is deliberately NOT content-addressed -- SPEC.md is
 * explicit that this layer changes and is always revalidated via ETag
 * instead -- so it's still hand-formatted JSON, same as the bash original,
 * for human readability.
 *
 * Run via `gradle generateFixtures` (or `./gradlew generateFixtures`) from
 * kotlin-client/ -- see the `generateFixtures` task in build.gradle.kts,
 * which pins the working directory to the repo root so fixtures/ always
 * lands next to server.py regardless of where the command was invoked from.
 */

fun sha256Of16(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
}

/**
 * Converts a plain Kotlin object graph (Map/List/String/Int/Double/
 * Boolean/null) into a Jakarta JSON-P `JsonValue` tree, since that's the
 * type titanium-jcs's `JsonCanonicalizer` actually consumes.
 */
fun toJson(value: Any?): JsonValue = when (value) {
    null -> JsonValue.NULL
    is JsonValue -> value
    is String -> Json.createValue(value)
    is Int -> Json.createValue(value)
    is Long -> Json.createValue(value)
    is Double -> Json.createValue(value)
    is Boolean -> if (value) JsonValue.TRUE else JsonValue.FALSE
    is Map<*, *> -> {
        val b = Json.createObjectBuilder()
        for ((k, v) in value) b.add(k as String, toJson(v))
        b.build()
    }
    is List<*> -> {
        val b = Json.createArrayBuilder()
        for (v in value) b.add(toJson(v))
        b.build()
    }
    else -> throw IllegalArgumentException("Unsupported fixture value type: ${value::class}")
}

/** RFC 8785 JCS-canonical UTF-8 bytes of `tree` -- what we hash and write. */
fun canonicalize(tree: Any?): ByteArray =
    JsonCanonicalizer.canonize(toJson(tree)).toByteArray(Charsets.UTF_8)

fun main() {
    val root = File(".").absoluteFile.normalize()
    val fixtures = File(root, "fixtures")
    val resourcesDir = File(fixtures, "resources")
    val setsDir = File(fixtures, "affordance-sets")
    val schemasDir = File(fixtures, "schemas")

    fixtures.deleteRecursively()
    resourcesDir.mkdirs()
    setsDir.mkdirs()
    schemasDir.mkdirs()

    val schemaHash = LinkedHashMap<String, String>() // name -> hash

    fun writeSchema(name: String, tree: LinkedHashMap<String, Any?>) {
        val bytes = canonicalize(tree)
        val h = sha256Of16(bytes)
        File(schemasDir, "sha256-$h.json").writeBytes(bytes)
        schemaHash[name] = h
    }

    fun schema(
        title: String,
        properties: LinkedHashMap<String, Any?>,
        required: List<String>
    ) = linkedMapOf<String, Any?>(
        "title" to title,
        "type" to "object",
        "properties" to properties,
        "required" to required
    )

    fun field(type: String, vararg extra: Pair<String, Any?>) =
        linkedMapOf<String, Any?>("type" to type, *extra)

    // --- Level 3: schemas (10 distinct affordance schemas) -----------------

    writeSchema(
        "sowSeed",
        schema(
            "Sow Seed",
            linkedMapOf(
                "cellId" to field("string"),
                "seedType" to field("string", "enum" to listOf("tomato", "basil", "pepper")),
                "depthCm" to field("number", "minimum" to 0.5, "maximum" to 5)
            ),
            listOf("cellId", "seedType")
        )
    )

    writeSchema(
        "waterCell",
        schema(
            "Water Cell",
            linkedMapOf(
                "cellId" to field("string"),
                "milliliters" to field("number", "minimum" to 10, "maximum" to 500)
            ),
            listOf("cellId", "milliliters")
        )
    )

    writeSchema(
        "harvest",
        schema(
            "Harvest",
            linkedMapOf(
                "cellId" to field("string"),
                "yieldGrams" to field("number", "minimum" to 0)
            ),
            listOf("cellId")
        )
    )

    writeSchema(
        "fertilize",
        schema(
            "Fertilize",
            linkedMapOf(
                "cellId" to field("string"),
                "product" to field("string")
            ),
            listOf("cellId", "product")
        )
    )

    writeSchema(
        "prune",
        schema("Prune", linkedMapOf("cellId" to field("string")), listOf("cellId"))
    )

    writeSchema(
        "inspect",
        schema(
            "Inspect",
            linkedMapOf(
                "cellId" to field("string"),
                "notes" to field("string")
            ),
            listOf("cellId")
        )
    )

    writeSchema(
        "transplant",
        schema(
            "Transplant",
            linkedMapOf(
                "fromCellId" to field("string"),
                "toCellId" to field("string")
            ),
            listOf("fromCellId", "toCellId")
        )
    )

    writeSchema(
        "mulch",
        schema("Mulch", linkedMapOf("cellId" to field("string")), listOf("cellId"))
    )

    writeSchema(
        "thin",
        schema(
            "Thin Seedlings",
            linkedMapOf(
                "cellId" to field("string"),
                "keepCount" to field("integer", "minimum" to 1)
            ),
            listOf("cellId", "keepCount")
        )
    )

    writeSchema(
        "rotate",
        schema(
            "Rotate Crop",
            linkedMapOf(
                "cellId" to field("string"),
                "nextCrop" to field("string")
            ),
            listOf("cellId", "nextCrop")
        )
    )

    println("Wrote ${schemaHash.size} schema files to $schemasDir")

    // --- Level 2: affordance-sets (variety in size: 1, 2, 3, 10) ------------

    val setHash = LinkedHashMap<String, String>()          // set-name -> hash
    val setMembers = LinkedHashMap<String, List<String>>() // set-name -> affordance names

    fun writeSet(setName: String, vararg members: String) {
        setMembers[setName] = members.toList()
        val tree = LinkedHashMap<String, Any?>()
        for (m in members) {
            val h = schemaHash.getValue(m)
            tree[m] = linkedMapOf<String, Any?>(
                "method" to "POST",
                "target" to "/beds/{bedId}/cells",
                "schemaRef" to linkedMapOf<String, Any?>("href" to "/schemas/sha256-$h.json")
            )
        }
        val bytes = canonicalize(tree)
        val h = sha256Of16(bytes)
        File(setsDir, "sha256-$h.json").writeBytes(bytes)
        setHash[setName] = h
    }

    writeSet("setSolo", "sowSeed")
    writeSet("setPair", "sowSeed", "waterCell")
    writeSet("setTrio", "sowSeed", "waterCell", "harvest")
    writeSet(
        "setFull",
        "sowSeed", "waterCell", "harvest", "fertilize", "prune",
        "inspect", "transplant", "mulch", "thin", "rotate"
    )

    println("Wrote ${setHash.size} affordance-set files to $setsDir")
    for ((k, h) in setHash) {
        val members = setMembers.getValue(k)
        println("  $k -> sha256-$h.json  (${members.size} affordance(s): ${members.joinToString(" ")})")
    }

    // --- Level 1: resources (12 beds, deliberately reusing set hashes) -----
    // Never content-addressed (SPEC.md §6.2) -- hand-formatted, not JCS'd.

    val bedSet = linkedMapOf(
        "bed-1" to "setPair",
        "bed-2" to "setPair",
        "bed-3" to "setSolo",
        "bed-4" to "setTrio",
        "bed-5" to "setPair",
        "bed-6" to "setTrio",
        "bed-7" to "setFull",
        "bed-8" to "setSolo",
        "bed-9" to "setFull",
        "bed-10" to "setTrio",
        "bed-11" to "setPair",
        "bed-12" to "setSolo"
    )

    // Fixed seed: regenerating gives byte-identical fixtures, so git diffs stay quiet.
    val rng = Random(42)
    for ((bed, setName) in bedSet) {
        val setH = setHash.getValue(setName)
        val moisture = rng.nextInt(100)
        val body = """{
  "id": "$bed",
  "state": { "moisture": $moisture, "crop": "tomato" },
  "_links": {
    "self": { "href": "/resources/$bed.json" },
    "affordances": { "href": "/affordance-sets/sha256-$setH.json" }
  }
}
"""
        File(resourcesDir, "$bed.json").writeText(body)
    }

    println("Wrote ${bedSet.size} resource files to $resourcesDir")

    // --- Manifest, for humans -----------------------------------------------

    val manifestFile = File(fixtures, "manifest.txt")
    val manifest = buildString {
        appendLine("bed -> affordance-set -> hash -> affordance count")
        val orderedBeds = bedSet.keys.sortedBy { it.substringAfter("-").toInt() }
        for (bed in orderedBeds) {
            val setName = bedSet.getValue(bed)
            val setH = setHash.getValue(setName)
            val n = setMembers.getValue(setName).size
            appendLine(
                "%-8s -> %-8s -> sha256-%s.json -> %s".format(bed, setName, setH, n)
            )
        }
    }
    manifestFile.writeText(manifest)

    println()
    println("Manifest written to $manifestFile:")
    print(manifest)
}
