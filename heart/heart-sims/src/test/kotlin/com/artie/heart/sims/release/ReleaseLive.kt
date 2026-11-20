package com.artie.heart.sims.release

import com.artie.heart.Signatures
import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.rise.ArtieKey
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Against the release's parties running (`MainKt release`, the real Jenkins): opens a reply, asks the wrapper to
 * build a commit of the library repo, signed, and waits on the reply until Jenkins' notifications have been told on
 * it. Runs only with RELEASE_LIVE (the host) and RELEASE_COMMIT set.
 */
class ReleaseLive {
    private val mapper = ObjectMapper()
    private val client = JavaHttpClient()

    @Test
    fun buildsACommitAndIsToldOnItsReply() {
        val host = System.getenv("RELEASE_LIVE") ?: return
        val commit = checkNotNull(System.getenv("RELEASE_COMMIT"))
        val replies = "http://$host:${Release.REPLIES_PORT}"
        val ci = "http://$host:${Release.CI_PORT}"
        val reply = "r-build-${commit.take(7)}-${Instant.now().epochSecond}"
        check(client(Request(Method.POST, "$replies/_release/replies/$reply")).status.successful)

        val index = client(Request(Method.GET, "$ci/").header("Authorization", BEARER).header("Accept", HAL))
        val etag = checkNotNull(index.header("ETag"))
        val body = mapper.writeValueAsString(
            mapOf(
                "commit" to commit,
                "_links" to mapOf(
                    "hr:reply-to" to mapOf("href" to "$replies/replies/$reply"),
                    "hr:keys" to mapOf("href" to "$replies${Signatures.KEYS}"),
                ),
            ),
        )
        val signed = ArtieKey.signer.sign("POST", ci + CiWrapper.BUILD, body, etag, Instant.now().epochSecond)
        val made = client(
            Request(Method.POST, ci + CiWrapper.BUILD).header("Authorization", BEARER).header("Accept", HAL)
                .header("Content-Type", "application/json").header("If-Match", etag)
                .header("Content-Digest", signed.contentDigest).header("Signature-Input", "sig1=${signed.params}")
                .header("Signature", "sig1=:${signed.signature}:").body(body),
        )
        assertEquals(201, made.status.code, made.bodyString())
        val build = ci + made.header("Location")
        check(client(Request(Method.PUT, "$replies/_release/replies/$reply/asked").body(build)).status.successful)
        println("build: $build, reply: $replies/replies/$reply")

        repeat(WAIT_SECONDS) {
            val read = Request(Method.GET, "$replies/replies/$reply").header("Accept", HAL)
                .header("Authorization", "Bearer ${Release.REPLY_TOKEN}")
            val now = mapper.readTree(client(read).bodyString())
            if (now["state"].asText() != "awaiting") {
                println("reply: ${now.toPrettyString()}")
                return
            }
            Thread.sleep(SECOND)
        }
        error("the reply was not answered in $WAIT_SECONDS seconds")
    }

    private companion object {
        const val HAL = "application/hal+json"
        const val BEARER = "Bearer ${CiWrapper.TOKEN}"
        const val WAIT_SECONDS = 120
        const val SECOND = 1000L
    }
}
