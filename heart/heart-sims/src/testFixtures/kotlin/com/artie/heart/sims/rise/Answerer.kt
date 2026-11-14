package com.artie.heart.sims.rise

import com.artie.heart.Dialect
import com.artie.heart.Signer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.net.URI
import java.time.Instant

/**
 * A party that answers RISE replies: it reads the reply where the request said, finds the form for the answer it
 * gives (`hr:progress`, `hr:completed` or `hr:failed`), and sends it as that form says, by its method, target and
 * content type, on the ETag it just read, signed (RFC 9421) with [signer], presenting [authorization] if the reply's
 * service asks for one. It knows no reply's address in advance, and no form's.
 */
class Answerer(
    private val client: HttpHandler,
    private val signer: Signer,
    private val authorization: String? = null,
) : Answers {
    private val mapper = ObjectMapper()

    /** Answers [reply] with [relation] (`progress`, `completed`, `failed`) and [body]; the reply service's answer. */
    override fun answer(reply: String, relation: String, body: Map<String, Any?>): Response {
        val read = client(authorized(Request(Method.GET, reply)).header("Accept", Dialect.JSONSCHEMA.mediaType))
        check(read.status.successful) { "the reply $reply answered ${read.status.code}" }
        val etag = checkNotNull(read.header("ETag")) { "the reply $reply gave no ETag" }
        val form: JsonNode = mapper.readTree(read.bodyString()).path("_forms").path("hr:$relation")
        check(!form.isMissingNode) { "the reply $reply offers no hr:$relation now" }
        val target = URI(reply).resolve(form.path("_links").path("target").path("href").asText())
        val method = form.path("method").asText()
        val text = mapper.writeValueAsString(body)
        val origin = "${target.scheme}://${target.rawAuthority}"
        val signed = signer.sign(method, origin + target.rawPath, text, etag, Instant.now().epochSecond)
        return client(
            authorized(Request(Method.valueOf(method), target.toString()))
                .header("Content-Type", form.path("contentType").asText())
                .header("Accept", Dialect.JSONSCHEMA.mediaType)
                .header("If-Match", etag)
                .header("Content-Digest", signed.contentDigest)
                .header("Signature-Input", "sig1=${signed.params}")
                .header("Signature", "sig1=:${signed.signature}:")
                .body(text),
        )
    }

    private fun authorized(request: Request) = authorization?.let { request.header("Authorization", it) } ?: request
}
