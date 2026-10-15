package com.artie.heart.fixtures

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** A lamp: the smallest resource type with an act that needs a body and acts that do not. */
data class Lamp(val id: Long, val status: String, val brightness: Int, val version: Int)

/** A lamp as its body reads, for the schema of its state: every field always there. */
@Schema(title = "Lamp")
data class LampBody(
    @field:NotNull @field:Schema(title = "Lamp id")
    val id: Long? = null,
    @field:NotNull @field:Schema(title = "Status", description = "Whether the lamp is on")
    val status: String? = null,
    @field:NotNull @field:Min(1) @field:Max(100) @field:Schema(title = "Brightness")
    val brightness: Int? = null,
)

/** The dim act's input. */
@Schema(title = "Dim")
data class Dim(
    @field:NotNull @field:Min(1) @field:Max(100)
    @field:Schema(title = "Brightness", description = "Percent of full brightness", examples = ["50"])
    val brightness: Int? = null,
    // Optional, but not empty when given; @NotBlank would also make it required.
    @field:Size(min = 1)
    @field:Schema(title = "Label", examples = ["reading"])
    val label: String? = null,
)

/** The input of installing a lamp, an act the bookmark offers: the new lamp's brightness. It is installed off. */
@Schema(title = "Install a lamp")
data class InstallLamp(
    @field:NotNull @field:Min(1) @field:Max(100)
    @field:Schema(title = "Brightness", description = "Percent of full brightness", examples = ["50"])
    val brightness: Int? = null,
)

/** Switching on or off takes no input. */
@Schema(title = "Switch off")
class SwitchOff

/** An input with a field that must not be blank: a name. */
@Schema(title = "Rename")
data class Rename(
    @field:NotBlank
    @field:Schema(title = "Name")
    val name: String = "",
)

/** An input whose field must not be blank and must also match a pattern of its own: capitals and spaces. */
@Schema(title = "Code")
data class Code(
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z ]+$")
    @field:Schema(title = "Code")
    val code: String = "",
)

/** An input that grew carefully: a new field with a default, and an old one kept but deprecated. */
@Schema(title = "Glow")
data class Glow(
    @field:Schema(title = "Colour", allowableValues = ["warm", "cool"], defaultValue = "warm")
    val colour: String? = null,
    @field:Schema(title = "Shade", deprecated = true)
    val shade: String? = null,
)

/** The lamp resource type, declared the way a domain server declares its own. */
object Lamps {
    fun profile(name: String = "home") = Profile(
        name = name,
        title = "Lamps",
        doc = "The acts a lamp offers.",
        relations = listOf(
            Relation("switch-on", AlpsType.UNSAFE, "Switch on", "Turns the lamp on.", "Offered while off."),
            Relation("switch-off", AlpsType.UNSAFE, "Switch off", "Turns the lamp off.", "Offered while on."),
            Relation("dim", AlpsType.IDEMPOTENT, "Dim the lamp", "Sets the brightness.", "Offered while on."),
            Relation("install-lamp", AlpsType.UNSAFE, "Install a lamp", "Adds a new lamp, switched off.", "Offered by the service index."),
            Relation("search-lamps", AlpsType.SAFE, "Find lamps by name", "Lists the lamps whose name has the text.", "A search."),
            Relation("get-lamp", AlpsType.SAFE, "Get a lamp by its id", "Goes to one lamp, given its id.", "A lookup."),
        ),
    )

    fun type(profile: Profile = profile()): ResourceType<Lamp, String> = ResourceType(
        collection = "lamps",
        profile = profile,
        acts = listOf(
            // Switching on and off take no input, so they share one schema.
            Act.of("switchOn", "switch-on", SwitchOff::class.java),
            Act.of("switchOff", "switch-off", SwitchOff::class.java),
            // Dimming used to be at "brighten"; that path still works (SPEC §6.2, former paths).
            Act.of("dim", "dim", Dim::class.java, formerPaths = listOf("brighten")),
        ),
        offers = Offers(
            keys = listOf("ON", "OFF"),
            offered = { key -> if (key == "ON") listOf("switchOff", "dim") else listOf("switchOn") },
            whyAbsent = { key, _ ->
                if (key == "ON") "The lamp is already on." else "The lamp is off; switch it on."
            },
        ),
        state = StateView(
            keyOf = { it.status },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.id, "status" to it.status, "brightness" to it.brightness) },
            bodyClass = LampBody::class.java,
            keyField = "status",
        ),
        bookmarkActs = listOf(Act.of("installLamp", "install-lamp", InstallLamp::class.java)),
        finding = Finding(
            noun = "lamp",
            search = Named("searchLamps", "search-lamps"),
            lookup = Named("getLamp", "get-lamp"),
            nameDescription = "Part of the lamp's name; case is ignored",
            idTitle = "Lamp id",
            idDescription = "The lamp's number",
        ),
    )
}
