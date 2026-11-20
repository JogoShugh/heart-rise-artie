package heart.minecraft

import com.artie.heart.sims.Simulator
import com.artie.heart.sims.deploy.Region
import heart.HeartRequest
import heart.HeartResponse
import heart.Transport
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request

/** The simulated deploy service, in memory, as one scenario sets it up and changes it behind the player's back. */
class DeploySteps(private val play: Play) {
    @Given("the deploy service's app {string} has the regions")
    fun regions(app: String, table: DataTable) {
        val regions = table.asMaps().groupBy({ it.getValue("environment") }) { row ->
            Region(row.getValue("name"), row.getValue("label"), row.getValue("available").toBoolean())
        }
        play.sims.setRegions(app, regions)
    }

    @Given("the deploy service has the releases")
    fun releases(table: DataTable) = table.asMaps().forEach { row ->
        play.sims.publishRelease(row.getValue("app"), row.getValue("version"), row.getValue("status"))
    }

    @When("someone else starts deploying {string}")
    fun someoneElseDeploys(app: String) = play.sims.startDeploying(app)
}

/** What one scenario holds: the simulated services, and the way to them. */
class Play {
    val sims = Simulator()

    /** The services, reached in this process: the same requests and answers, with no network between. */
    val wire = object : Transport {
        override fun send(request: HeartRequest): HeartResponse {
            val bare = Request(Method.valueOf(request.method), request.url)
            val sent = request.headers.entries.fold(bare) { built, (name, value) -> built.header(name, value) }
            val answer = sims.handler(request.body?.let { sent.body(it) } ?: sent)
            val headers = answer.headers.groupBy({ it.first }, { it.second.orEmpty() })
            return HeartResponse(answer.status.code, headers, answer.body.stream.readBytes())
        }
    }

    companion object {
        /** Where the deploy service is: over https, so ARTIE sends its credential as it would beyond the machine. */
        const val DEPLOY = "https://deploy.test"

        /** The Bearer token the deploy service admits. */
        const val TOKEN = "deploy-token"
    }
}
