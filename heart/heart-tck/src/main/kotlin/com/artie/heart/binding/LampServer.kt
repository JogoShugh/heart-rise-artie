package com.artie.heart.binding

import com.artie.heart.ActInput
import com.artie.heart.Acted
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Listing
import com.artie.heart.Past
import com.artie.heart.PastEvent
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.RuleRefusedException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The lamps a binding's test server holds. */
class LampStore {
    val lamps = ConcurrentHashMap<Long, Lamp>()

    /** What happened to each lamp, for the lamps whose past a scenario sets up. */
    val events = ConcurrentHashMap<Long, List<PastEvent>>()

    /**
     * Another client's write, staged by a scenario to land on a lamp just
     * after the server next reads it: between an act's If-Match check and the act.
     */
    val writeAfterNextRead = ConcurrentHashMap<Long, (Lamp) -> Lamp>()

    /** Lamp [id] as the server reads it; a staged write lands right after. */
    fun read(id: Long): Lamp {
        val lamp = lamps[id] ?: throw notFound(id)
        writeAfterNextRead.remove(id)?.let { write -> lamps[id] = write(lamp) }
        return lamp
    }

    /** A lamp as of [instant]: switched off at brightness 50 until its events say otherwise. */
    fun at(id: Long, instant: Instant): Lamp =
        events[id].orEmpty().filter { !it.occurredAt.isAfter(instant) }.fold(installed(id)) { lamp, past ->
            when (past.type) {
                "SwitchedOn" -> lamp.copy(status = "ON")
                else -> lamp.copy(brightness = (past.event as Map<*, *>)["brightness"] as Int)
            }.copy(version = past.sequenceNo)
        }

    private companion object {
        const val INSTALLED_BRIGHTNESS = 50

        /** A lamp before anything happened to it. */
        fun installed(id: Long) = Lamp(id, "OFF", INSTALLED_BRIGHTNESS, version = 0)
    }
}

/**
 * The server under the binding features: one resource type, the lamp, and
 * nothing else, no handlers of its own. It says only how a lamp is read and
 * what each act does. A binding's tests register [resource], [colours] and
 * the store, and serve them with the binding; the server admits [USERS] and
 * [TOKENS], and the profile "home" names its realm.
 */
object LampServer {
    /** The Bearer token every scenario's client carries by default. */
    const val LAMP_TOKEN = "lamp-token"

    /** The user "coach", whose password is "s3cret", held as its bcrypt hash. */
    const val USERS = "coach:\$2y\$10\$8aPKQ4NLZ03c0ql39PXrZuIWta4FhSekHlrDmcbAAxxnhLXovjO8C"

    /** [LAMP_TOKEN], held as its SHA-256 in hex. */
    const val TOKENS = "a1011f8452b5270bb1b7dd59ea154bca26711ace2c21d4607c0fd4d5b26d14cb"

    /** A listing for forms' choices (listings.feature): the colours of a tone. */
    fun colours() = Listing("/colours", "colours") { query ->
        listOf("amber" to "warm", "red" to "warm", "blue" to "cool")
            .filter { (_, tone) -> query["tone"] == null || query["tone"] == tone }
            .map { (name, tone) -> linkedMapOf("name" to name, "tone" to tone) }
    }

    fun resource(store: LampStore): HeartResource<Lamp, String> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> store.read(id) },
        perform = { lamp, act, input ->
            val next = when (act) {
                "dim" -> lamp.copy(brightness = brightness(lamp, input))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            // Recorded only if the lamp is still the one the client's ETag matched.
            if (!store.lamps.replace(lamp.id, lamp, next)) {
                throw ConcurrentWriteException("lamp ${lamp.id} was written first")
            }
            Acted(next)
        },
        past = Past(
            at = { id, instant -> store.lamps[id]?.let { store.at(id, instant) } ?: throw notFound(id) },
            history = { id -> store.lamps[id]?.let { store.events[id].orEmpty() } ?: throw notFound(id) },
        ),
        performAtBookmark = { _, input ->
            val id = (store.lamps.keys.maxOrNull() ?: 0) + 1
            val lamp = Lamp(id, "OFF", checkNotNull(input.bind(InstallLamp::class.java).brightness), version = 1)
            store.lamps[id] = lamp
            Acted(lamp)
        },
        find = { name, id ->
            store.lamps.values.sortedBy { it.id }
                .map { Found(it.id, "Lamp ${it.id}") }
                .filter { name == null || it.title.contains(name, ignoreCase = true) }
                .filter { id == null || it.id == id }
        },
    )

    /** The brightness to dim [lamp] to. A rule only the lamp knows: not the brightness it already has. */
    private fun brightness(lamp: Lamp, input: ActInput): Int {
        val brightness = checkNotNull(input.bind(Dim::class.java).brightness)
        if (brightness == lamp.brightness) {
            throw RuleRefusedException.at("/brightness", "The lamp is already at $brightness")
        }
        return brightness
    }
}

private fun notFound(id: Long) = ResourceNotFoundException("no lamp $id")

/**
 * How the binding under test reads its configured credentials: from users
 * (`name:bcrypt,...`) and tokens (`sha256-hex,...`), to a check of an
 * Authorization header value, or a refusal to start. Each binding registers one.
 */
fun interface CredentialReader {
    fun read(users: String, tokens: String): (authorization: String?) -> Boolean
}
