package steps

import heart.ModelKey
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals

/** model-key.feature: where ARTIE finds its model key, in a home folder of the scenario's own. */
class ModelKeySteps {
    private val home = Files.createTempDirectory("artie-home")
    private val env = mutableMapOf<String, String>()
    private var found: ModelKey? = null

    @Given("the environment's ANTHROPIC_API_KEY is {string}")
    fun environment(key: String) {
        if (key.isNotEmpty()) env["ANTHROPIC_API_KEY"] = key
    }

    @Given("the credentials file's key for {string} is {string}")
    fun file(origin: String, key: String) {
        if (key.isEmpty()) return
        val folder = Files.createDirectories(home.resolve(".artie"))
        val file = folder.resolve("credentials")
        Files.writeString(file, "[$origin]\nbearer = $key\n")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
    }

    @When("ARTIE looks for its model key")
    fun looks() {
        found = ModelKey.standard(env, home)
    }

    @Then("it finds {string}, from {}")
    fun finds(key: String, where: String) = assertEquals(
        key.ifEmpty { null } to where,
        found?.value to (found?.source ?: "nowhere"),
    )

    private var reasoned: heart.script.Reasoned? = null

    @When("a script host's ARTIE, allowed the claude reasoner, is asked to reason")
    fun reasonWithoutKey() {
        reasoned = heart.ArtieNavigatorProvider.standard(env, home, reasoner = "claude").reason("tidy up")
    }

    @Then("the reason step was refused as {string}, by {string}: {string}")
    fun reasonRefused(kind: String, by: String, detail: String) {
        val refused = reasoned?.answer as? heart.script.Answer.Refused ?: error("not refused: ${reasoned?.answer}")
        assertEquals(listOf(kind, by, detail), listOf(refused.kind.words, refused.by.words, refused.detail))
    }

    @Then("what it says of the key is {string}")
    fun says(words: String) = assertEquals(words, found.toString())
}
