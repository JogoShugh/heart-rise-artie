package conformance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals

/** Steps for dialects.feature: one resource read in both dialects, by two clients. */
class DialectSteps(private val world: World) {
    private val me get() = world.actor.client
    private val other get() = checkNotNull(world.other) { "no other client" }.client

    @When("each client reads bed {int} in its own dialect")
    fun readBoth(bed: Int) {
        me.enter(world.bookmark)
        me.act("garden:get-bed", buildJsonObject { put("id", bed) })
        other.enter(world.bookmark)
        other.act("getBed", buildJsonObject { put("id", bed) })
        other.follow("item", "bed-$bed")
    }

    @Then("both hold the same state")
    fun sameState() = assertEquals(checkNotNull(me.current).state, checkNotNull(other.current).state)

    @Then("both offer the act {string}, by the same method")
    fun sameAct(act: String) {
        listOf(me, other).forEach { client ->
            val offered = checkNotNull(client.current).affordances.values
                .map { "${it.method} ${it.target.substringAfterLast('/')}" }
            assertEquals(listOf("POST $act"), offered)
        }
    }
}
