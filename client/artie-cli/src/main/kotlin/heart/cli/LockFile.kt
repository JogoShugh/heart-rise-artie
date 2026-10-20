package heart.cli

import heart.script.FormPin
import heart.script.Pins
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * The lock file a run holds to (spec/PLAN-SCRIPT-GENERATOR.md, "Guards"): the file --pins names, or NAME.pins.yaml
 * beside NAME.kts. With none, the run holds to nothing, unless it asked for --guard: then it stops before its first
 * request. A run asked at another rung than the lock file's stops too; without --rung, it reads at the lock file's.
 */
class LockFile(val pins: Pins?, val guard: String?, val rung: String?)

internal const val STOPPED = "stopped before the first request"

/** The lock file for [asked], whose script is [script], found in [folder], held to as the flag, then [config], says. */
fun lockFile(asked: Arguments, folder: Path, script: Path, config: Config = Config()): LockFile {
    val name = asked.pins ?: (script.fileName.toString().substringBeforeLast('.') + ".pins.yaml")
    val file = folder.resolve(name)
    if (!Files.exists(file)) {
        val wanted = if (asked.pins != null) "--pins" else "--guard".takeIf { asked.guard != null }
        if (wanted != null) throw BadArguments("$STOPPED: $wanted, but no lock file at $name")
        return LockFile(null, null, asked.rung)
    }
    val pins = pins(Files.readString(file))
    if (asked.rung != null && pins.rung != null && asked.rung != pins.rung) {
        throw BadArguments("$STOPPED: the lock file was recorded at ${pins.rung}, not ${asked.rung}")
    }
    return LockFile(pins, asked.guard ?: config.guard ?: "lenient", asked.rung ?: pins.rung)
}

/** The lock file whose text is [text], read. */
internal fun pins(text: String): Pins {
    val doc = Yaml().load<Map<String, Any?>>(text).orEmpty()
    val forms = (doc["forms"] as? Map<*, *>).orEmpty().entries.associate { (rel, pin) ->
        val fields = pin as? Map<*, *>
        rel.toString() to FormPin(
            fields?.get("integrity")?.toString(),
            (fields?.get("sets") as? List<*>).orEmpty().map { it.toString() },
            fields?.get("method")?.toString(),
        )
    }
    val links = (doc["links"] as? Map<*, *>).orEmpty().entries
        .associate { (rel, target) -> rel.toString() to target.toString() }
    return Pins(doc["rung"]?.toString(), forms, links)
}
