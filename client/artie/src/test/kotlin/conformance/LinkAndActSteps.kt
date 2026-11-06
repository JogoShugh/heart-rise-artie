package conformance

import com.artie.heart.sims.Simulator
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Steps for links.feature and acts.feature: what ARTIE follows, and what an act carries and leaves held. */
class LinkAndActSteps(private val world: World) {
    private val actor get() = world.actor

    @When("it enters the pull requests of {string}")
    fun enterPulls(repository: String) {
        world.rep = actor.client.enter("${origin("GitHub")}/repos/$repository/pulls")
    }

    @When("it enters the pull request {int} of {string}")
    fun enterPull(number: Int, repository: String) {
        world.rep = actor.client.enter("${origin("GitHub")}/repos/$repository/pulls/$number")
    }

    @When("it enters the issue {int} of {string}")
    fun enterIssue(number: Int, repository: String) {
        world.rep = actor.client.enter("${origin("GitHub")}/repos/$repository/issues/$number")
    }

    @io.cucumber.java.en.Given(
        "a teammate will close issue {int} of {string} on GitHub itself right after a client reads it",
    )
    fun teammateCloses(number: Int, repository: String) {
        check(repository == "acme/web") { "the GitHub service holds acme/web" }
        world.sims.github.meddle(number.toLong(), mapOf("state" to "closed", "state_reason" to "not_planned"))
    }

    @When("it tries to follow {string} titled {string}")
    fun tryFollowTitled(rel: String, title: String) = world.trying { actor.client.follow(rel, title) }

    @When("it tries to follow {string} with no title")
    fun tryFollow(rel: String) = world.trying { actor.client.follow(rel, null) }

    @Then("it holds a representation")
    fun holds() {
        assertNull(world.error, "it failed: ${world.error?.message}")
        assertNotNull(world.rep)
    }

    @Given("the {word} service's job also holds {}")
    fun alsoHolds(service: String, members: String) {
        faults(service).body.members = faults(service).body.json(members)
    }

    @Given("the {word} service's job links only {}")
    fun linksOnly(service: String, links: String) {
        faults(service).body.linksOnly = faults(service).body.json(links)
    }

    @Given("the {word} service's job embeds only {}")
    fun embedsOnly(service: String, embedded: String) {
        faults(service).body.embedsOnly = faults(service).body.json(embedded)
    }

    @Then("it holds exactly these links, with no titles")
    fun exactlyLinks(table: DataTable) {
        val links = world.held.linksByRel.flatMap { (rel, links) -> links.map { rel to it } }
        assertEquals(table.asMaps(), links.map { (rel, link) -> mapOf("relation" to rel, "href" to pathOf(link.href)) })
        assertTrue(links.all { (_, link) -> link.title == null }, "a link has a title: $links")
    }

    @Then("it holds embedded resources at exactly {string}")
    fun embeddedAt(path: String) =
        assertEquals(listOf(path), world.held.embeddedByRel.values.flatten().map { pathOf(it.href) })

    @Then("it sent {word} with the body {string} and the content type {string}")
    fun sent(method: String, body: String, type: String) {
        val sent = world.sims.log.last { it.method == method }
        assertEquals(body to type, sent.body to sent.headers["Content-Type"].orEmpty())
    }

    @Then("it carried the validator it read")
    fun carriedValidator() =
        assertEquals(world.validatorBefore, world.sims.log.last { it.method != "GET" }.headers["If-Match"])

    @Given("the {word} service sends {string} to another origin")
    fun toAnotherOrigin(service: String, form: String) = reshape(
        service, form, """{"_links": {"target": {"href": "http://${Simulator.ELSEWHERE}/comments"}}}""",
    )

    @Given("the {word} service's form {string} names no method or content type")
    fun noMethod(service: String, form: String) = reshape(service, form, """{"method": null, "contentType": null}""")

    @Given("the {word} service's form {string} takes {string}")
    fun takes(service: String, form: String, type: String) = reshape(service, form, """{"contentType": "$type"}""")

    @Given("the {word} service answers acts with {word} body, from {}")
    fun answersActs(service: String, body: String, from: String) {
        faults(service).actsWithNoBody = body == "no"
        faults(service).actsFrom = from.takeIf { it != "nowhere" }?.removeSurrounding("\"")
    }

    @Given("the {word} service links its affordance set without an integrity value")
    fun noIntegrity(service: String) {
        faults(service).body.noIntegrity = true
    }

    @Given("the {word} service links no affordance set")
    fun noSet(service: String) {
        faults(service).body.noSet = true
    }

    @Then("the service was sent the credential")
    fun credentialSent() =
        assertNotNull(world.sims.log.first { it.service != Simulator.ELSEWHERE }.headers["Authorization"])

    @Then("the other origin was sent none")
    fun noneElsewhere() {
        val other = world.sims.log.filter { it.service == Simulator.ELSEWHERE }
        assertEquals(1, other.size)
        assertNull(other.single().headers["Authorization"])
    }

    @Then("the client holds {string}")
    fun clientHolds(path: String) = assertEquals(path, pathOf(world.held.url))

    @Then("the client holds {string}, gone")
    fun clientHoldsGone(path: String) {
        val held = checkNotNull(actor.client.current) { "nothing held" }
        assertEquals(path to true, pathOf(held.url) to held.gone)
    }

    private fun faults(service: String) = world.sims.faults("${service.lowercase()}.test")

    private fun reshape(service: String, form: String, patch: String) {
        faults(service).body.reshaped[form] = faults(service).body.json(patch)
    }

    private fun pathOf(href: String) = URI(href).rawPath
}
