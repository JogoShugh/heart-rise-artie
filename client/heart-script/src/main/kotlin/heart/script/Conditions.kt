package heart.script

/*
 * Conditions as facts blocks, not Kotlin's if (spec/ideas/dsl-conditions-as-facts.md): a branch, `given { facts }
 * then { … } otherwise { … }`, and the guards of a whenever's member, each of which lets the rest run or ends the
 * member's turn.
 */

/** A branch on [facts], read now on the resource held: `then` runs where they hold, `otherwise` where they don't. */
fun Here.given(facts: Facts.() -> Unit): Given = Given(this, holds(facts))

/** A branch, its facts read once. */
@HeartDsl
class Given internal constructor(private val here: Here, private val holding: Boolean) {
    /** Run [block] where the facts hold. */
    infix fun then(block: Here.() -> Unit): Given {
        if (holding) here.block()
        return this
    }

    /** Run [block] where the facts don't hold. */
    infix fun otherwise(block: Here.() -> Unit) {
        if (!holding) here.block()
    }
}

/** A whenever's member is left: the guard that ended its turn, for whenever to catch. */
internal class MemberLeft : RuntimeException(null, null, false, false)

/** Guard: the rest of this member's turn runs only where [facts] hold. */
fun Here.has(facts: Facts.() -> Unit) {
    if (!holds(facts)) throw MemberLeft()
}

/** Guard: where [facts] hold, the member is left, and the run notes "Left <address>: [words]". */
fun Here.leaveWhere(words: String, facts: Facts.() -> Unit) {
    if (holds(facts)) {
        note("Left ${held.address}: $words")
        throw MemberLeft()
    }
}

/** Guard: the rest runs only for a member [who] made, by its embedded author, user or creator. */
fun Here.madeBy(who: String) {
    if (!held.kept(who, null)) throw MemberLeft()
}

/** Guard: the rest runs only for a member whose words say [words], in any case. */
fun Here.saying(words: String) {
    if (!held.kept(null, words)) throw MemberLeft()
}
