package heart.script

/**
 * What must hold, as a block (spec/ideas/dsl-conditions-as-facts.md): `"state" eq "open"`, `"github:labels" has
 * "alert"`, a fact about a resource kept by name, `at("watch") { … }`, or any condition the script works out,
 * `that(…)`. The block is ordinary Kotlin, so a fact may depend on an input; it reads, and never acts. It ends up as
 * the facts `holds` reads on the resource held, and the further conditions beside them.
 */
@HeartDsl
class Facts internal constructor(internal val here: Here) {
    internal val wanted = linkedMapOf<String, Any?>()
    private val others = mutableListOf<() -> Boolean>()

    /** The member [this] equals [value]. */
    infix fun String.eq(value: Any?) {
        wanted[this] = value
    }

    /** The member [this]'s values include [item]: an item of its list, an embedded resource, a link's title. */
    infix fun String.has(item: Any?) {
        wanted[this] = (wanted[this] as? List<*>).orEmpty() + item
    }

    /** The resource kept as [name] (`keepAs`), read again, holds [facts]; a name kept nowhere stops the script. */
    fun at(name: String, facts: Facts.() -> Unit) {
        others += { here.heldAt(name, facts) }
    }

    /** A condition the script works out itself. */
    fun that(condition: Boolean) {
        others += { condition }
    }

    internal fun hold(): Boolean = here.holds(wanted) && others.all { it() }
}

/** Whether every one of [facts] holds on the resource held. */
fun Here.holds(facts: Facts.() -> Unit): Boolean = Facts(this).apply(facts).hold()
