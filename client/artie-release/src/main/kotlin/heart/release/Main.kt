package heart.release

import com.artie.heart.sims.release.Release
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * The merge watch release, with ARTIE inside its parties ([hosting]), served as heart-sims' Release serves them. Set
 * in the environment:
 *
 * | Variable | What |
 * |---|---|
 * | RELEASE_HOST | the address every party is served and linked at, such as the Mac's on the home network |
 * | RELEASE_REPOSITORY | the library app's git repository (exhibits/merge-watch-release/make-app-repo.sh) |
 * | RELEASE_SCRIPT | the release's kept script (exhibits/merge-watch-release/release.heart.yaml) |
 * | JENKINS_ADMIN_USER, JENKINS_ADMIN_PASSWORD, JENKINS_HOOK_SECRET | as jenkins/run.sh started Jenkins with |
 * | RELEASE_PLAIN_HTTP | networks ARTIE may send credentials to over plain HTTP, as masks; default 192.168.0.0/16 |
 */
fun main(args: Array<String>) {
    val env = System.getenv()
    val host = env["RELEASE_HOST"] ?: "localhost"
    val masks = (env["RELEASE_PLAIN_HTTP"] ?: "192.168.0.0/16").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val replies = java.util.concurrent.CompletableFuture<DeskReplies>()
    val release = Release(
        host,
        Path.of(env.getValue("RELEASE_REPOSITORY")),
        env["JENKINS_ADMIN_USER"] ?: "artie",
        env.getValue("JENKINS_ADMIN_PASSWORD"),
        env.getValue("JENKINS_HOOK_SECRET"),
        hosting(host, Path.of(env.getValue("RELEASE_SCRIPT")), masks, { replies.complete(it) }, releaseRung(env)),
    )
    if (args.firstOrNull() != "capture") return release.serve()
    // capture GOAL OUT name=value…: the parties served, the release goal walked once with Claude, then done.
    thread(isDaemon = true) { release.serve() }
    check(awaitServing("http://$host:${Release.DEV_PORT}/", SERVING_SECONDS)) { "the parties never served" }
    val inputs = args.drop(CAPTURE_INPUTS).associate { it.substringBefore('=') to it.substringAfter('=') }
    val stopped = capture(Path.of(args[1]), Path.of(args[2]), inputs, replies.get(), masks)
    println(stopped ?: "captured in ${args[2]}")
    kotlin.system.exitProcess(if (stopped == null) 0 else 1)
}

private const val CAPTURE_INPUTS = 3
private const val SERVING_SECONDS = 30L
