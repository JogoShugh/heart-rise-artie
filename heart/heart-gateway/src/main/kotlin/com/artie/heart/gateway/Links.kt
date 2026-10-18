package com.artie.heart.gateway

import com.artie.heart.Hypermedia
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI

/** A media type for links whose URI has [host] (any when null) and ends with [suffix] (any when null). */
class TypeRule(val host: String?, val suffix: String?, val type: String) {
    fun matches(uri: URI): Boolean =
        (host == null || uri.host.equals(host, ignoreCase = true)) &&
            (suffix == null || uri.path.orEmpty().endsWith(suffix, ignoreCase = true))
}

/**
 * Which members of an upstream's answers are only links, and how each becomes
 * a HEART link (spec/PLAN-GATEWAY-URL-LINKS.md), from a connector's `links`
 * section; a resource file may give its own [embed]. Only members named by
 * [members] are read: no other data is ever scanned for addresses.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class LinkRules(
    /** Member names that are links, as globs: `*_url`, `url`. */
    val members: List<String>,
    /** The member that is the resource's own address: it becomes self, and leaves the state. */
    val self: String?,
    /** Relation names by member name; any other is the profile's CURIE and the name without `_url`. */
    val relations: Map<String, String>,
    /** Media types of links outside the API, by host and suffix, first match wins. */
    val types: List<TypeRule>,
    /** The media type of a link into the upstream's API that the gateway doesn't serve. */
    val apiType: String?,
    /** Paths of nested objects that are resources of their own, embedded: `user`, `head/repo`. */
    val embed: List<String>,
    /** Members left out of the state, and not linked either; empty unless an author wants it. */
    val drop: List<String>,
    /** The upstream's own links object, as GitHub's pull request carries `_links`: read as links too. */
    val upstreamLinks: String?,
) {
    private val patterns = members.map { glob ->
        Regex("^" + glob.split("*").joinToString(".*") { Regex.escape(it) } + "$")
    }

    fun isLink(name: String): Boolean = patterns.any { it.matches(name) }

    /** These rules, with a resource file's own embeds when it gives any. */
    fun embedding(own: List<String>?): LinkRules =
        if (own == null) this else LinkRules(members, self, relations, types, apiType, own, drop, upstreamLinks)

    companion object {
        fun of(node: JsonNode): LinkRules? {
            if (node.isMissingNode || node.isNull) return null
            return LinkRules(
                members = node.path("members").map { it.asText() },
                self = node.path("self").asText(null),
                relations = node.path("relations").properties().associate { it.key to it.value.asText() },
                types = node.path("types").map {
                    TypeRule(it.path("host").asText(null), it.path("suffix").asText(null), it.path("type").asText())
                },
                apiType = node.path("api").asText(null),
                embed = node.path("embed").map { it.asText() },
                drop = node.path("drop").map { it.asText() },
                upstreamLinks = node.path("upstream").asText(null),
            )
        }
    }
}

/** A GET the gateway serves, as a path pattern, and the relation of its operation when the connector names it. */
class ServedPath(template: PathTemplate, val relation: String?) {
    private val pattern = Regex("^" + template.replace(Regex("\\{[^}/]+}"), "[^/]+") + "$")

    fun matches(path: String): Boolean = pattern.matches(path)
}

/**
 * Writes one link: a link into the upstream's API at [base] that the gateway
 * serves ([served]) is rewritten to the gateway's own address, a swap of
 * [base] for nothing, under its operation's relation; any other keeps its
 * address. Each gets a media type when one is known, and `templated` when
 * it is a URI template.
 */
class LinkWriter(
    private val rules: LinkRules,
    private val base: String,
    private val served: List<ServedPath>,
    private val curie: String,
) {
    /** The gateway's own address for [href] when it serves it; [href] otherwise. */
    fun address(href: String): String = if (servedBy(href) != null) href.removePrefix(base) else href

    /** Adds the link to [href], named after [name] or the [member] it was, to [links]. */
    fun add(links: MutableMap<String, Any?>, name: String, member: String, href: String) {
        val relation = servedBy(href)?.relation ?: rules.relations[member] ?: name.trimEnd('-')
        val key = if (':' in relation || relation in IANA) relation else "$curie:$relation"
        val link = link(href)
        links[key] = when (val held = links[key]) {
            null -> link
            link -> held
            is List<*> -> if (link in held) held else held + link
            else -> listOf(held, link)
        }
    }

    private fun link(href: String): Map<String, Any?> {
        val served = servedBy(href)
        val type = when {
            served != null -> HAL_JSON
            href.startsWith(base) -> rules.apiType
            else -> runCatching { URI(href.replace(TEMPLATE, "")) }.getOrNull()
                ?.let { uri -> rules.types.firstOrNull { it.matches(uri) }?.type }
        }
        return buildMap {
            put("href", address(href))
            type?.let { put("type", it) }
            if ('{' in href) put("templated", true)
        }
    }

    /** The served GET at [href], if any. A template is a family of addresses, never one the gateway serves. */
    private fun servedBy(href: String): ServedPath? {
        if (!href.startsWith(base) || '{' in href) return null
        val path = href.removePrefix(base).substringBefore('?')
        return served.firstOrNull { it.matches(path) }
    }

    private companion object {
        const val HAL_JSON = "application/hal+json"
        val TEMPLATE = Regex("\\{[^}]*}")

        /** Relations IANA registers, written bare: no CURIE. */
        val IANA = setOf("alternate", "icon", "self", "item", "collection", "related", "license")
    }
}

/** A state with its link members lifted out: what is left, and the links and embedded resources they became. */
class Lifted(val state: JsonNode, val hypermedia: Hypermedia)

/**
 * Lifts an upstream state's link members into HEART links, by [rules], each
 * written by [writer]. A nested object [rules] embed is embedded when it has
 * its own address; its links are lifted the same way, and its data stays.
 */
class LinkLifter(private val rules: LinkRules, private val writer: LinkWriter, private val curie: String) {
    fun lift(state: JsonNode): Lifted {
        val (left, links, embedded) = parts(state.deepCopy())
        return Lifted(left, Hypermedia(links, embedded))
    }

    /** [node] as a whole HAL resource object, its self link from its own address; null when it names none. */
    fun resource(node: JsonNode): Map<String, Any?>? = (node.deepCopy<JsonNode>() as? ObjectNode)?.let(::embedded)

    /** One resource object: its state left, its links, and its embedded resources. */
    private fun parts(node: JsonNode): Triple<JsonNode, Map<String, Any?>, Map<String, Any?>> {
        val obj = node as? ObjectNode ?: return Triple(node, emptyMap(), emptyMap())
        val links = LinkedHashMap<String, Any?>()
        val embedded = LinkedHashMap<String, Any?>()
        rules.embed.forEach { path -> embed(obj, path)?.let { embedded["$curie:${path.replace('/', '-')}"] = it } }
        rules.upstreamLinks?.let { member -> upstream(obj.remove(member), links) }
        rules.drop.forEach { obj.remove(it) }
        members(obj, "", links)
        return Triple(obj, links, embedded)
    }

    /** The object or array at [path] in [obj], as embedded resources, taken out of [obj]; null when none is one. */
    private fun embed(obj: ObjectNode, path: String): Any? {
        val names = path.split("/")
        val parent = names.dropLast(1).fold(obj as JsonNode?) { at, name -> at?.get(name) } as? ObjectNode
        val value = parent?.get(names.last())
        val resources = when (value) {
            is ObjectNode -> listOfNotNull(embedded(value))
            is ArrayNode -> value.mapNotNull { (it as? ObjectNode)?.let(::embedded) }
            else -> emptyList()
        }
        if (resources.isNotEmpty()) parent?.remove(names.last())
        return when {
            resources.isEmpty() -> null
            value is ArrayNode -> resources
            else -> resources.single()
        }
    }

    /** An embedded resource object: its own state, links and embedded ones, and its self link. None without self. */
    private fun embedded(value: ObjectNode): Map<String, Any?>? {
        val self = rules.self?.let { value.get(it)?.asText() }?.takeIf(::isHttp) ?: return null
        val (left, links, embedded) = parts(value)
        val body = MAPPER.convertValue(left, Map::class.java).mapKeys { it.key.toString() }.toMutableMap()
        body["_links"] = mapOf("self" to mapOf("href" to writer.address(self))) + links
        if (embedded.isNotEmpty()) body["_embedded"] = embedded
        return body
    }

    /** The upstream's own links object: each relation's href, as a link named after it. */
    private fun upstream(node: JsonNode?, links: MutableMap<String, Any?>) {
        node?.properties()?.forEach { (rel, link) ->
            val href = link.path("href").asText(null)
            if (rel != "self" && href != null) writer.add(links, rel.replace('_', '-'), "$rel$URL", href)
        }
    }

    /** Each link member of [obj], and of the objects in it, lifted out; [prefix] names the nesting. */
    private fun members(obj: ObjectNode, prefix: String, links: MutableMap<String, Any?>) {
        obj.properties().toList().forEach { (name, value) ->
            if (value is ObjectNode) {
                members(value, prefix + name.replace('_', '-') + "-", links)
            } else if (rules.isLink(name) && value.isTextual && isHttp(value.asText())) {
                lift(obj, name, prefix, links)
            }
        }
    }

    private fun lift(obj: ObjectNode, name: String, prefix: String, links: MutableMap<String, Any?>) {
        val href = obj.remove(name).asText()
        if (prefix.isEmpty() && name == rules.self) return
        writer.add(links, prefix + stem(name), if (prefix.isEmpty()) name else prefix + name, href)
    }

    private companion object {
        const val URL = "_url"
        val MAPPER = ObjectMapper()

        fun isHttp(text: String) = text.startsWith("https://") || text.startsWith("http://")

        /** A member's name as a relation: `comments_url` is `comments`; `url` alone names nothing more. */
        fun stem(name: String): String = name.removeSuffix(URL).removeSuffix("url").replace('_', '-')
    }
}
