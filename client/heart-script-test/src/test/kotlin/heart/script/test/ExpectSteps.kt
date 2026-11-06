package heart.script.test

import heart.script.ScriptSteps
import io.cucumber.java.Before
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals

/** Steps for expect's features, on top of the DSL's own: the same service, scripts that import expect. */
class ExpectSteps(private val script: ScriptSteps) {
    @Before
    fun importExpect() {
        script.imports += "heart.script.test.*"
    }

    @When("this test script runs")
    fun runs(text: String) = script.runs(text)

    @Then("the test failed at step {int}: {}")
    fun failed(step: Int, reason: String) {
        val stop = checkNotNull(script.stopped) { "the script finished" }
        assertEquals(
            listOf("ExpectationNotMet", "$step", reason),
            listOf(stop::class.simpleName, "${stop.step}", stop.reason),
            stop.message,
        )
    }

    @Then("trace step {int} expected {string}")
    fun expected(step: Int, words: String) = assertEquals(words, script.run.trace[step - 1].expect)

    @Then("the script noted nothing")
    fun notedNothing() = assertEquals(emptyList(), script.run.notes)
}
