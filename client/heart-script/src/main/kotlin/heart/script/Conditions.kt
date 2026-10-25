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

/** A guard ended a block: each block it passes through on its way out goes back where it was, read again. */
internal open class Ended : RuntimeException(null, null, false, false)

/** A whenever's member is left: the guard that ended its turn, for whenever to catch. */
internal class MemberLeft : Ended()

/** A part of the script is done already: the guard that ended it, for doing to catch. */
internal class PartDone : Ended()

/** How deep in doing blocks this thread is, so a doneWhere in none stops the script. */
private val doings = ThreadLocal.withInitial { 0 }

/**
 * A part of the script, which [words] say, run as [block]: a doneWhere in it ends it where the part is done
 * already, and the script goes on after it.
 */
fun Here.doing(words: String, block: Here.() -> Unit) {
    doings.set(doings.get() + 1)
    try {
        block()
    } catch (_: PartDone) {
        note("Done already: $words")
    } finally {
        doings.set(doings.get() - 1)
    }
}

/** Guard: where [facts] hold, the doing block this is in is done already, and ends. */
fun Here.doneWhere(facts: Facts.() -> Unit) {
    kotlin.check(doings.get() > 0) { "doneWhere ends a doing block, and is in none" }
    if (holds(facts)) throw PartDone()
}

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
