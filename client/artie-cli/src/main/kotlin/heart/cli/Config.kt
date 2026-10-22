package heart.cli

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * A run's config (spec/ideas/replay-guards-lenient-strict.md, "Script, lock file and config"): how strictly it holds
 * to its lock file, set by a person in NAME.config.yaml beside NAME.kts, or in the file --config names. The tool
 * never writes it. With none, every setting is its default, as before there was a config.
 */
class Config(
    val guard: String? = null,
    /** Whether a run writes what it was newly offered into the lock file: auto, propose (says it), never. */
    val repin: String? = null,
    /**
     * What a lenient run does when a form it uses changed: refuse, the default, or values: act when the form keeps
     * its pinned method and what the script sends fits it.
     */
    val onChange: String? = null,
    /** The validator each act carries: last, the default, or none, a blind write (spec/ideas/off-the-rails.md). */
    val validators: String? = null,
)

/** The settings a config may hold, and the values each may take. */
private val SETTINGS: Map<String, Set<String>> = mapOf(
    "guard" to GUARDS,
    "repin" to setOf("auto", "propose", "never"),
    "on-change" to setOf("refuse", "values"),
    "validators" to VALIDATORS,
)

/** The config for [asked], whose script is [script], found in [folder]. */
fun config(asked: Arguments, folder: Path, script: Path): Config {
    val name = asked.config ?: (script.fileName.toString().substringBeforeLast('.') + ".config.yaml")
    val file = folder.resolve(name)
    if (!Files.exists(file)) {
        if (asked.config != null) throw BadArguments("$STOPPED: --config, but no config at $name")
        return Config()
    }
    val doc = Yaml().load<Map<String, Any?>>(Files.readString(file)).orEmpty()
    doc.forEach { (setting, value) -> held(name, setting, value) }
    return Config(
        guard = doc["guard"]?.toString(),
        repin = doc["repin"]?.toString(),
        onChange = doc["on-change"]?.toString(),
        validators = doc["validators"]?.toString(),
    )
}

/** [values] as a person lists them: `a, b or c`. */
internal fun said(values: Collection<String>): String =
    if (values.size < 2) values.joinToString() else values.toList().dropLast(1).joinToString() + " or " + values.last()

/** Stops the run when [setting] isn't one a config may hold, or [value] isn't one it may take. */
private fun held(name: String, setting: String, value: Any?) {
    val allowed = SETTINGS[setting]
    val problem = when {
        allowed == null -> "no setting $setting"
        value.toString() !in allowed -> "$setting is ${said(allowed)}"
        else -> return
    }
    throw BadArguments("$STOPPED: $name: $problem")
}
