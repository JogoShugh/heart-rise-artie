package heart.script

/*
 * Resources kept by name, as a goal names them (`as watch`), so a script reads one again by its name rather than
 * by a variable it fills: `act(…) { keepAs("watch") }`, then `until = { at("watch") { "state" eq "closed" } }`.
 */

/** Keep the resource held here under [name], its address the one the server gave. */
fun Here.keepAs(name: String) {
    run.kept[name] = held.address
}

/**
 * Whether the resource kept as [name], read again, holds [facts]. A name kept nowhere is a mistake in the script, and
 * stops it: read as a fact that doesn't hold, it would keep a watch's Until from ever ending.
 */
internal fun Here.heldAt(name: String, facts: Facts.() -> Unit): Boolean {
    val address = requireNotNull(run.kept[name]) {
        "nothing is kept as \"$name\"; keepAs(\"$name\") where it is reached"
    }
    var holding = false
    at(address) { holding = holds(facts) }
    return holding
}
