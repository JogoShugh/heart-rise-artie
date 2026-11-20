package com.artie.heart.sims.ci

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.toUrlFormEncoded
import java.util.Base64

/** What the wrapper asks of Jenkins: to build a job with parameters, and a build's log and archived files. */
interface Jenkins {
    /** Queues [job] with [parameters]; Jenkins names the build when it starts, in its notification. */
    fun build(job: String, parameters: Map<String, String>)

    /** The file [path] build [number] of [job] archived, or null when there is none. */
    fun artifact(job: String, number: Int, path: String): ByteArray?

    /** Build [number] of [job]'s console text, or null when there is none. */
    fun log(job: String, number: Int): String?
}

/** A real Jenkins at [base], over its remote API, as [user] with [password]. */
class JenkinsHttp(
    private val client: HttpHandler,
    private val base: String,
    user: String,
    password: String,
) : Jenkins {
    private val auth = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())

    override fun build(job: String, parameters: Map<String, String>) {
        val query = parameters.toList().toUrlFormEncoded()
        val answer = client(Request(Method.POST, Uri.of("$base/job/$job/buildWithParameters?$query")).header("Authorization", auth))
        check(answer.status.successful) { "Jenkins refused to build $job: ${answer.status}" }
    }

    override fun artifact(job: String, number: Int, path: String): ByteArray? =
        read("$base/job/$job/$number/artifact/artifacts/$path")?.let { it.body.payload.array() }

    override fun log(job: String, number: Int): String? = read("$base/job/$job/$number/consoleText")?.bodyString()

    private fun read(url: String) =
        client(Request(Method.GET, url).header("Authorization", auth)).takeIf { it.status.successful }
}
