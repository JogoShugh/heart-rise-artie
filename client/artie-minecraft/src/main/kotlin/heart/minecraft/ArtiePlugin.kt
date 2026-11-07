package heart.minecraft

import heart.ArtieClient
import heart.AuthTransport
import heart.CachingTransport
import heart.EnvironmentCredentials
import heart.HttpTransport
import heart.IntegrityCheck
import heart.UserAgentTransport
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.dialog.Dialog as PaperDialog

/**
 * ARTIE in Minecraft, as a Paper server plugin. `/artie [address]` opens a HEART resource as a dialog, the one in
 * config.yml when no address is given. Each player has a session of their own: their own ARTIE, holding the
 * resource they are on. A press goes back to the server with what the player entered; ARTIE reads and acts off the
 * main thread, and the answer comes back as the next dialog. Credentials come from the environment, as ARTIE's
 * always do: ARTIE_TOKEN_LOCALHOST_8402 for http://localhost:8402.
 */
class ArtiePlugin : JavaPlugin() {
    override fun onEnable() {
        saveDefaultConfig()
        registerCommand("artie", "Opens a HEART resource as a dialog: /artie [address]", Open())
    }

    private inner class Open : BasicCommand {
        override fun execute(source: CommandSourceStack, args: Array<String>) {
            val player = source.executor as? Player
            if (player == null) {
                source.sender.sendMessage("Only a player can open a dialog.")
                return
            }
            val address = args.firstOrNull() ?: config.getString("start").orEmpty()
            val session = DialogSession(ArtieClient(transport()))
            later(player, session) { session.open(address) }
        }
    }

    /** ARTIE's wire, as the artie command builds it: credentials by origin, integrity checked, the shared kept. */
    private fun transport(): CachingTransport {
        val wire = UserAgentTransport(next = HttpTransport())
        return CachingTransport(IntegrityCheck(AuthTransport(EnvironmentCredentials(System.getenv()), wire)))
    }

    /**
     * Runs [work], ARTIE's reads and acts, off the main thread, and shows [player] the dialog it made back on it. A
     * failure, of either, is a notice.
     */
    private fun later(player: Player, session: DialogSession, work: () -> Dialog) {
        server.scheduler.runTaskAsynchronously(this, Runnable {
            val dialog = runCatching(work).getOrElse(::failure)
            server.scheduler.runTask(this, Runnable {
                val shown = runCatching { render(player, session, dialog) }
                    .getOrElse { render(player, session, failure(it)) }
                player.showDialog(shown)
            })
        })
    }

    private fun failure(failed: Throwable): Dialog {
        logger.warning("ARTIE: $failed")
        return Dialog("ARTIE could not do that", listOf("${failed.message}"), buttons = emptyList())
    }

    private fun render(player: Player, session: DialogSession, dialog: Dialog): PaperDialog = PaperDialog.create {
        it.empty()
            .base(
                DialogBase.builder(Component.text(dialog.title))
                    .canCloseWithEscape(true)
                    // Every button's answer is the next dialog: the player waits for it, rather than see the world.
                    .afterAction(
                        if (dialog.buttons.isEmpty()) DialogBase.DialogAfterAction.CLOSE
                        else DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE,
                    )
                    .body(body(dialog))
                    .inputs(dialog.inputs.map(::input))
                    .build(),
            )
            .type(type(player, session, dialog))
    }

    /**
     * [dialog]'s text, then its titled values, each as one block of lines: Minecraft spaces blocks apart, and lines
     * of one block close together. A value's title is gold, the value white, so each line reads at a glance.
     */
    private fun body(dialog: Dialog): List<DialogBody> = listOf(
        dialog.body.map { Component.text(it) },
        dialog.facts.map { Component.text("${it.label}: ", NamedTextColor.GOLD).append(Component.text(it.value)) },
    ).filter { it.isNotEmpty() }
        .map { lines -> DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(), lines), WIDTH) }

    /** [dialog]'s buttons, two to a row when there are more than one; with none, a notice the player closes. */
    private fun type(player: Player, session: DialogSession, dialog: Dialog): DialogType =
        if (dialog.buttons.isEmpty()) {
            DialogType.notice()
        } else {
            DialogType.multiAction(dialog.buttons.map { button(player, session, dialog, it) })
                .columns(if (dialog.buttons.size > 1) 2 else 1).build()
        }

    /**
     * A button that sends back what the player entered in [dialog]'s inputs, once, and says on hover what it does.
     * A greyed one is grey, as Minecraft shows a button that is off; on hover it says why.
     */
    private fun button(player: Player, session: DialogSession, dialog: Dialog, button: Button): ActionButton {
        val pressed = DialogAction.customClick(
            { response, _ ->
                val values = dialog.inputs.associate { it.key to response.getText(it.key).orEmpty() }
                later(player, session) { session.press(button, values) }
            },
            ClickCallback.Options.builder().uses(1).build(),
        )
        val label = Component.text(button.label, if (button.greyed) NamedTextColor.GRAY else null)
        return ActionButton.create(label, button.hover?.let(Component::text), BUTTON_WIDTH, pressed)
    }

    private fun input(input: Input): DialogInput = when (input) {
        is TextInput -> DialogInput.text(input.key, Component.text(input.label))
            .maxLength(input.maxLength).width(WIDTH).build()
        is OptionInput -> DialogInput.singleOption(
            input.key,
            Component.text(input.label),
            input.options.mapIndexed { index, option ->
                OptionEntry.create(option.id, Component.text(option.shown), index == 0)
            },
        ).width(WIDTH).build()
    }

    private companion object {
        /** How wide a line of text or an input is, in Minecraft's units (it allows 1 to 1024). */
        const val WIDTH = 300

        /** How wide a button is (1 to 1024): Minecraft's own default, so two stand side by side. */
        const val BUTTON_WIDTH = 150
    }
}
