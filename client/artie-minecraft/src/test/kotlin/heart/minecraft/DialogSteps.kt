package heart.minecraft

import com.artie.heart.sims.Simulator
import heart.ArtieClient
import heart.AuthTransport
import heart.CachingTransport
import heart.Credential
import heart.CredentialSources
import heart.IntegrityCheck
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A player in front of the dialogs: what each shows, and the buttons pressed, as Minecraft would send them. */
class DialogSteps(private val play: Play) {
    private val session by lazy {
        val credentials = CredentialSources.of(mapOf(Play.DEPLOY to Credential.Bearer(Play.TOKEN)))
        DialogSession(ArtieClient(CachingTransport(IntegrityCheck(AuthTransport(credentials, play.wire)))))
    }
    private lateinit var shown: Dialog

    @When("a player opens the app {string}")
    fun opens(app: String) {
        shown = session.open("${Play.DEPLOY}/apps/$app")
    }

    @When("the player presses {string}")
    fun presses(label: String) = press(label, emptyMap())

    @When("the player fills in, and presses {string}")
    fun fillsIn(label: String, table: DataTable) = press(label, table.asMap())

    /**
     * As Minecraft sends a press: every input's value, each as the player left it. An option begins on the first
     * choice, and a player can choose only one shown.
     */
    private fun press(label: String, entered: Map<String, String>) {
        val button = shown.buttons.firstOrNull { it.label == label } ?: error("no button $label in $shown")
        entered.keys.forEach { key -> assertTrue(shown.inputs.any { it.key == key }, "no input $key in $shown") }
        val values = shown.inputs.associate { input ->
            val value = entered[input.key] ?: (input as? OptionInput)?.options?.first()?.id.orEmpty()
            if (input is OptionInput) assertTrue(input.options.any { it.id == value }, "$value isn't shown")
            input.key to value
        }
        shown = session.press(button, values)
    }

    @Then("the dialog is titled {string}")
    fun titled(title: String) = assertEquals(title, shown.title, "$shown")

    @Then("its body reads")
    fun body(table: DataTable) = assertEquals(table.asList(), shown.body)

    @Then("it shows")
    fun shows(table: DataTable) = assertEquals(table.asLists(), shown.facts.map { listOf(it.label, it.value) })

    @Then("its buttons are")
    fun buttons(table: DataTable) = assertEquals(table.asList(), shown.buttons.map { it.label })

    @Then("its buttons, offered or greyed, are")
    fun buttonsShown(table: DataTable) = assertEquals(
        table.asMaps().map { row -> listOf(row["button"], row["shown"], row["on hover"].orEmpty()) },
        shown.buttons.map { listOf(it.label, if (it.greyed) "greyed" else "offered", it.hover.orEmpty()) },
    )

    @Then("its inputs are")
    fun inputs(table: DataTable) = assertEquals(
        table.asMaps(),
        shown.inputs.map {
            mapOf("input" to it.key, "label" to it.label, "kind" to if (it is OptionInput) "single option" else "text")
        },
    )

    @Then("the choices for {string} are")
    fun choices(key: String, table: DataTable) {
        val input = shown.inputs.firstOrNull { it.key == key } as? OptionInput ?: error("no option $key in $shown")
        assertEquals(table.asMaps(), input.options.map { mapOf("choice" to it.id, "shown as" to it.shown) })
    }

    @Then("the deploy service received no acts")
    fun noActs() = assertEquals(emptyList(), acts())

    @Then("the deploy service's acts, each sent with If-Match, were")
    fun actsWere(table: DataTable) {
        val acts = acts()
        assertEquals(
            table.asMaps().map { row -> listOf(row["method"], row["path"], row["body"].orEmpty()) },
            acts.map { listOf(it.method, it.url, it.body) },
        )
        acts.forEach { act -> assertTrue(act.headers.keys.any { it.equals("If-Match", ignoreCase = true) }, "$act") }
    }

    private fun acts() = play.sims.log.filter { it.service == Simulator.DEPLOY && it.method !in SAFE }

    private companion object {
        val SAFE = setOf("GET", "HEAD")
    }
}
