package heart.cli.generate

/*
 * The walk of a capture (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 2): what each step of a generated script is,
 * nested as the run nested it, decided once, before any language writes it. ScriptWriter writes it as Kotlin; the
 * values each step sends, and why, are worked out as it is written.
 */

/** The statements of a block, at the step that opened it. */
class Block(
    /** The resources the step led to, kept by the names the values read from them are read by. */
    val keeps: List<Variable>,
    /** The resource it led to, kept by the name the watch reads it again by, when no value is read from it. */
    val watched: String?,
    /** The steps nested in it, as the run nested them. */
    val statements: List<Walked>,
    /** The titles of the items the resource it was opened at linked: the list its follows went through, if any. */
    val listed: List<String> = emptyList(),
    /** The relation of the step that opened it, such as the search that made the list. */
    val opener: String? = null,
)

/** This block with no reads again of its own: a scope's waits read the reply again, which the scope does itself. */
internal fun Block.withoutSyncs() = Block(keeps, watched, statements.filterNot { it is Walked.Sync }, listed, opener)

/** The marks by which a goal's step opened a block of its own (goal-rise.feature). */
internal val SCOPES = listOf("enter", "once-answered", "run-script", "given")

/** One statement of a generated script: a step of the run, and its row. */
sealed interface Walked {
    val step: Step

    /** An act or follow, made as [call] says; [block] its steps, and [fresh] when it reads a new resource again. */
    class Selection(override val step: Step, val call: Call, val fresh: Boolean, val block: Block) : Walked

    /** The held resource read again, where the run did. */
    class Sync(override val step: Step) : Walked

    /** A wait or a check, as the run's script wrote it. */
    class Waits(override val step: Step) : Walked

    /**
     * A block a goal's step opened by its words (goal-rise.feature): `enter` another party's entry, [value] its
     * address as the script reads it, or `once-answered`, [value] the reply it waits on; [block] its steps.
     */
    class Scope(override val step: Step, val kind: String, val value: String, val block: Block) : Walked
}

/** How a selection is made again. */
sealed interface Call {
    /** As the reason step it was: a goal step marked reasoned, or one kept with --keep-reason. */
    data object Reasoned : Call

    /** In its [words] alone, for the reasoner to choose on every run (--intents). */
    class Words(val words: String) : Call

    /** The link the run followed. */
    data object Follow : Call

    /** The form the run acted on, with the values it sent. */
    data object Act : Call
}
