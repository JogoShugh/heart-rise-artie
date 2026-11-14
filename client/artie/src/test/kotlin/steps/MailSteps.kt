package steps

import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartSettings
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.net.URI
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * script-listeners.feature, slice 7: a plain mail service that publishes only OpenAPI, and the HEART gateway in
 * front of it, made from its description and a connector, beside the GitHub gateway: a script acts on both.
 */
class MailSteps(private val ctx: ClientContext) {
    private val mapper = ObjectMapper()

    /** Each mailbox, by name: whether it is OPEN or CLOSED. */
    private val mailboxes = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Each message sent, in order: its mailbox, to whom, its subject and its body. */
    private val sent = java.util.concurrent.CopyOnWriteArrayList<Map<String, String>>()

    private val upstream: HttpHandler = routes(
        "/mailboxes/{id}" bind Method.GET to { request ->
            mailbox(request.path("id").orEmpty())?.let { json(it) } ?: Response(Status.NOT_FOUND)
        },
        "/mailboxes/{id}/actions/send" bind Method.POST to { request ->
            val id = request.path("id").orEmpty()
            val fields = mapper.readTree(request.bodyString())
            sent += mapOf(
                "mailbox" to id,
                "to" to fields.path("to").asText(),
                "subject" to fields.path("subject").asText(),
                "body" to fields.path("body").asText(),
            )
            mailbox(id)?.let { json(it) } ?: Response(Status.NOT_FOUND)
        },
    )

    @Given(
        "ARTIE also talks to the mail gateway at {string}, in front of a small mail service with the mailbox {string}",
    )
    fun mailGateway(origin: String, mailbox: String) {
        mailboxes[mailbox] = "OPEN"
        val loaded = Connector.load(FOLDER, mapper)
        val description = checkNotNull(javaClass.getResource("/mail/openapi.json")).readText()
        val gateway = Gateway(
            GatewaySpec(description, loaded.reads), upstream, loaded.profile, UpstreamAuth.Forwarded,
            connector = loaded,
        ).handler(Credentials.forwarded(), HeartSettings())
        val host = URI(origin).host
        val mail = InProcessTransport(gateway)
        val others = checkNotNull(ctx.inProcess) { "the GitHub gateway first" }
        // Each request goes to the gateway its host names.
        ctx.inProcess = object : heart.Transport {
            override fun send(request: heart.HeartRequest): heart.HeartResponse =
                if (URI(request.url).host == host) mail.send(request) else others.send(request)
        }
    }

    @Then("the small mail service sent these messages")
    fun sentMessages(table: DataTable) = assertEquals(table.asMaps(), sent.map { it - "body" })

    private fun mailbox(id: String): Map<String, Any?>? = mailboxes[id]?.let { status ->
        val count = sent.count { it["mailbox"] == id }
        mapOf("id" to id.toLong(), "address" to "ops$id@rockets.test", "status" to status, "sent" to count)
    }

    private fun json(body: Any): Response =
        Response(Status.OK).header("Content-Type", "application/json").body(mapper.writeValueAsString(body))

    private companion object {
        val FOLDER: Path = Path.of(checkNotNull(MailSteps::class.java.getResource("/connectors/mail")).toURI())
    }
}
