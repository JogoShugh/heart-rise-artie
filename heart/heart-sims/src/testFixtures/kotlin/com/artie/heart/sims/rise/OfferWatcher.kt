package com.artie.heart.sims.rise

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.Dialect
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Hypermedia
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.Replies
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.Signatures
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.artie.heart.sims.library.SignedRequests
import com.artie.heart.sims.library.SigningKey
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.then
import java.security.PublicKey
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/** The watcher's signing key: every answer it sends on a caller's reply is signed with it. */
object WatcherKey : SigningKey("watcher-2026-11") {
    const val KEY_ID = "watcher-2026-11"
}

/** One request to be told when [target] offers [relation]: how it stands, and how many looks it has had. */
data class OfferWatch(
    val id: Long,
    val target: String,
    val relation: String,
    val reply: String,
    val maxLooks: Int?,
    val standing: String = WATCHING,
    val looks: Int = 0,
    val version: Int = 0,
) {
    companion object {
        const val WATCHING = "watching"
        const val ANSWERED = "answered"
        const val GAVE_UP = "gave up"
    }
}

/**
 * The offer watch, a RISE service at [origin]: its entry offers `watcher:watch`, a request to be told on the caller's
 * reply when the resource at an address offers an act, by its relation. Each [look] reads every watched resource as
 * a HEART client does, through [reader], and answers the reply, signed by [answerer], once its forms offer the act;
 * or, past the looks a request allows, tells it the act was not offered in time. It knows the resources only by
 * HEART: their forms, by relation. A caller presents one of [tokens] and signs by a key of the set it names.
 */
class OfferWatcher(
    private val reader: HttpHandler,
    private val answerer: Answers,
    private val origin: String = ORIGIN,
    tokens: List<String> = listOf(TOKEN),
    private val callerKeys: (String) -> Map<String, PublicKey> = { emptyMap() },
) {
    private val mapper = ObjectMapper()
    private val watches = ConcurrentSkipListMap<Long, OfferWatch>()
    private val next = AtomicLong(1)

    private val type = ResourceType(
        collection = "watches",
        profile = PROFILE,
        acts = emptyList(),
        offers = Offers(keys = STANDINGS, offered = { emptyList() }, whyAbsent = { _, _ -> "" }),
        state = StateView<OfferWatch, String>(
            keyOf = { it.standing },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.id, "offers" to it.relation, "standing" to it.standing, "looks" to it.looks) },
            hypermediaOf = { watch ->
                Hypermedia(
                    links = mapOf(
                        "about" to mapOf("href" to watch.target),
                        "hr:reply-to" to mapOf("href" to watch.reply),
                        "hr:keys" to mapOf("href" to Signatures.KEYS, "type" to Signatures.JWK_SET),
                    ),
                )
            },
            keyField = "standing",
        ),
        finding = Finding(
            noun = "watch",
            search = Named("searchWatches", "search-watches"),
            lookup = Named("getWatch", "get-watch"),
            idTitle = "Watch number",
            idDescription = "The watch's number, in its Location",
        ),
        bookmarkActs = listOf(Act("watch", "watch", WATCH_INPUT)),
    )

    private val resource = HeartResource(
        layers = Layers(type),
        load = { id -> watches[id] ?: throw ResourceNotFoundException("no watch $id") },
        perform = { current, _, _ -> Acted(current) },
        find = { _, id -> watches.values.filter { id == null || it.id == id }.map { Found(it.id, it.relation) } },
        performAtBookmark = { _, input ->
            val watch = OfferWatch(
                id = next.getAndIncrement(),
                target = input.json["resource"].asText(),
                relation = input.json["offers"].asText(),
                reply = input.json["_links"]["hr:reply-to"]["href"].asText(),
                maxLooks = input.json["looks"]?.asInt(),
            )
            watches[watch.id] = watch
            Acted(watch, created = "/watches/${watch.id}")
        },
    )

    val handler: HttpHandler = signedRequests().then(
        HeartHttp(
            listOf(resource),
            Credentials.parse(users = "", tokens = tokens.joinToString(",") { Tokens.sha256(it) }),
            HeartSettings(title = "Offer watch", realm = PROFILE.name, keys = mapOf(WatcherKey.KEY_ID to WatcherKey.pair.public)),
        ),
    )

    /** The watch [id] as it is now; null when there is none. */
    fun watch(id: Long): OfferWatch? = watches[id]

    /** One look at every watched resource: each that offers its act now is answered; each out of looks gives up. */
    fun look() = watches.values.filter { it.standing == OfferWatch.WATCHING }.forEach(::look)

    private fun look(watch: OfferWatch) {
        val read = reader(Request(Method.GET, watch.target).header("Accept", Dialect.JSONSCHEMA.mediaType))
        val offered = read.status.successful && !mapper.readTree(read.bodyString()).path("_forms").path(watch.relation).isMissingNode
        val self = "$origin/watches/${watch.id}"
        val inReplyTo = Replies.IN_REPLY_TO to mapOf("href" to self)
        val looked = watch.copy(looks = watch.looks + 1, version = watch.version + 1)
        when {
            offered -> {
                watches[watch.id] = looked.copy(standing = OfferWatch.ANSWERED)
                answerer.answer(
                    watch.reply, "completed",
                    mapOf(
                        "state" to "completed",
                        "result" to mapOf(
                            "resource" to watch.target, "offers" to watch.relation, "etag" to read.header("ETag"),
                            "_links" to mapOf("about" to mapOf("href" to watch.target)),
                        ),
                        "_links" to mapOf(inReplyTo),
                    ),
                )
            }
            watch.maxLooks != null && looked.looks >= watch.maxLooks -> {
                watches[watch.id] = looked.copy(standing = OfferWatch.GAVE_UP)
                answerer.answer(
                    watch.reply, "failed",
                    mapOf(
                        "state" to "failed",
                        "type" to "$origin/problems/not-offered-in-time",
                        "title" to "The act was not offered in the time allowed",
                        "detail" to "${watch.relation} was not offered after ${looked.looks} looks",
                        // RFC 9457 extension member: the resource watched, kept with the problem wherever it goes.
                        "resource" to watch.target,
                        "_links" to mapOf(inReplyTo, "about" to mapOf("href" to watch.target)),
                    ),
                )
            }
            else -> {
                watches[watch.id] = looked
                if (watch.looks == 0) {
                    answerer.answer(
                        watch.reply, "progress",
                        mapOf(
                            "state" to "awaiting", "percent" to 0,
                            "note" to "Still looking: ${watch.relation} is not offered yet",
                            "_links" to mapOf(inReplyTo),
                        ),
                    )
                }
            }
        }
    }

    /** Refuses, with 401, a request to watch that its caller did not sign with a key of the set it names. */
    private fun signedRequests(): Filter = Filter { next ->
        { request ->
            if (request.method != Method.POST || request.uri.path != WATCH) {
                next(request)
            } else {
                val keys = runCatching {
                    callerKeys(mapper.readTree(request.bodyString())["_links"]["hr:keys"]["href"].asText())
                }.getOrDefault(emptyMap())
                val key = SignedRequests.keyIdOf(request)?.let { keys[it] }
                if (key != null && SignedRequests.signedBy(request, origin, key)) {
                    next(request)
                } else {
                    SignedRequests.unauthorized(request, "Offer watch", keys.keys.firstOrNull() ?: "")
                }
            }
        }
    }

    companion object {
        const val ORIGIN = "http://localhost:8099"
        const val TOKEN = "watcher-token"
        const val WATCH = "/actions/watch"
        private val STANDINGS = listOf(OfferWatch.WATCHING, OfferWatch.ANSWERED, OfferWatch.GAVE_UP)

        private const val WATCH_INPUT = """{
  "type": "object",
  "required": ["resource", "offers", "_links"],
  "properties": {
    "resource": {"type": "string", "format": "uri", "title": "Resource", "description": "The resource to watch"},
    "offers": {"type": "string", "title": "Act", "description": "The relation of the act to wait for"},
    "looks": {"type": "integer", "minimum": 1, "title": "Looks", "description": "How many looks before giving up"},
    "_links": {
      "type": "object",
      "required": ["hr:reply-to", "hr:keys"],
      "additionalProperties": true,
      "properties": {
        "hr:reply-to": {"type": "object", "required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}}},
        "hr:keys": {"type": "object", "required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}}}
      }
    }
  }
}"""

        val PROFILE = Profile(
            name = "watcher",
            title = "Offer watch",
            doc = "Requests to be told when a resource offers an act.",
            relations = listOf(
                Relation("watch", AlpsType.UNSAFE, "Ask to be told when an act is offered", "Watches a resource and tells the caller's reply once it offers the act.", "Offered at the entry."),
                Relation("search-watches", AlpsType.SAFE, "Find watches", "Lists the watches.", "Always."),
                Relation("get-watch", AlpsType.SAFE, "Get a watch", "One watch, by its number.", "Always."),
            ),
        )
    }
}
