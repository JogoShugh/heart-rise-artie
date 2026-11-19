package heart.release

import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.release.DevEnvironment
import com.artie.heart.sims.release.Release
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.OfferWatcher
import heart.Credential
import heart.CredentialSource
import heart.Dialect
import heart.Hosted
import heart.HttpCache
import heart.Origin
import heart.Rung
import heart.SigningKey
import java.nio.file.Files
import java.nio.file.Path

/**
 * The release's parties with ARTIE inside them (release-desk.feature): the watch and the CI wrapper answer through
 * ARTIE, each signing with its own key; on the replies' port, the release desk, at [host], running the kept script
 * at [script] with ARTIE, which signs as the desk and may send credentials over plain HTTP to [masks].
 */
fun hosting(
    host: String,
    script: Path,
    masks: List<String>,
    /** Told of the desk's replies once it keeps them: a capture run in this process asks with them. */
    kept: (DeskReplies) -> Unit = {},
    /** The rung and the dialect the desk's ARTIE reads every party at and in. */
    reading: DeskReading = DeskReading(),
): Release.Hosting = Release.Hosting(
    answers = { key -> ArtieAnswers(key.keyId, SigningKey(key.keyId, key.pair.private), Release.REPLY_TOKEN, masks) },
    front = { service ->
        // One HTTP cache for every release the desk runs: a form set or schema one release read, the next reads
        // from here, with no request (hosted.feature).
        val cache = HttpCache()
        ReleaseDesk(
            script = { org.yaml.snakeyaml.Yaml().load<Map<*, *>>(Files.readString(script)) },
            replies = DeskReplies(service, "http://$host:${Release.REPLIES_PORT}").also(kept),
            artie = { kept ->
                Hosted(
                    PartyCredentials,
                    SigningKey(ArtieKey.KEY_ID, ArtieKey.pair.private),
                    name = "desk",
                    plainHttp = masks,
                    replies = kept,
                ).also {
                    it.rung = reading.rung
                    it.dialect = reading.dialect
                    it.cache = cache
                }
            },
            tokens = listOf(ReleaseDesk.TOKEN, Release.REPLY_TOKEN),
            keys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        ).handler
    },
)

/** The rung the release's [env] says its desk reads at, RELEASE_RUNG: L0, all in one answer, when it says none. */
fun releaseRung(env: Map<String, String>): Rung =
    env["RELEASE_RUNG"]?.takeIf { it.isNotBlank() }?.let(Rung::valueOf) ?: Rung.L0

/** The dialect the release's [env] says its desk asks in, RELEASE_DIALECT: HAL Schema Forms when it says none. */
fun releaseDialect(env: Map<String, String>): Dialect =
    env["RELEASE_DIALECT"]?.takeIf { it.isNotBlank() }?.let(Dialect::named) ?: Dialect.HAL_SCHEMA_FORMS

/** How the desk's ARTIE reads every party: the rung it asks for (SPEC §6.10) and the dialect it asks in (§6.1). */
data class DeskReading(val rung: Rung = Rung.L0, val dialect: Dialect = Dialect.HAL_SCHEMA_FORMS) {
    companion object {
        /** As the release's [env] says: RELEASE_RUNG and RELEASE_DIALECT. */
        fun of(env: Map<String, String>): DeskReading = DeskReading(releaseRung(env), releaseDialect(env))
    }
}

/** The credential the desk's ARTIE presents at each party, by port: each party's own test credential, and no other. */
object PartyCredentials : CredentialSource {
    private const val PREVIEW_PORT = 8180

    // The preview is the library: a member's card number and PIN, the example's test card.
    private const val LIBRARY_CARD = "21000451"
    private const val LIBRARY_PIN = "1234"

    private val byPort = mapOf(
        Release.GITHUB_PORT to Credential.Bearer(GitHubService.TOKEN),
        Release.GITLAB_PORT to Credential.Bearer(Release.GITLAB_TOKEN),
        Release.GITHUB_LIVE_PORT to Credential.Bearer(GitHubService.TOKEN),
        Release.WATCHER_PORT to Credential.Bearer(OfferWatcher.TOKEN),
        Release.REPLIES_PORT to Credential.Bearer(Release.REPLY_TOKEN),
        Release.CI_PORT to Credential.Bearer(CiWrapper.TOKEN),
        Release.DEV_PORT to Credential.Bearer(DevEnvironment.TOKEN),
        PREVIEW_PORT to Credential.Basic(LIBRARY_CARD, LIBRARY_PIN),
    )

    override fun credentialFor(origin: Origin): Credential? = byPort[origin.port]
}
