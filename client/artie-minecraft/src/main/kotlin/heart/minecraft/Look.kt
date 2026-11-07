package heart.minecraft

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor

/**
 * A dialog's text as Minecraft draws it, in Adventure's text components. They need no server, so what the player
 * sees can be read without one.
 */
internal object Look {
    /**
     * [dialog]'s text, then its titled values, each as one block of lines: Minecraft spaces blocks apart, and lines
     * of one block close together.
     */
    fun body(dialog: Dialog): List<Component> = listOf(dialog.body, dialog.facts.map { "${it.label}: ${it.value}" })
        .filter { it.isNotEmpty() }
        .map { lines -> Component.join(JoinConfiguration.newlines(), lines.map(Component::text)) }

    /** A button's label: white, as Minecraft draws one; dark grey when greyed, as plain as a button that is off. */
    fun label(button: Button): Component =
        Component.text(button.label, if (button.greyed) NamedTextColor.DARK_GRAY else NamedTextColor.WHITE)
}
