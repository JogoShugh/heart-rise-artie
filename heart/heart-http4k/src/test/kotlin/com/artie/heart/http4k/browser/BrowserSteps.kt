package com.artie.heart.http4k.browser

import com.artie.heart.binding.LampServer
import com.artie.heart.binding.LampStore
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.server.Http4kServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * A person at a HEART page in headless Chrome. Every step acts as they would, by the page's own buttons and fields;
 * what the page shows is read back from it. What heart-forms.js ran goes to build/browser-coverage, one file a
 * scenario, in the format NODE_V8_COVERAGE writes, its address the source file's (c8 reports it for SonarQube).
 */
class BrowserSteps(private val store: LampStore, private val server: Http4kServer) {
    private lateinit var tab: Chrome.Tab
    private var rendered: RenderedPage? = null
    private var asked: String? = null
    private val base get() = "http://localhost:${server.port()}"

    @Before
    fun open() {
        assumeTrue(Chrome.available, "no Chrome on this machine")
        tab = Chrome.tab()
        tab.send("Network.enable")
        tab.send(
            "Network.setExtraHTTPHeaders",
            mapOf("headers" to mapOf("Authorization" to "Bearer ${LampServer.LAMP_TOKEN}"))
        )
        tab.send("Profiler.enable")
        tab.send("Profiler.startPreciseCoverage", mapOf("callCount" to true, "detailed" to true))
    }

    @After
    fun close() {
        if (!::tab.isInitialized) return
        keepCoverage()
        tab.close()
        rendered?.close()
    }

    @Given("lamp {long} is {string} at brightness {int}, served by heart-http4k")
    fun lamp(id: Long, status: String, brightness: Int) {
        store.lamps[id] = Lamp(id, status, brightness, version = 3)
    }

    @Given("lamp {long} is open in a browser")
    fun openLamp(id: Long) = go("$base/lamps/$id")

    @Given("the bookmark is open in a browser")
    fun openBookmark() = go("$base/")

    @Given("the lamps named {string} are open in a browser")
    fun openSearch(name: String) {
        openBookmark()
        send("search-lamps", mapOf("name" to name))
        tab.waitFor("the lamps listed", "document.querySelector('tr[data-item]')")
    }

    @When("lamp {long} is opened in a browser at its act {string}")
    fun openAtAct(id: Long, relation: String) {
        openLamp(id)
        val dialog = tab.eval("${button(relation)}.getAttribute('data-dialog')").asText()
        go("about:blank")
        go("$base/lamps/$id#$dialog")
    }

    @Given("a resource whose act {string} has the schema {}, open in a browser")
    fun openRendered(relation: String, schema: String) {
        rendered = RenderedPage(relation, schema).also { go(it.address) }
    }

    @Given("another client dims lamp {long} to {int}")
    fun otherDims(id: Long, brightness: Int) {
        store.lamps.computeIfPresent(id) { _, lamp -> lamp.copy(brightness = brightness, version = lamp.version + 1) }
    }

    @Given("another client switches lamp {long} off")
    fun otherSwitchesOff(id: Long) {
        store.lamps.computeIfPresent(id) { _, lamp -> lamp.copy(status = "OFF", version = lamp.version + 1) }
    }

    @When("the person sends the act {string} with")
    fun sendWith(relation: String, fields: DataTable) =
        send(relation, fields.asMaps().associate { it.getValue("field") to it["value"].orEmpty() })

    @When("the person opens the act {string}, adds an entry to {string}, and fills the entries {string} and {string}")
    fun fillList(relation: String, field: String, first: String, second: String) {
        openDialog(relation)
        val list = "${form(relation)}.querySelector('[name=${json(field)}]').closest('.field')"
        tab.eval("$list.querySelector('[data-add]').click()")
        tab.eval(
            "(() => { const inputs = $list.querySelectorAll('[data-list] input'); " +
                "inputs[0].value = ${json(first)}; inputs[1].value = ${json(second)}; })()",
        )
    }

    @When("sends the act {string}")
    fun submit(relation: String) {
        tab.eval("${form(relation)}.querySelector('button[type=submit]').click()")
    }

    @When("the person sends the search {string} with")
    fun search(relation: String, fields: DataTable) {
        // The address is asked for, and answered with no content: the page stays, and what it ran with it.
        asked = tab.heldNavigation {
            send(relation, fields.asMaps().associate { it.getValue("field") to it["value"].orEmpty() })
        }
    }

    @When("the person presses the greyed act {string}")
    fun pressGreyed(relation: String) {
        tab.eval("${greyed(relation)}.click()")
    }

    @When("the person asks for the acts of the row {string}")
    fun askRow(title: String) {
        tab.eval("${row(title)}.querySelector('[data-acts]').click()")
    }

    @Then("the resource shows {string} as {string}")
    fun shows(member: String, value: String) = tab.waitFor(
        "$member shown as $value",
        "document.querySelector('dl.state dt[title=${json(member)}] + dd')?.textContent.trim() === ${json(value)}",
    )

    @Then("the page announces {string}")
    fun announces(text: String) =
        tab.waitFor("announced: $text", "document.getElementById('announce').textContent === ${json(text)}")

    @Then("the page announces that {string} is no longer offered, with its reason")
    fun announcesGone(title: String) {
        val reason = "document.getElementById(${greyed(title, byTitle = true)}?.getAttribute('aria-describedby'))"
        tab.waitFor(
            "announced: $title is no longer offered",
            "document.getElementById('announce').textContent === 'This changed since you opened it. ' + " +
                "${json(title)} + ' is no longer offered: ' + $reason?.textContent",
        )
    }

    @Then("the dialog of {string} is still open")
    fun stillOpen(relation: String) = isOpen(relation)

    @Then("the dialog of {string} is open")
    fun isOpen(
        relation: String
    ) = tab.waitFor("the dialog of $relation open", "${form(relation)}?.closest('dialog').open")

    @Then("the dialog of {string} is open again, saying {string}")
    fun openAgain(relation: String, text: String) {
        isOpen(relation)
        val problem = "${form(relation)}.querySelector('.problem')"
        tab.waitFor(
            "the dialog says: $text",
            "$problem.textContent === ${json(text)} && $problem.classList.contains('stale')"
        )
    }

    @Then("the dialog of {string} says {string}")
    fun dialogSays(relation: String, text: String) =
        tab.waitFor(
            "the dialog says: $text",
            "${form(relation)}.querySelector('.problem').textContent === ${json(text)}"
        )

    @Then("the field {string} says {string}")
    fun fieldSays(field: String, text: String) = tab.waitFor(
        "the field $field says: $text",
        "[...document.querySelectorAll('[name=${json(field)}]')].some(c => c.closest('.field')" +
            ".querySelector('.error')?.textContent === ${json(text)})",
    )

    @Then("the field {string} still holds {string}")
    fun fieldHolds(field: String, value: String) = tab.waitFor(
        "the field $field holds $value",
        "document.querySelector('dialog[open] [name=${json(field)}]')?.value === ${json(value)}",
    )

    @Then("the reason beside the greyed {string} is shown")
    fun reasonShown(relation: String) = tab.waitFor("the reason shown", "!${why(relation)}.hidden")

    @Then("the reason beside the greyed {string} is hidden")
    fun reasonHidden(relation: String) = tab.waitFor("the reason hidden", "${why(relation)}.hidden")

    @Then("the browser asks for an address whose query has {string} as {string}")
    fun askedAddress(name: String, value: String) {
        val query = URI(checkNotNull(asked) { "no address asked for" }).rawQuery.orEmpty().split('&')
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals(value, query[name], "$asked")
    }

    @Then("the row's acts read")
    fun rowActs(acts: DataTable) {
        tab.waitFor("the row's acts read", "document.querySelector('.row-menu[data-read]')")
        val read = tab.eval(
            "[...document.querySelector('.row-menu[data-read]').children].map(e => e.textContent)",
        ).map { it.asText() }
        assertEquals(acts.asMaps().map { it.getValue("act").trim() }, read)
    }

    @Then("nothing was sent")
    fun nothingSent() = assertEquals(0, checkNotNull(rendered).sent.get())

    private fun send(relation: String, fields: Map<String, String>) {
        openDialog(relation)
        fields.forEach { (name, value) ->
            tab.eval("${form(relation)}.querySelector('[name=${json(name)}]').value = ${json(value)}")
        }
        submit(relation)
    }

    private fun openDialog(relation: String) {
        tab.eval("${button(relation)}.click()")
        isOpen(relation)
    }

    /** Goes to [address] as a person would, once the page and its scripts are in; the last page's coverage kept. */
    private fun go(address: String) {
        keepCoverage()
        tab.send("Page.navigate", mapOf("url" to address))
        if (address != "about:blank") {
            tab.waitFor(
                "$address loaded",
                "document.readyState === 'complete' && window.htmx && location.href !== 'about:blank'"
            )
        }
    }

    private fun keepCoverage() {
        val scripts = tab.send("Profiler.takePreciseCoverage")["result"]
            .filter { it["url"].asText().contains(FORMS) }
        if (scripts.isEmpty()) return
        val result = scripts.map { script ->
            mapOf(
                "scriptId" to script["scriptId"].asText(),
                "url" to SOURCE.toUri().toString(),
                "functions" to script["functions"]
            )
        }
        Files.createDirectories(COVERAGE)
        MAPPER.writeValue(COVERAGE.resolve("coverage-${UUID.randomUUID()}.json").toFile(), mapOf("result" to result))
    }

    private fun button(relation: String) = "document.querySelector('button.act${rel(relation)}:not([aria-disabled])')"
    private fun form(relation: String) = "document.querySelector('form${rel(relation)}')"

    private fun greyed(key: String, byTitle: Boolean = false) =
        if (byTitle) {
            "[...document.querySelectorAll('button.act[aria-disabled]')].find(b => b.textContent === ${json(key)})"
        } else {
            "document.querySelector('button.act${rel(key)}[aria-disabled]')"
        }

    private fun why(relation: String) = "document.getElementById(${greyed(relation)}.getAttribute('aria-describedby'))"

    private fun row(title: String) =
        "[...document.querySelectorAll('tr[data-item]')].find(r => r.cells[0].textContent.trim() === ${json(title)})"

    /** An act by its relation, with or without the profile's prefix. */
    private fun rel(relation: String) = "[data-rel\$=${json(relation.substringAfter(':'))}]"

    private fun json(text: String) = MAPPER.writeValueAsString(text).replace("'", "\\'")

    private companion object {
        val MAPPER = ObjectMapper()
        const val FORMS = "heart-forms."
        val SOURCE: Path = Path.of(
            "../heart-core/src/main/resources/heart/assets/heart-forms.js"
        ).toAbsolutePath().normalize()
        val COVERAGE: Path = Path.of("build/browser-coverage")
    }
}
