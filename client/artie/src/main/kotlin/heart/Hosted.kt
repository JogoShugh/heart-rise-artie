package heart

import heart.script.Navigator
import heart.script.Replies
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
    /** Networks the host lets ARTIE send credentials to over plain HTTP, as masks: `192.168.0.0/16`. */
    plainHttp: List<String> = emptyList(),
    /** The replies the host keeps, for a script that asks to be answered later (RISE); null when it keeps none. */
    private val replies: Replies? = null,
) {
    private val masks = plainHttp.map(::AddressMask)

    /** A client of its own, for one run: its own cache and trace, on the host's wire. */
    fun navigator(): Navigator {
        // Every exchange on the wire is kept for the run's capture, credentials written `<token>`, as sent: signed.
        val log = ExchangeLog(wire)
        val signed = signing?.let { SigningTransport(it, log) } ?: log
        val named = UserAgentTransport("${UserAgentTransport.DEFAULT} ($name)", TraceContextTransport(Trace(), signed))
        val bare = AuthTransport(credentials, named, masks)
        val transport = CachingTransport(IntegrityCheck(bare))
        // A script a link hands over is read as it is, below the cache, as a bare call is.
        return WiredNavigator(ArtieNavigator(ArtieClient(transport), exchangeLog = log), bare)
    }

    /** Runs [script], a kept script held as data, with [inputs]; the run comes back, ended or stopped. */
    fun run(script: Map<*, *>, inputs: Map<String, String> = emptyMap()): HostedRun {
        val run = Run(inputs = inputs, navigator = ::navigator)
        run.replies = replies
        Run.current = run
        val stopped = try {
            runData(script)
            null
        } catch (stop: ScriptStopped) {
            stop
        } catch (refused: IllegalArgumentException) {
            // ARTIE refused before sending (a credential over plain HTTP): the host gets the run back, stopped.
            ScriptStopped(run.step, "", "\"${refused.message}\"")
        }
        return HostedRun(run, stopped)
    }
}

/** A hosted run, back with its host: its [run], trace and notes, and where it [stopped], or null if it ended. */
class HostedRun(val run: Run, val stopped: ScriptStopped?)
