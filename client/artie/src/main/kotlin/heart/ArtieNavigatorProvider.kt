package heart

import heart.script.ClientSettings
import heart.script.Navigator
import heart.script.NavigatorProvider
import java.nio.file.Path

/**
 * ARTIE, as the client a script host finds by ServiceLoader: over HTTP and CoAP, with the standard
 * credential sources (environment, `~/.artie/credentials`, Vault), caching and checking integrity.
 */
class ArtieNavigatorProvider : NavigatorProvider {
    override fun navigator(): Navigator = navigator(ClientSettings())

    override fun navigator(settings: ClientSettings): Navigator {
        val home = Path.of(System.getProperty("user.home"))
        return standard(System.getenv(), home, settings)
    }

    companion object {
        private const val NO_KEY =
            "no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials"
        /**
         * ARTIE with the credentials [env] and [userHome] hold: over HTTP and HTTPS, plain CoAP, and coaps,
         * whose pre-shared keys stay inside its DTLS connector, as [settings] ask. With the reasoner `claude`, its
         * `reason(…)` steps ask Claude, with the model key from [ModelKey.standard]; with no key, they are refused,
         * saying where one goes. Every request carries its W3C traceparent, so a server's own record of what it did
         * for it (the gateway's upstream calls) can be tied to the step. With exchanges asked for, those on the wire
         * are kept for the run's capture. With pins, each act is held to the script's lock file first. A bare
         * script's calls (spec/ideas/bare-runs.md) go out with the same credentials, trace and log, and nothing else.
         */
        fun standard(env: Map<String, String>, userHome: Path, settings: ClientSettings = ClientSettings()):
            Navigator {
            val rung = settings.rung?.let(Rung::valueOf) ?: Rung.L0
            val guarding = settings.guarding
            val guard = settings.pins?.let {
                ScriptGuard(
                    it,
                    strict = guarding.guard == "strict",
                    values = guarding.onChange == "values",
                    everything = guarding.look == "everything",
                )
            }
            val credentials = CredentialSources.standard(env, userHome)
            val log = if (settings.exchanges) ExchangeLog(HttpTransport()) else null
            val wire = TraceContextTransport(Trace(), UserAgentTransport(next = log ?: HttpTransport()))
            val transport = CachingTransport(IntegrityCheck(AuthTransport(credentials, wire)))
            val coaps = mapOf("coaps" to CoapBinding(credentials = credentials))
            val care = Care(validators = guarding.validators != "none")
            val client = ArtieClient(transport, rung, care = care, bindings = coaps)
            val claude = settings.reasoner == "claude"
            // The model's address (spec/PLAN-LOCAL-MODEL.md): Anthropic's needs a key; another takes one only
            // when the credentials file holds one for it.
            val origin = settings.model.url?.trimEnd('/') ?: ModelKey.ORIGIN
            val key = if (claude) ModelKey.standard(env, userHome, origin) else null
            val named = guarding.guard
            val navigator = when {
                !claude -> ArtieNavigator(client, exchangeLog = log, guard = guard, guarding = named)
                key == null && origin == ModelKey.ORIGIN -> ArtieNavigator(client, null, NO_KEY, log, guard, named)
                else -> {
                    val model = settings.model.name ?: ClaudeReasoner.DEFAULT_MODEL
                    val length = settings.model.maxTokens ?: ClaudeReasoner.MAX_TOKENS
                    val at = origin + ClaudeReasoner.MESSAGES
                    val reasoner = ClaudeReasoner(HttpTransport(), key?.value, model, at, length)
                    ArtieNavigator(client, reasoner, NO_KEY, log, guard, named)
                }
            }
            // A bare script's calls go out below ARTIE's cache and integrity check, on the same trace and log.
            return WiredNavigator(navigator, AuthTransport(credentials, wire))
        }
    }
}
