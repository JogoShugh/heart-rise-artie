import heart.ArtieClient
import heart.AuthTransport
import heart.CachingTransport
import heart.CredentialSources
import heart.Dialect
import heart.HttpTransport
import heart.IntegrityCheck
import heart.Rung
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * ARTIE driven by code: the way the README shows it, written once on Monday and
 * never changed. It names no URI, method or status, but it does name the act
 * (`garden:get-bed`, `garden:water-cell`) and its field (`milliliters`): those
 * are bound when it is written. It prints what happened, and every request it
 * sent with its status, as `CALL <method> <path> <status>` lines.
 */
fun main() {
    val bookmark = System.getenv("HEART_BOOKMARK") ?: "http://localhost:8080/"
    val credentials = CredentialSources.standard(System.getenv(), Path.of(System.getProperty("user.home")))
    val log = CallLog(HttpTransport())
    val client = ArtieClient(CachingTransport(IntegrityCheck(AuthTransport(credentials, log))), Rung.L2, Dialect.HAL_SCHEMA_FORMS)
    val outcome = runCatching {
        client.enter(bookmark)
        client.act("garden:get-bed", buildJsonObject { put("id", 2) })
        client.act("garden:water-cell", buildJsonObject { put("milliliters", 250) })
    }
    log.calls.forEach { println("CALL ${it["method"]} ${it["path"]} ${it["status"]}") }
    outcome.fold(
        onSuccess = { println("RESULT watered bed 2") },
        onFailure = {
            println("RESULT failed: ${it.message}")
            exitProcess(1)
        },
    )
}
