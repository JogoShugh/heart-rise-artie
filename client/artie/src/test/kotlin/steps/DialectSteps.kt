package steps

import heart.ArtieClient
import heart.Dialect
import heart.Rung
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Steps for SPEC §6.1 / §6.10.6: the dialect ARTIE speaks, and L5 agreement. */
class DialectSteps(private val ctx: ClientContext) {

    @Given("the client works in dialect {word}")
    fun worksIn(name: String) {
        ctx.dialect = Dialect.named(name)
    }

    @When("the client is set to rung {word}")
    fun setTo(rung: String) {
        ctx.lastError = runCatching { ArtieClient(ctx.transport, Rung.valueOf(rung), ctx.dialect) }.exceptionOrNull()
    }

    /** The legality decision both dialects must agree on: which acts, at which targets. */
    @Then("on bed {int} a hal-schema-forms client and a hal-forms client see the same act targets")
    fun sameActs(bed: Int) {
        val url = ctx.url("/beds/$bed")
        val acts = Dialect.entries.associateWith { dialect ->
            ctx.newClient(dialect).enter(url).affordances.values.map { "${it.method} ${it.target}" }.toSet()
        }
        assertTrue(acts.values.all { it.isNotEmpty() }, "no acts: $acts")
        assertEquals(acts.getValue(Dialect.HAL_SCHEMA_FORMS), acts.getValue(Dialect.HAL_FORMS))
    }
}
