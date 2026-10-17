package com.artie.heart.gateway

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * What a reader recognizes in an upstream answer. Every test it has must
 * hold; a reader with none recognizes every answer, and is its API's default.
 * - [body]: members of the body, by JSON Pointer, equal to a value, or
 *   present at all where the value is null;
 * - [mediaType]: the answer's media type;
 * - [path]: the description's path template the answer was asked at.
 */
class Match(
    private val body: Map<JsonPointer, String?> = emptyMap(),
    private val mediaType: MediaType? = null,
    val path: PathTemplate? = null,
) {
    private val pathPattern = path?.let { Regex("^" + it.replace(Regex("\\{[^}/]+}"), "[^/]+") + "$") }

    fun holds(answer: Answer): Boolean =
        body.all { (pointer, value) -> has(answer.body.at(pointer), value) } &&
            (mediaType == null || mediaType.equals(answer.mediaType, ignoreCase = true)) &&
            (pathPattern == null || pathPattern.matches(answer.path))

    private fun has(member: JsonNode, value: String?): Boolean =
        !member.isMissingNode && (value == null || member.asText() == value)
}

/**
 * One reader of a connector, from its `<name>.reader.yaml`: what answers it
 * recognizes, how those answers signal what is allowed now, and what of
 * them is control, not state, to leave out of the HEART state.
 */
class Reader(
    val file: String,
    val match: Match,
    private val signal: AllowedNow,
    private val hidden: List<JsonPointer>,
    /** The operationIds the file names, checked against the description at start. */
    val named: Set<OperationId>,
) {
    fun read(answer: Answer): Reading {
        val state = answer.body.deepCopy<JsonNode>()
        hidden.forEach { pointer -> (state.at(pointer.head()) as? ObjectNode)?.remove(pointer.last().matchingProperty) }
        if (signal is ODataActions) (state as? ObjectNode)?.properties()?.removeIf { signal.isAdvertisement(it.key) }
        return Reading(signal.of(answer.body, answer.links), state)
    }
}

/**
 * How one upstream API signals what is allowed: a folder with a
 * `connector.yaml`, naming the profile its relations are named in and
 * listing its readers in order, and one `<name>.reader.yaml` per reader,
 * with any JSONata file a reader uses beside it. The first reader that
 * recognizes an answer reads it, so the API's default reader, which
 * recognizes everything, is listed last.
 */
class Connector(val name: String, val profile: String, val readers: List<Reader>) {

    /** The first reader that recognizes [answer], or none. */
    fun readerFor(answer: Answer): Reader? = readers.firstOrNull { it.match.holds(answer) }

    /** Refuses a reader that names an operation or a path the upstream's description doesn't have. */
    fun check(spec: GatewaySpec) {
        val operations = spec.types.flatMap { it.acts + it.bookmarkActs }.map { it.operation.name }.toSet()
        val paths = spec.types.map { "/${it.collection}/{id}" }.toSet()
        readers.forEach { reader ->
            reader.match.path?.let { path ->
                check(path.replace(Regex("\\{[^}/]+}"), "{id}") in paths) {
                    "${reader.file}: the upstream's description serves no $path"
                }
            }
            (reader.named - operations).firstOrNull()?.let {
                error("${reader.file}: the upstream's description has no operation $it")
            }
        }
    }

    companion object {
        private const val READER = ".reader.yaml"

        fun load(folder: Path, mapper: ObjectMapper): Connector {
            val name = folder.fileName.toString()
            val yaml = ObjectMapper(YAMLFactory())
            val api = yaml.readTree(folder.resolve("connector.yaml").toFile())
            val profile = checkNotNull(api.path("profile").asText(null)) { "$name/connector.yaml: no profile" }
            val listed = api.path("readers").map { it.asText() }
            val present = Files.list(folder).use { paths ->
                paths.map { it.fileName.toString() }.filter { it.endsWith(READER) }.sorted().toList()
            }
            (present - listed.toSet()).firstOrNull()
                ?.let { error("$name/$it: not listed under readers in connector.yaml") }
            (listed - present.toSet()).firstOrNull()
                ?.let { error("$name/connector.yaml: lists $it, which is not there") }
            val readers = listed.map { file ->
                ReaderFile(folder, "$name/$file", yaml.readTree(folder.resolve(file).toFile()), mapper).reader()
            }
            return Connector(name, profile, readers)
        }
    }
}

/** One `<name>.reader.yaml`, read into its reader, or refused naming the file. */
private class ReaderFile(val folder: Path, val label: String, val node: JsonNode, val mapper: ObjectMapper) {
    private val allowed = node.path("allowed")
    private val names: NameMap = allowed.path("map").fields().asSequence().associate { it.key to it.value.asText() }

    fun reader(): Reader {
        val table = allowed.path("table").asText(null)?.let { StateTable(pointer(it), rows()) }
        val signal: AllowedNow = table
            ?: allowed.path("odata").asText(null)?.let { ODataActions(it, names) }
            ?: allowed.path("names").asText(null)?.let { NamesList(pointer(it), names) }
            ?: allowed.path("booleans").asText(null)?.let { BooleanMap(pointer(it), names) }
            ?: allowed.path("links").asText(null)?.let { LinkArray(pointer(it), names) }
            ?: allowed.path("jsonata").asText(null)?.let { JsonataSignal(Files.readString(folder.resolve(it)), mapper) }
            ?: error("$label: allowed must name one of names, booleans, links, odata, table or jsonata")
        val hidden = node.path("hide").map { pointer(it.asText()) }
        return Reader(label, match(), signal, hidden, names.values.toSet() + rowsNamed())
    }

    private fun match(): Match {
        val matches = node.path("matches")
        val body = matches.path("body").fields().asSequence()
            .associate { (at, value) -> pointer(at) to value.takeUnless { it.isNull }?.asText() }
        return Match(body, matches.path("mediaType").asText(null)?.lowercase(), matches.path("path").asText(null))
    }

    private fun rows(): Map<String, TableRow> = allowed.path("rows").fields().asSequence().associate { (value, row) ->
        value to TableRow(
            row.path("allow").map { it.asText() }.toSet(),
            row.path("absent").fields().asSequence().associate { it.key to it.value.asText() },
        )
    }

    private fun rowsNamed(): Set<OperationId> = rows().values.flatMap { it.allow + it.absent.keys }.toSet()

    private fun pointer(text: String): JsonPointer =
        runCatching { JsonPointer.compile(text) }.getOrElse { error("$label: $text is not a JSON Pointer") }
}
