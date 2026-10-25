package heart.script

/*
 * Conditions as data, not Kotlin's if: a script, and a watch written from a goal's If, says which facts it
 * goes by, as `holds` reads them on the resource held, and the DSL decides.
 */

/** Run [block] where every one of [facts] holds on the resource held; else do nothing here. */
fun Here.onlyWhere(facts: Map<String, Any?>, block: Here.() -> Unit) {
    if (holds(facts)) block()
}

/**
 * Run [block] unless every one of [facts] holds on the resource held. Where they do, the resource is left, and the
 * run notes so: "Left <address>: [leaving]".
 */
fun Here.unless(facts: Map<String, Any?>, leaving: String, block: Here.() -> Unit) {
    if (holds(facts)) note("Left ${held.address}: $leaving") else block()
}
