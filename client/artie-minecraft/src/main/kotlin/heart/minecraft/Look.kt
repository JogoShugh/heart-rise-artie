package heart.minecraft

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * What Minecraft draws, in Adventure's text components. They need no server, so what the player sees can be read
 * without one. A dialog with nothing to fill in is a written book: its text left-aligned and dark on the page, its
 * buttons links. A form is a dialog of inputs.
 */
internal object Look {
    /** How many lines a book's page holds. */
    private const val PAGE_LINES = 14

    /** How many characters fit across a page, a few under the most, so a line is never cut off. */
    private const val LINE_CHARS = 18

    /** Whether [dialog] opens as a book: it has nothing to fill in. */
    fun isBook(dialog: Dialog): Boolean = dialog.inputs.isEmpty()

    /**
     * [dialog] as a book's lines: its title, its text, a link for each button, then the resource's state, each part
     * a blank line apart. [click] says what clicking a button's link does.
     */
    fun lines(dialog: Dialog, click: (Button) -> ClickEvent<*>? = { null }): List<Component> {
        val facts = (dialog.facts + dialog.about?.facts.orEmpty()).map(::fact)
        val parts = listOf(dialog.body.map(Component::text), dialog.buttons.map { link(it, click(it)) }, facts)
        return listOf(Component.text(dialog.title, NamedTextColor.BLACK, TextDecoration.BOLD)) +
            parts.filter { it.isNotEmpty() }.flatMap { listOf(Component.empty()) + it }
    }

    /** [lines] as a book's pages: as many whole lines on a page as it holds, a long one counted as it wraps. */
    fun pages(lines: List<Component>): List<Component> {
        val pages = mutableListOf(mutableListOf<Component>())
        var used = 0
        lines.forEach { line ->
            val rows = maxOf(1, (plain(line).length + LINE_CHARS - 1) / LINE_CHARS)
            if (used + rows > PAGE_LINES) {
                pages += mutableListOf<Component>()
                used = 0
            }
            if (used > 0 || plain(line).isNotEmpty()) {
                pages.last() += line
                used += rows
            }
        }
        return pages.map { Component.join(JoinConfiguration.newlines(), it) }
    }

    /** [line]'s words, as the player reads them. */
    fun plain(line: Component): String = PlainTextComponentSerializer.plainText().serialize(line)

    /**
     * [dialog]'s text, then its titled values, each as one block of lines: Minecraft spaces blocks apart, and lines
     * of one block close together.
     */
    fun body(dialog: Dialog): List<Component> = listOf(dialog.body, dialog.facts.map { "${it.label}: ${it.value}" })
        .filter { it.isNotEmpty() }
        .map { lines -> Component.join(JoinConfiguration.newlines(), lines.map(Component::text)) }

    /** A dialog button's label, white, as Minecraft draws one. */
    fun label(button: Button): Component = Component.text(button.label, NamedTextColor.WHITE)

    /**
     * A button as a book's link: dark blue and underlined, as a page's links are, or grey when greyed; on hover,
     * what it does or why it is off.
     */
    private fun link(button: Button, click: ClickEvent<*>?): Component {
        val text = if (button.greyed) {
            Component.text(button.label, NamedTextColor.GRAY)
        } else {
            Component.text(button.label, NamedTextColor.DARK_BLUE, TextDecoration.UNDERLINED)
        }
        return text.hoverEvent(button.hover?.let { HoverEvent.showText(Component.text(it)) }).clickEvent(click)
    }

    /** A value on the page: its title dark grey, the value black. */
    private fun fact(fact: Fact): Component = Component.text()
        .append(Component.text("${fact.label}: ", NamedTextColor.DARK_GRAY))
        .append(Component.text(fact.value, NamedTextColor.BLACK))
        .build()
}
