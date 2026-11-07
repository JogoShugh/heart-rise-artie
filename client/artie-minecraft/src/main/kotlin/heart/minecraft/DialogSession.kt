package heart.minecraft

import heart.AccessRefused
import heart.ActRefused
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.ReadRefused
import heart.StaleAct
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One player's way through HEART resources, a dialog at a time. ARTIE reads and acts; each answer is the next
 * dialog. A resource is its state as titled values, a button for each act on offer, saying what it does, and a
 * greyed button for each act that is absent, saying why. An act's form is a dialog of inputs, or a few, one step at
 * a time (see [FormSteps]). An act is sent as ARTIE sends one, with If-Match. A refusal because the resource changed
 * is said in plain words; any other is a notice in the server's words.
 */
class DialogSession(private val client: HeartRiseClient) {
    private val held get() = checkNotNull(client.current) { "no resource is open" }

    /** The resource at [url], as a dialog. */
    fun open(url: String): Dialog = resource(client.enter(url))

    /** What pressing [button] leads to, with what the player entered in the dialog's inputs as [values], by key. */
    fun press(button: Button, values: Map<String, String>): Dialog = when (val press = button.press) {
        is Press.Open -> form(press.rel, JsonObject(emptyMap()), 0)
        is Press.Next -> form(press.rel, steps(press.rel).filled(press.filled, values), press.step)
        is Press.Send -> send(press, steps(press.rel).filled(press.filled, values))
        Press.Back -> resource(held)
        Press.Read -> read(held.url)
    }

    private fun steps(rel: String): FormSteps {
        val form = checkNotNull(held.affordance(rel)) { "$rel is not offered now" }
        return FormSteps(form, client.choices(rel))
    }

    private fun resource(rep: HeartRepresentation) = Dialog(
        title = titleOf(rep),
        body = emptyList(),
        facts = rep.state.mapNotNull { (name, value) -> fact(name, value) },
        buttons = rep.affordances.map { (rel, form) -> Button(form.title ?: rel, Press.Open(rel), form.description) } +
            absent(rep),
    )

    /** The resource read again: after a refusal, the state is what the next dialog shows. */
    private fun read(url: String): Dialog = try {
        resource(client.enter(url))
    } catch (refused: ReadRefused) {
        notice("Not read", refused.message.orEmpty())
    }

    /** Step [step] of [rel]'s form, under what the steps before it filled in, [filled]. */
    private fun form(rel: String, filled: JsonObject, step: Int): Dialog {
        val form = checkNotNull(held.affordance(rel)) { "$rel is not offered now" }
        val steps = steps(rel)
        val title = form.title ?: rel
        val next = if (step == steps.steps.lastIndex) {
            Button(title, Press.Send(rel, title, filled))
        } else {
            Button("Next", Press.Next(rel, filled, step + 1))
        }
        val progress = "Step ${step + 1} of ${steps.steps.size}".takeIf { steps.steps.size > 1 }
        return Dialog(
            title = title,
            body = listOfNotNull(form.description, progress),
            facts = filled.entries.map { (name, value) -> Fact(steps.label(name), text(value)) },
            inputs = steps.steps[step].map { steps.input(it, filled) },
            buttons = listOf(next, Button("Back", Press.Back)),
        )
    }

    /**
     * [press]'s act, sent. A resource that changed since the player looked refuses it (412); ARTIE has read it
     * again, and the player is told so plainly, with the refusal itself on hover.
     */
    private fun send(press: Press.Send, filled: JsonObject): Dialog = try {
        resource(client.act(press.rel, filled))
    } catch (stale: StaleAct) {
        val name = titleOf(held)
        Dialog(
            "$name changed since you looked",
            listOf("${press.title} was not done: $name changed after this dialog opened."),
            buttons = listOf(Button("See it now", Press.Read, stale.message)),
        )
    } catch (refused: ActRefused) {
        notice("${press.title} was refused", refused.message.orEmpty())
    } catch (refused: AccessRefused) {
        notice("${press.title} was refused", refused.message.orEmpty())
    }

    /** What the server said, and a button to read the resource again. */
    private fun notice(title: String, said: String) =
        Dialog(title, listOf(said), buttons = listOf(Button("OK", Press.Read)))

    /**
     * A greyed button for each act that is absent, saying why, as the representation's `hr:unavailable` says.
     * Pressing one reads the resource again: the act may be on offer by then.
     */
    private fun absent(rep: HeartRepresentation): List<Button> {
        val said = rep.manifest.unavailable?.let { client.listReader(it.href) } as? JsonObject
        return ((said?.get("unavailable") as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }
            .map { Button(text(it["title"] ?: it["rel"]), Press.Read, text(it["reason"]), greyed = true) }
    }

    private companion object {
        /** A resource's title: its self link's, else its state's title or name, else its address. */
        fun titleOf(rep: HeartRepresentation): String = rep.links("self").firstOrNull()?.title
            ?: listOf("title", "name").firstNotNullOfOrNull { (rep.state[it] as? JsonPrimitive)?.takeUnless(::isNull) }
                ?.content
            ?: rep.url

        /** One member of the state, titled: a value as it reads, an object by its names; none when null. */
        fun fact(name: String, value: JsonElement): Fact? = when (value) {
            is JsonNull -> null
            is JsonPrimitive -> value.content
            is JsonObject -> value.keys.joinToString(", ")
            is JsonArray -> "${value.size} items"
        }?.let { Fact(FieldTitle.of(name), it) }

        fun text(value: JsonElement?): String = (value as? JsonPrimitive)?.content ?: "$value"

        fun isNull(value: JsonPrimitive) = value is JsonNull
    }
}
