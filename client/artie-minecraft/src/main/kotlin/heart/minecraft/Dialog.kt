package heart.minecraft

import kotlinx.serialization.json.JsonObject

/**
 * A dialog as Minecraft shows one (Java Edition 1.21.6 and later): a title, lines of text, titled values, the
 * inputs a player fills in, and buttons. It knows nothing of any domain: what it holds comes from the
 * representation.
 */
data class Dialog(
    val title: String,
    val body: List<String>,
    val inputs: List<Input> = emptyList(),
    val buttons: List<Button>,
    val facts: List<Fact> = emptyList(),
)

/** A value and its title, as a member of the state or a field filled in so far. */
data class Fact(val label: String, val value: String)

/** One input a player fills in; what the player entered comes back by [key]. */
sealed interface Input {
    val key: String
    val label: String
}

/** A line of text, up to [maxLength] characters. */
data class TextInput(override val key: String, override val label: String, val maxLength: Int) : Input

/** One of a closed set of [options], the first chosen to begin with. */
data class OptionInput(override val key: String, override val label: String, val options: List<Option>) : Input

/** One option: the [id] that comes back, and what the player is [shown]. */
data class Option(val id: String, val shown: String)

/**
 * One button: its label, what pressing it does, and what it says on [hover]. A [greyed] button stands for an act
 * not on offer: it is shown so the player sees the act and why, and pressing it only reads the resource again.
 */
data class Button(val label: String, val press: Press, val hover: String? = null, val greyed: Boolean = false)

/** What a button does. The fields [Press.Next] and [Press.Send] carry were filled in by the dialogs before. */
sealed interface Press {
    /** Opens the form of the act [rel], at its first step. */
    data class Open(val rel: String) : Press

    /** Asks for the fields of [rel]'s form at [step], whose choices may depend on those filled so far. */
    data class Next(val rel: String, val filled: JsonObject, val step: Int) : Press

    /** Sends the act [rel], titled [title], with what was filled in. */
    data class Send(val rel: String, val title: String, val filled: JsonObject) : Press

    /** Back to the resource, as held. */
    data object Back : Press

    /** The resource, read again. */
    data object Read : Press
}
