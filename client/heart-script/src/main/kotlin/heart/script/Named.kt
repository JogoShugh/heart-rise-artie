package heart.script

/*
 * Resources kept by name, as a goal names them (`as watch`), so a script reads one again by its name rather than
 * by a variable it fills: `act(…) { keepAs("watch") }`, then `until = holdsAt("watch", facts)`.
 */

/** Keep the resource held here under [name], its address the one the server gave. */
fun Here.keepAs(name: String) {
    run.kept[name] = held.address
}

/**
 * Whether every one of [facts] holds on the resource kept as [name], read again: an Until or a condition as data. A
 * name not kept yet holds nothing.
 */
fun holdsAt(name: String, facts: Map<String, Any?>): Here.() -> Boolean = {
    val address = run.kept[name]
    var holding = false
    if (address != null) at(address) { holding = holds(facts) }
    holding
}
