package heart.release

import com.artie.heart.sims.rise.ArtieKey
import heart.ClaudeReasoner
import heart.Hosted
import heart.HttpTransport
import heart.ModelKey
import heart.SigningKey
import heart.cli.goalFile
import heart.script.Run
import heart.script.ScriptStopped
import heart.script.runGoal
import java.nio.file.Files
import java.nio.file.Path

/**
 * The release goal's one inference run (goal-rise.feature): the desk's ARTIE walks the goal file at [goal] with
 * Claude planning each move and choosing each act, asking with the desk's [replies], signing as the desk, given
 * [inputs]; its capture is written to [out], for artie generate to keep as the release script every run after reads,
 * with no model. Returns where it stopped, or null when it finished.
 */
fun capture(goal: Path, out: Path, inputs: Map<String, String>, replies: DeskReplies, masks: List<String>): String? {
    val home = Path.of(System.getProperty("user.home"))
    val key = checkNotNull(ModelKey.standard(System.getenv(), home)) {
        "no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials"
    }
    val reasoner = ClaudeReasoner(HttpTransport(), key.value, ClaudeReasoner.DEFAULT_MODEL)
    val hosted = Hosted(
        PartyCredentials, SigningKey(ArtieKey.KEY_ID, ArtieKey.pair.private), name = "desk",
        plainHttp = masks, replies = replies,
    ).also { it.reasoner = reasoner }
    val navigator = hosted.navigator()
    val planner = checkNotNull(navigator.planner()) { "the desk's ARTIE has no planner" }
    val run = Run(inputs = inputs, navigator = { navigator })
    run.replies = replies
    run.reasoning = true
    Run.current = run
    val stopped = try {
        runGoal(goalFile(goal.fileName.toString(), Files.readString(goal)), planner)
        null
    } catch (stop: ScriptStopped) {
        stop.message
    }
    run.usage = navigator.usage
    run.model = navigator.model
    Files.writeString(out, run.traceYaml())
    return stopped
}
