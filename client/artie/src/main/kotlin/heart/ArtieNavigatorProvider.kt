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
        val rung = settings.rung?.let(Rung::valueOf) ?: Rung.L0
        val home = Path.of(System.getProperty("user.home"))
        val guard = settings.pins?.let {
            ScriptGuard(it, strict = settings.guard == "strict", values = settings.onChange == "values")
        }
        return standard(System.getenv(), home, rung, settings.reasoner, settings.exchanges, guard)
    }

    companion object {
        private const val NO_KEY =
            "no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials"

        /**
         * ARTIE with the credentials [env] and [userHome] hold: over HTTP and HTTPS, plain CoAP, and coaps,
         * whose pre-shared keys stay inside its DTLS connector. With [reasoner] `claude`, its `reason(…)` steps
         * ask Claude, with the model key from [ModelKey.standard]; with no key, they are refused, saying where
         * one goes. Every request carries its W3C traceparent, so a server's own record of what it did for it
         * (the gateway's upstream calls) can be tied to the step. With [exchanges], the exchanges on the wire
         * are kept for the run's capture. With [guard], each act is held to the script's lock file first.
         */
        @Suppress("LongParameterList") // each is one thing a run asks of its client
        fun standard(
            env: Map<String, String>,
            userHome: Path,
            rung: Rung = Rung.L0,
            reasoner: String? = null,
            exchanges: Boolean = false,
            guard: ScriptGuard? = null,
        ): Navigator {
            val credentials = CredentialSources.standard(env, userHome)
            val log = if (exchanges) ExchangeLog(HttpTransport()) else null
            val wire = TraceContextTransport(Trace(), UserAgentTransport(next = log ?: HttpTransport()))
            val transport = CachingTransport(IntegrityCheck(AuthTransport(credentials, wire)))
            val coaps = mapOf("coaps" to CoapBinding(credentials = credentials))
            val client = ArtieClient(transport, rung, bindings = coaps)
            val key = if (reasoner == "claude") ModelKey.standard(env, userHome) else null
            return when {
                reasoner != "claude" -> ArtieNavigator(client, exchangeLog = log, guard = guard)
                key == null -> ArtieNavigator(client, null, NO_KEY, log, guard)
                else -> ArtieNavigator(client, ClaudeReasoner(HttpTransport(), key.value), NO_KEY, log, guard)
            }
        }
    }
}
