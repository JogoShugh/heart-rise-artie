package heart.script

import kotlin.properties.ReadOnlyProperty

/*
 * Resources kept by name, as a goal names them (`as watch`), so a script reads one again by its name rather than
 * by a variable it fills: `act(…) { keepAs("watch") }`, then `until = { at("watch") { "state" eq "closed" } }`, and
 * `val number by kept("watch") { long("number") }` for a value it read there.
 */

/** Keep the resource held here under [name], its address the one the server gave, and its state now. */
fun Here.keepAs(name: String) {
    run.kept[name] = Run.Kept(held.address, state)
}

/**
 * A value read, with [read], from the resource kept as [name], as it was when it was kept: declared once, as an
 * input is, and read after the act that keeps it. Read before, it stops the script.
 */
fun <T> Here.kept(name: String, read: State.() -> T): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ ->
    val kept = requireNotNull(run.kept[name]) {
        "nothing is kept as \"$name\" yet; keepAs(\"$name\") before reading it"
    }
    kept.state.read()
}

/**
 * Whether the resource kept as [name], read again, holds [facts]. A name kept nowhere is a mistake in the script, and
 * stops it: read as a fact that doesn't hold, it would keep a watch's Until from ever ending.
 */
internal fun Here.heldAt(name: String, facts: Facts.() -> Unit): Boolean {
    val kept = requireNotNull(run.kept[name]) {
        "nothing is kept as \"$name\"; keepAs(\"$name\") where it is reached"
    }
    var holding = false
    at(kept.address) { holding = holds(facts) }
    return holding
}
