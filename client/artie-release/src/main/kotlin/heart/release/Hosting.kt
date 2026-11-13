package heart.release

import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.release.DevEnvironment
import com.artie.heart.sims.release.Release
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.OfferWatcher
import heart.Credential
import heart.CredentialSource
import heart.Hosted
import heart.Origin
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
): Release.Hosting = Release.Hosting(
    answers = { key -> ArtieAnswers(key.keyId, SigningKey(key.keyId, key.pair.private), Release.REPLY_TOKEN, masks) },
    front = { service ->
        ReleaseDesk(
            script = { org.yaml.snakeyaml.Yaml().load<Map<*, *>>(Files.readString(script)) },
            replies = DeskReplies(service, "http://$host:${Release.REPLIES_PORT}").also(kept),
            artie = { kept ->
                Hosted(
                    PartyCredentials, SigningKey(ArtieKey.KEY_ID, ArtieKey.pair.private), name = "desk",
                    plainHttp = masks, replies = kept,
                )
            },
            tokens = listOf(ReleaseDesk.TOKEN, Release.REPLY_TOKEN),
            keys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        ).handler
    },
)

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
