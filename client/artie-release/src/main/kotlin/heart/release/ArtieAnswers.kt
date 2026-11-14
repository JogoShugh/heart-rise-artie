package heart.release

import com.artie.heart.sims.rise.Answers
import heart.Credential
import heart.CredentialSource
import heart.Hosted
import heart.HttpTransport
import heart.Origin
import heart.SigningKey
import heart.Transport
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * A party answering the replies it was asked on through ARTIE, run inside it (release-desk.feature): a kept script
 * of one step, from the reply's own address, to act on its form for the answer by relation (`hr:completed`, …),
 * as that form says, signed with the party's [key], presenting [token] at the reply's origin. The party knows no
 * reply's form in advance; ARTIE reads it.
 */
class ArtieAnswers(
    private val party: String,
    private val key: SigningKey,
    private val token: String,
    private val plainHttp: List<String> = emptyList(),
    private val wire: Transport = HttpTransport(),
) : Answers {
    override fun answer(reply: String, relation: String, body: Map<String, Any?>): Response {
        val at = Origin.of(reply)
        val credentials = object : CredentialSource {
            override fun credentialFor(origin: Origin): Credential? = Credential.Bearer(token).takeIf { origin == at }
        }
        val script = mapOf(
            "heart" to 1,
            "kind" to "kept",
            "start" to reply,
            "steps" to listOf(mapOf("act" to "hr:$relation", "fields" to body)),
        )
        val run = Hosted(credentials, key, wire, party, plainHttp).run(script)
        val stopped = run.stopped ?: return Response(Status.OK)
        return Response(Status.CONFLICT).body(stopped.message.orEmpty())
    }
}
