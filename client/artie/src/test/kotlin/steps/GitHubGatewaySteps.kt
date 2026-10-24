package steps

import com.artie.heart.gateway.fixtures.Comment
import com.artie.heart.gateway.fixtures.GitHubGateway
import com.artie.heart.gateway.fixtures.GitHubUpstream
import com.artie.heart.gateway.fixtures.Issue
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import java.nio.file.Path

/** github-gateway.feature: the GitHub gateway and a small GitHub, in process, for ARTIE to drive. */
class GitHubGatewaySteps(private val ctx: ClientContext) {
    /** The small GitHub, as it stands: other steps read it after a run. */
    val github = GitHubUpstream()

    @Given("a small GitHub knows these callers")
    fun callers(table: DataTable) =
        table.asMaps().forEach { github.callers[it.getValue("token")] = it.getValue("caller") }

    @Given("{string} has these issues on the small GitHub")
    fun issues(repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        table.asMaps().forEach { row ->
            github.issues += Issue(
                owner, repo, row.getValue("number").toLong(), row.getValue("title"), row.getValue("state"),
                stateReason = null, locked = false, author = row.getValue("author"),
            )
        }
    }

    @Given("issue {long} of {string} has these comments on the small GitHub")
    fun comments(number: Long, repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        table.asMaps().forEach { row ->
            github.comments +=
                Comment(owner, repo, number, row.getValue("id").toLong(), row.getValue("author"), row.getValue("body"))
        }
    }

    @Given("ARTIE talks to the GitHub gateway at {string} in front of the small GitHub")
    fun gateway(origin: String) {
        // The gateway as shipped: its own connector folder, beside this build.
        ctx.inProcess = InProcessTransport(GitHubGateway.handler(github, CONNECTOR))
        ctx.baseUrl = null
        check(origin.startsWith("http")) { "an http origin, as the gateway serves" }
    }

    @Given("ARTIE talks straight to the small GitHub at {string}")
    fun straight(origin: String) {
        // No gateway: a bare script's calls (bare-script.feature) reach GitHub's own REST API.
        ctx.inProcess = InProcessTransport(github.handler)
        ctx.baseUrl = null
        check(origin.startsWith("http")) { "an http origin, as GitHub serves" }
    }

    @Given("the GitHub gateway's switch for acts with no validator is {word}")
    fun noValidatorSwitch(switch: String) {
        ctx.inProcess = InProcessTransport(GitHubGateway.handler(github, CONNECTOR, validatorRequired = switch != "on"))
    }

    private companion object {
        val CONNECTOR: Path = Path.of("..", "..", "heart", "heart-gateway", "connectors", "github")
    }
}
