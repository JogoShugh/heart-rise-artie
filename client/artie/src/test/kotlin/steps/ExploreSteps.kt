package steps

import heart.AffordanceForm
import heart.Exploration
import heart.ExploreSamples
import heart.Explorer
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.Manifest
import heart.Rung
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals

/**
 * A lamp service in memory, behind ARTIE's own client interface: its bookmark
 * offers a search, the search finds lamp 7, and the lamp offers what its
 * state allows. It counts on nothing about the explorer.
 */
class LampService : HeartRiseClient {
    private var on = false
    private var brightness = 50
    private var at = "/"

    /** Whether the bookmark also offers installing a new lamp. */
    var installs = false

    /** Whether any search was run. */
    var searched = false

    override var current: HeartRepresentation? = null
    override val appliedRung: Rung? = Rung.L0

    override fun discover(url: String) = Unit

    override fun enter(url: String): HeartRepresentation {
        at = if (url.endsWith("/lamps/7")) "/lamps/7" else "/"
        return show()
    }

    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        check(current?.affordance(rel) != null) { "$rel is not offered at $at" }
        when (rel) {
            "home:search-lamps" -> {
                at = "/lamps?name=${payload["name"]?.jsonPrimitive?.content}"
                searched = true
            }
            "home:install-lamp" -> {
                at = "/lamps/8"
                on = false
                brightness = payload.getValue("brightness").jsonPrimitive.int
            }
            "home:switch-on" -> on = true
            "home:switch-off" -> on = false
            "home:dim" -> brightness = payload.getValue("brightness").jsonPrimitive.int
        }
        return show()
    }

    override fun follow(rel: String, title: String?): HeartRepresentation {
        check(rel == "item" && title == "Lamp 7") { "no $rel titled $title" }
        at = "/lamps/7"
        return show()
    }

    override fun describe(rel: String): String? = null

    override fun explain(rel: String): String? = null

    override fun done() = Unit

    private fun show(): HeartRepresentation {
        val offered = when {
            at == "/" -> listOf("home:search-lamps") + if (installs) listOf("home:install-lamp") else emptyList()
            at.startsWith("/lamps?") -> emptyList()
            on -> listOf("home:switch-off", "home:dim")
            else -> listOf("home:switch-on")
        }
        val state = buildJsonObject {
            put("on", on)
            put("brightness", brightness)
        }
        val forms = offered.associateWith {
            AffordanceForm(it, "$at/actions/$it", false, "POST", "application/json", JsonObject(emptyMap()))
        }
        return HeartRepresentation(at, state, Manifest(forms), "\"$on-$brightness\"").also { current = it }
    }
}

class ExploreSteps {
    private lateinit var service: LampService
    private lateinit var samples: ExploreSamples
    private lateinit var exploration: Exploration

    @Given("a lamp service whose bookmark searches lamps by name, with lamp 7 off")
    fun lampService() {
        service = LampService()
    }

    @Given("the lamp service's bookmark also offers installing a lamp")
    fun installs() {
        service.installs = true
    }

    @Then("it ran no search")
    fun noSearch() = assertEquals(false, service.searched)

    @Given("explore samples searching {string} with acts:")
    fun samples(name: String, table: DataTable) = samples(name, ExploreSamples.DEFAULT_STEPS, table)

    @Given("explore samples searching {string}, at most {int} steps, with acts:")
    fun samples(name: String, steps: Int, table: DataTable) {
        val acts = table.asMaps().associateTo(LinkedHashMap()) { row ->
            val bodies = row.getValue("bodies").split(";").map { Json.parseToJsonElement(it.trim()).jsonObject }
            row.getValue("rel") to bodies
        }
        val search = ExploreSamples.Search("home:search-lamps", buildJsonObject { put("name", name) }, "Lamp 7")
        samples = ExploreSamples(search, acts, steps)
    }

    @When("ARTIE explores the lamp service")
    fun explore() {
        exploration = Explorer(service).explore("http://lamps.test", samples)
    }

    @Then("it took these acts")
    fun took(table: DataTable) = assertEquals(table.asMaps().map { it.getValue("act") }, exploration.acts)

    @Then("it took no acts")
    fun tookNone() = assertEquals(emptyList(), exploration.acts)

    @Then("it stopped because {string}")
    fun stopped(reason: String) = assertEquals(reason, exploration.stopped)

    @When("the explore samples are read from:")
    fun read(json: String) {
        samples = ExploreSamples.parse(json)
    }

    @Then("the samples search with {string} for the item {string}, at most {int} steps")
    fun searchWith(rel: String, item: String, steps: Int) =
        assertEquals(Triple(rel, item, steps), Triple(samples.search.rel, samples.search.item, samples.maxSteps))

    @Then("the samples have {int} body for {string} and {int} for {string}, in that order")
    fun bodies(first: Int, firstRel: String, second: Int, secondRel: String) =
        assertEquals(
            listOf(firstRel to first, secondRel to second),
            samples.acts.map { (rel, bodies) -> rel to bodies.size },
        )
}
