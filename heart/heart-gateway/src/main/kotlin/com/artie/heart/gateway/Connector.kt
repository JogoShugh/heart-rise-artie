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
    /** Rules, in order, that make acts absent, with reasons, after the signal has said what it allows. */
    private val rules: List<Rule> = emptyList(),
) {
    fun read(answer: Answer): Reading {
        val state = answer.body.deepCopy<JsonNode>()
        hidden.forEach { pointer -> (state.at(pointer.head()) as? ObjectNode)?.remove(pointer.last().matchingProperty) }
        if (signal is ODataActions) (state as? ObjectNode)?.properties()?.removeIf { signal.isAdvertisement(it.key) }
        return Reading(Rule.applied(signal.of(answer.body, answer.links), rules, answer), state)
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
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class Connector(
    val name: String,
    val profile: String,
    val readers: List<Reader>,
    /** The operations the gateway reads; none means every one. */
    val include: Set<OperationId>? = null,
    /** Headers every call to the upstream carries, such as GitHub's `X-GitHub-Api-Version`. */
    val headers: Map<String, String> = emptyMap(),
    /** The resources of an upstream whose paths aren't HEART's, one file each. */
    val resources: List<ResourceDeclaration> = emptyList(),
    /** Which members of the upstream's answers are only links, and how each becomes a HEART link. */
    val links: LinkRules? = null,
) {

    /** The first reader that recognizes [answer], or none. */
    fun readerFor(answer: Answer): Reader? = readers.firstOrNull { it.match.holds(answer) }

    /** Refuses an included operation, a resource or a reader the upstream's description doesn't serve. */
    fun check(spec: GatewaySpec) {
        (include.orEmpty() - spec.routes.keys).firstOrNull()
            ?.let { error("$name/connector.yaml: the upstream's description has no operation $it") }
        resources.forEach { resource ->
            (resource.named - spec.routes.keys).firstOrNull()
                ?.let { error("${resource.file}: the upstream's description has no operation $it") }
        }
        // A reader names an act by its operation's id; a declared act is named by the resource file.
        val operations = spec.types.flatMap { it.acts + it.bookmarkActs }.map { it.operation.name }.toSet() +
            resources.flatMap { resource -> resource.acts.map { it.name } }
        val paths = spec.types.map { "/${it.collection}/{id}" }.toSet() +
            resources.map { spec.route(it.read).path.replace(Regex("\\{[^}/]+}"), "{id}") }
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
            val resources = api.path("resources").map { it.asText() }.map { file ->
                ResourceDeclaration.of("$name/$file", yaml.readTree(folder.resolve(file).toFile()))
            }
            val include = api.path("include").takeIf { it.isArray }?.map { it.asText() }?.toSet()
            val headers = api.path("headers").fields().asSequence().associate { it.key to it.value.asText() }
            return Connector(name, profile, readers, include, headers, resources, LinkRules.of(api.path("links")))
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
            ?: always()
            ?: error("$label: allowed must name one of names, booleans, links, odata, table, jsonata or always")
        val hidden = node.path("hide").map { pointer(it.asText()) }
        val rules = allowed.path("rules").map { rule ->
            Rule(
                Condition.all(rule.path("when")),
                rule.path("absent").fields().asSequence().associate { it.key to it.value.asText() },
            )
        }
        val named = names.values.toSet() + rowsNamed() + always()?.allow.orEmpty() + rulesNamed(rules)
        return Reader(label, match(), signal, hidden, named, rules)
    }

    /** Acts every state allows, but as the rules say: an upstream whose resource has no state to choose by. */
    private fun always(): Always? =
        allowed.path("always").takeIf { it.isArray }?.let { list -> Always(list.map { it.asText() }.toSet()) }

    private fun rulesNamed(rules: List<Rule>): Set<OperationId> = rules.flatMap { it.absent.keys }.toSet()

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
