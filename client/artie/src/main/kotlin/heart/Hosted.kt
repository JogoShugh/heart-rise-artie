package heart

import heart.script.Navigator
import heart.script.Run
import heart.script.ScriptStopped
import heart.script.runData

/**
 * ARTIE, run by a service in its own process to make its calls to other parties (hosted.feature). ARTIE never
 * serves: the host gives it the [credentials] it calls with, and no others (no environment, no `~/.artie`), the
 * [signing] key its acts are signed with, if any, and the [wire] they go out on. Each request names the host in
 * User-Agent, as `ARTIE/… ([name])`. Several hosts may run ARTIE in one process at once: each run is its own.
 */
class Hosted(
    private val credentials: CredentialSource = CredentialSources.NONE,
    private val signing: SigningKey? = null,
    private val wire: Transport = HttpTransport(),
    private val name: String = "host",
) {
    /** A client of its own, for one run: its own cache and trace, on the host's wire. */
    fun navigator(): Navigator {
        val signed = signing?.let { SigningTransport(it, wire) } ?: wire
        val named = UserAgentTransport("${UserAgentTransport.DEFAULT} ($name)", TraceContextTransport(Trace(), signed))
        val transport = CachingTransport(IntegrityCheck(AuthTransport(credentials, named)))
        return ArtieNavigator(ArtieClient(transport))
    }

    /** Runs [script], a kept script held as data, with [inputs]; the run comes back, ended or stopped. */
    fun run(script: Map<*, *>, inputs: Map<String, String> = emptyMap()): HostedRun {
        val run = Run(inputs = inputs, navigator = ::navigator)
        Run.current = run
        val stopped = try {
            runData(script)
            null
        } catch (stop: ScriptStopped) {
            stop
        }
        return HostedRun(run, stopped)
    }
}

/** A hosted run, back with its host: its [run], trace and notes, and where it [stopped], or null if it ended. */
class HostedRun(val run: Run, val stopped: ScriptStopped?)
