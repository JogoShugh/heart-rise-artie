package heart

import heart.script.Pins
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Look-then-leap's one look (spec/ideas/off-the-rails.md), at the entry's manifest of every form the server
 * declares: whether the run may leave the rails, and what it says either way. Looking at what is [used], each
 * form [pins] holds must be listed with its pinned integrity; looking at everything, the manifest's digest must
 * be the one the lock file recorded.
 */
internal class LookThenLeap(private val pins: Pins, private val everything: Boolean) {

    /** Whether the run leaves the rails, having entered at [entry], and what it says of it. */
    fun look(entry: HeartRepresentation?): Pair<Boolean, String> {
        val manifest = entry?.state?.get(MANIFEST) as? JsonObject ?: return false to NO_MANIFEST
        return if (everything) everything(manifest) else used(manifest)
    }

    private fun everything(manifest: JsonObject): Pair<Boolean, String> {
        val digest = (manifest["digest"] as? JsonPrimitive)?.content
        return when (pins.digest) {
            null -> false to NO_DIGEST
            digest -> true to ALL_AS_WRITTEN
            else -> false to ALL_CHANGED
        }
    }

    private fun used(manifest: JsonObject): Pair<Boolean, String> {
        val listed = manifest["forms"] as? JsonObject
        val changed = pins.forms.filter { (rel, pin) ->
            val now = (listed?.get(rel) as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content }
            pin.integrity != null && pin.integrity !in now
        }.keys
        if (changed.isEmpty()) return true to USED_AS_PINNED
        return false to "Look then leap: ${changed.joinToString(", ")} changed since the lock file, so the run " +
            "stays lenient."
    }

    private companion object {
        const val MANIFEST = "hr:manifest"
        const val USED_AS_PINNED =
            "Look then leap: every form the script uses is as the lock file pins it, so the run leaves the rails."
        const val ALL_AS_WRITTEN = "Look then leap: every form the server declares is as when the lock file was " +
            "written, so the run leaves the rails."
        const val ALL_CHANGED =
            "Look then leap: the server's forms changed since the lock file was written, so the run stays lenient."
        const val NO_DIGEST =
            "Look then leap: the lock file records no digest of the server's forms, so the run stays lenient."
        const val NO_MANIFEST =
            "Look then leap: the entry lists no manifest of the server's forms, so the run stays lenient."
    }
}
