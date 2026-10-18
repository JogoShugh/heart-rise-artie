package heart

import java.nio.file.Path

/**
 * The key ARTIE reasons with (spec/PLAN-ARTIE-SCRIPTS.md, decisions): [value] is the key itself,
 * [source] where it was found. Said aloud, a key names its source and never its value.
 */
class ModelKey(val value: String, val source: String) {
    override fun toString() = "a model key, from $source"

    companion object {
        private const val ENVIRONMENT = "ANTHROPIC_API_KEY"
        private const val ORIGIN = "https://api.anthropic.com"

        /**
         * ANTHROPIC_API_KEY in [env] first; else the Bearer entry for https://api.anthropic.com in the
         * credentials file, ARTIE_HOME's or else `.artie` in [userHome]; null when neither holds one.
         */
        fun standard(env: Map<String, String>, userHome: Path): ModelKey? {
            env[ENVIRONMENT]?.takeIf { it.isNotBlank() }?.let { return ModelKey(it, "the environment") }
            val folder = env["ARTIE_HOME"]?.let { Path.of(it) } ?: userHome.resolve(".artie")
            val held = FileCredentials(folder.resolve("credentials")).credentialFor(Origin.of(ORIGIN))
            return (held as? Credential.Bearer)?.header()?.removePrefix("Bearer ")
                ?.let { ModelKey(it, "the credentials file") }
        }
    }
}
