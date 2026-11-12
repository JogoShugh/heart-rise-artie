package heart.release

import com.artie.heart.sims.release.Release
import java.nio.file.Path

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
fun main() {
    val env = System.getenv()
    val host = env["RELEASE_HOST"] ?: "localhost"
    val masks = (env["RELEASE_PLAIN_HTTP"] ?: "192.168.0.0/16").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Release(
        host,
        Path.of(env.getValue("RELEASE_REPOSITORY")),
        env["JENKINS_ADMIN_USER"] ?: "artie",
        env.getValue("JENKINS_ADMIN_PASSWORD"),
        env.getValue("JENKINS_HOOK_SECRET"),
        hosting(host, Path.of(env.getValue("RELEASE_SCRIPT")), masks),
    ).serve()
}
