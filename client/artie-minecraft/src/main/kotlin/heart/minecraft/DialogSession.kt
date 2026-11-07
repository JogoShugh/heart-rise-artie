package heart.minecraft

import heart.AccessRefused
import heart.ActRefused
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.ReadRefused
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One player's way through HEART resources, a dialog at a time. ARTIE reads and acts; each answer is the next
 * dialog. A resource is its state as lines of text, a line for each act that is absent and why, and a button for
 * each act on offer. An act's form is a dialog of inputs, or a few, one step at a time (see [FormSteps]). An act is
 * sent as ARTIE sends one, with If-Match; a refusal is a notice in the server's words.
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
        body = rep.state.mapNotNull { (name, value) -> line(name, value) } + absent(rep),
        buttons = rep.affordances.map { (rel, form) -> Button(form.title ?: rel, Press.Open(rel)) },
    )

    /** The resource read again: after a refusal, the state is what the next dialog shows. */
    private fun read(url: String): Dialog = try {
        resource(client.enter(url))
    } catch (refused: ReadRefused) {
        notice("Not read", refused.message.orEmpty())
    }

    /** Step [step] of [rel]'s form, with [filled] what the steps before it filled in. */
    private fun form(rel: String, filled: JsonObject, step: Int): Dialog {
        val form = checkNotNull(held.affordance(rel)) { "$rel is not offered now" }
        val steps = steps(rel)
        val title = form.title ?: rel
        val so = filled.entries.joinToString(", ") { (name, value) -> "$name ${text(value)}" }
        val progress = "Step ${step + 1} of ${steps.steps.size}" + (if (so.isEmpty()) "" else ": $so")
        val next = if (step == steps.steps.lastIndex) {
            Button(title, Press.Send(rel, title, filled))
        } else {
            Button("Next", Press.Next(rel, filled, step + 1))
        }
        return Dialog(
            title = title,
            body = listOfNotNull(form.description, progress.takeIf { steps.steps.size > 1 }),
            inputs = steps.steps[step].map { steps.input(it, filled) },
            buttons = listOf(next, Button("Back", Press.Back)),
        )
    }

    private fun send(press: Press.Send, filled: JsonObject): Dialog = try {
        resource(client.act(press.rel, filled))
    } catch (refused: ActRefused) {
        notice("${press.title} was refused", refused.message.orEmpty())
    } catch (refused: AccessRefused) {
        notice("${press.title} was refused", refused.message.orEmpty())
    }

    /** What the server said, and a button to read the resource again. */
    private fun notice(title: String, said: String) =
        Dialog(title, listOf(said), buttons = listOf(Button("OK", Press.Read)))

    /** One line for each act that is absent, and why, as the representation's `hr:unavailable` says. */
    private fun absent(rep: HeartRepresentation): List<String> {
        val said = rep.manifest.unavailable?.let { client.listReader(it.href) } as? JsonObject
        return ((said?.get("unavailable") as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }
            .map { "${text(it["title"] ?: it["rel"])}: ${text(it["reason"])}" }
    }

    private companion object {
        /** A resource's title: its self link's, else its state's title or name, else its address. */
        fun titleOf(rep: HeartRepresentation): String = rep.links("self").firstOrNull()?.title
            ?: listOf("title", "name").firstNotNullOfOrNull { (rep.state[it] as? JsonPrimitive)?.takeUnless(::isNull) }
                ?.content
            ?: rep.url

        /** One member of the state as a line: a value as it reads, an object by its names; none when null. */
        fun line(name: String, value: JsonElement): String? = when (value) {
            is JsonNull -> null
            is JsonPrimitive -> "$name: ${value.content}"
            is JsonObject -> "$name: ${value.keys.joinToString(", ")}"
            is JsonArray -> "$name: ${value.size} items"
        }

        fun text(value: JsonElement?): String = (value as? JsonPrimitive)?.content ?: "$value"

        fun isNull(value: JsonPrimitive) = value is JsonNull
    }
}
