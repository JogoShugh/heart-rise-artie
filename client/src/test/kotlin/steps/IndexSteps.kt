package steps

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Steps for entering at the service index, GET forms, and following links by relation and title. */
class IndexSteps(private val ctx: ClientContext) {

    private var requestsBeforeFollow = 0

    @When("the client follows the {string} link titled {string}")
    fun follow(rel: String, title: String) {
        ctx.current = ctx.client.follow(rel, title)
    }

    /** A link with no title, such as a page link: chosen by its relation alone. */
    @When("the client follows the {string} link")
    fun followUntitled(rel: String) {
        ctx.current = ctx.client.follow(rel, null)
    }

    @When("the client tries to follow the {string} link titled {string}")
    fun tryFollow(rel: String, title: String) {
        requestsBeforeFollow = ctx.transport.requests.size
        ctx.lastError = runCatching { ctx.client.follow(rel, title) }.exceptionOrNull()
    }

    @Then("the client sent no request for it")
    fun noRequestForIt() = assertEquals(requestsBeforeFollow, ctx.transport.requests.size)

    @Then("that request had no body")
    fun noBody() = assertNull(ctx.transport.requests.last().body)

    @Then("the client's last request was {word} {string}")
    fun lastRequest(method: String, url: String) {
        val last = ctx.transport.requests.last()
        assertEquals("$method $url", "${last.method} ${last.url}")
    }

    /** Collects the items of every page, following [rel] by relation until a page has none. */
    @Then("following {string} from the search reaches every item it counts")
    fun followsEveryPage(rel: String) {
        val counted = ctx.current.state["count"]?.jsonPrimitive?.int ?: error("the search has no count")
        val seen = mutableListOf<String?>()
        var page = ctx.current
        while (true) {
            seen += page.links("item").map { it.title }
            if (page.links(rel).isEmpty()) break
            page = ctx.client.follow(rel, null)
        }
        assertEquals(counted, seen.size, "items: $seen")
        assertEquals(seen.size, seen.toSet().size, "an item was listed twice: $seen")
    }

    @Then("the resource links to {string} titled {string}")
    fun linksTitled(rel: String, titles: String) =
        assertEquals(titles.split(',').map { it.trim() }, ctx.current.links(rel).map { it.title })
}
