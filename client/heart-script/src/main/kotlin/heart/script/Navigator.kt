package heart.script

import kotlinx.serialization.json.JsonObject
import java.util.ServiceLoader

/**
 * What a HEART client holds now (SPEC §6.8): a resource's state, the acts its
 * manifest offers, by relation, and its links, each relation with its links'
 * titles (null for a link with none). [address] is for the run's record only:
 * a script never sees it, and never names one.
 */
class Representation(
    val address: String,
    val state: JsonObject,
    val offered: Set<String>,
    val links: Map<String, List<String?>>,
)

/** Which side refused: the client, before sending anything, or the server. */
enum class Side(val words: String) { CLIENT("the client"), SERVER("the server") }

/** Why a selection was refused, as HEART's core names it (SPEC §6.0), never by a status code. */
enum class Refusal(val words: String) {
    NotOffered("not offered"),
    Stale("stale"),
    InvalidInput("invalid input"),
    PreconditionRequired("precondition required"),
    NotAuthenticated("not authenticated"),
    Forbidden("forbidden"),
    NotFound("not found"),
    NotAChoice("not a choice"),
    NoBinding("no binding"),
    IntegrityMismatch("integrity mismatch"),
    Unreachable("unreachable"),
    NoReasoner("no reasoner"),
    Unclear("unclear to the reasoner"),
}

/** What came of a selection, in the core's terms (SPEC §6.0). */
sealed interface Answer {
    /** Done: what the client holds now, the resource's new state or what a link led to. */
    class Done(val now: Representation) : Answer

    /** The act brought a resource into being, and the client holds it now. */
    class NewResource(val now: Representation) : Answer

    /** Refused, by [by], with HEART's own reason. */
    class Refused(val kind: Refusal, val by: Side, val detail: String) : Answer
}

/**
 * A HEART client, as a script needs it: the contract any client implements
 * to run scripts (spec/PLAN-ARTIE-SCRIPTS.md, "Not ARTIE's: a contract").
 * Each call is a selection or a read, never an address but the entry, or an
 * address the client already holds; the client does the rest: target,
 * method, validator, binding.
 */
interface Navigator {
    /** The entry's representation: the only address a script knows. */
    fun enter(entry: String): Answer

    /** Select the form [rel], with [fields]: the client knows whether it reads or acts. */
    fun act(rel: String, fields: Map<String, Any?>): Answer

    /** Select the link [rel] titled [title] (null: the one with no title). */
    fun follow(rel: String, title: String?): Answer

    /** Read again a resource the client held before, at [address]: never one it wasn't given. */
    fun visit(address: String): Answer

    /** Why the act [rel] is not offered, as the server says (SPEC §6.12); null when it says nothing. */
    fun why(rel: String): String?

    /** What the relation [rel] means, from its profile; null when it says nothing. */
    fun describe(rel: String): String?

    /**
     * Hand [intent] to the client's reasoner, which chooses one selection from what the held representation
     * offers; the client checks it and makes it. A client with no reasoner refuses.
     */
    fun reason(intent: String): Reasoned =
        Reasoned(null, Answer.Refused(Refusal.NoReasoner, Side.CLIENT, "this client has no reasoner"))
}

/** What a reasoner chose: an act on the form [relation] with [fields], or a link [relation] titled [title]. */
class Choice(
    val relation: String,
    val fields: Map<String, Any?> = emptyMap(),
    val follow: Boolean = false,
    val title: String? = null,
)

/** A reason step's outcome: what was [chosen] (null when nothing was), and the [answer] to making it. */
class Reasoned(val chosen: Choice?, val answer: Answer)

/** How a HEART client is found to run scripts: by ServiceLoader, as a JDBC driver is. */
fun interface NavigatorProvider {
    fun navigator(): Navigator

    /** A client made as the run asks ([settings]); a setting a client doesn't support is its own choice. */
    fun navigator(settings: ClientSettings): Navigator = navigator()

    companion object {
        /** The first client on the classpath that provides one, made with [settings]. */
        fun load(settings: ClientSettings = ClientSettings()): Navigator =
            ServiceLoader.load(NavigatorProvider::class.java).firstOrNull()?.navigator(settings)
                ?: error("no HEART client on the classpath provides heart.script.NavigatorProvider")
    }
}

/**
 * What a run asks of the client it is given: the [rung] to read at (L0, L1, L2: SPEC §6.10), and the
 * [reasoner] to use for `reason(…)` steps (for ARTIE: `claude`). Null: the client's own choice, or none.
 */
class ClientSettings(val rung: String? = null, val reasoner: String? = null)
