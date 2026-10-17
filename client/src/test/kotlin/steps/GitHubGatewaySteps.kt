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
    private val github = GitHubUpstream()

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
        val connector = Path.of("..", "heart", "heart-gateway", "connectors", "github")
        ctx.inProcess = InProcessTransport(GitHubGateway.handler(github, connector))
        ctx.baseUrl = null
        check(origin.startsWith("http")) { "an http origin, as the gateway serves" }
    }
}
