package steps

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals

/** script-watch.feature: a script that keeps watching the small GitHub, in process. */
class WatchSteps(private val gateway: GitHubGatewaySteps, private val scripts: ScriptSteps) {
    @Given("pull request {long} is a draft on the small GitHub")
    fun draft(number: Long) {
        gateway.github.pulls.first { it.number == number }.draft = true
    }

    @Given("pull request {long} is a draft on the small GitHub until it is next read")
    fun draftUntilRead(number: Long) {
        draft(number)
        gateway.github.afterNextRead[number] = { it.draft = false }
    }

    @Given("the small GitHub answers the {int}nd read of {string} with 500")
    fun failsRead(n: Int, path: String) {
        gateway.github.failRead[path] = n
    }

    @Then("the small GitHub was asked to merge these pull requests, in order")
    fun merged(table: DataTable) {
        val expected = table.asMaps().mapNotNull { it["number"]?.takeIf(String::isNotBlank)?.toLong() }
        val asked = gateway.github.requests.filter { it.method.name == "PUT" }
            .mapNotNull { MERGE.matchEntire(it.uri.path)?.groupValues?.get(1)?.toLong() }
        assertEquals(expected, asked)
    }

    @Then("the run noted")
    fun noted(table: DataTable) {
        val expected = table.asMaps().mapNotNull { it["note"]?.takeIf(String::isNotBlank) }
        assertEquals(expected, checkNotNull(scripts.lastRun).notes)
    }

    @Then("the run noted one note beginning {string}")
    fun notedOne(beginning: String) {
        val notes = checkNotNull(scripts.lastRun).notes
        assertEquals(1, notes.count { it.startsWith(beginning) }, notes.toString())
    }

    private companion object {
        val MERGE = Regex("""/repos/[^/]+/[^/]+/pulls/(\d+)/merge""")
    }
}
