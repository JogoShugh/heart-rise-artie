package heart.script

import kotlinx.serialization.json.JsonObject
import java.util.ServiceLoader

/**
 * What a HEART client holds now (SPEC §6.8): a resource's state, the acts its
 * manifest offers, by relation, its links, each relation with its links'
 * titles (null for a link with none), and the resources it embeds (SPEC §6.1),
 * by relation. [address] is for the run's record only: a script never sees
 * it, and never names one.
 */
class Representation(
    val address: String,
    val state: JsonObject,
    val offered: Set<String>,
    val links: Map<String, List<String?>>,
    val embedded: Map<String, List<Embedded>> = emptyMap(),
    /**
     * The links the client can follow, by relation and title: what a model is shown. A link it has no binding
     * for, a template that needs values, a link out of HEART: [links] keeps them, as the server sent them.
     */
    val followable: Map<String, List<String?>> = links,
)

/**
 * A resource the held one embeds (SPEC §6.1): its title and its state, read in
 * place with no request. It has no forms: to act on it, a script follows its
 * relation by its title. [address] is for the run's record only.
 */
class Embedded(val title: String?, val state: JsonObject, internal val address: String) {
    /** Its state, read by JSON Pointer, as the held resource's is. */
    val read: State get() = State(state)
}

/** The resources the held one embeds under [rel], read in place with no request (SPEC §6.1). */
fun Here.embedded(rel: String): List<Embedded> = held.embedded[rel].orEmpty()

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

    /** A script's lock file refused the act: what it is about to use is not what the script was recorded with. */
    Guard("guard"),
}

/**
 * What a script was recorded against, from its lock file (spec/PLAN-SCRIPT-GENERATOR.md): the [rung] it read at,
 * each form's own integrity and the sets it was offered in, by relation, and each link's target template.
 */
class Pins(
    val rung: String? = null,
    val forms: Map<String, FormPin> = emptyMap(),
    val links: Map<String, String> = emptyMap(),
)

/**
 * One form's pin: its own integrity (HAL-SCHEMA-FORMS-HR Rule 10), the affordance sets it was offered in, and its
 * [method], which a form that changed must keep for a run to go past it by the values it sends.
 */
class FormPin(val integrity: String?, val sets: List<String> = emptyList(), val method: String? = null)

/** What came of a selection, in the core's terms (SPEC §6.0); [used], the affordance the client used, if any. */
sealed interface Answer {
    val used: Used?

    /** Done: what the client holds now, the resource's new state or what a link led to. */
    class Done(val now: Representation, override val used: Used? = null) : Answer

    /** The act brought a resource into being, and the client holds it now. */
    class NewResource(val now: Representation, override val used: Used? = null) : Answer

    /** Refused, by [by], with HEART's own reason. */
    class Refused(val kind: Refusal, val by: Side, val detail: String, override val used: Used? = null) : Answer
}

/** A resource the server names by address and integrity (SPEC §6.5): an affordance set, a schema. */
class Pinned(val href: String, val integrity: String?)

/**
 * What a replay checks before it acts (spec/ideas/replay-guards-lenient-strict.md): the [rung] read at, the
 * affordance [set] the form came from, the [form]'s own integrity, its [schema], and the If-Match sent.
 */
// A record names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class Guard(
    val rung: String? = null,
    val set: Pinned? = null,
    val form: String? = null,
    val schema: Pinned? = null,
    val ifMatch: String? = null,
    /** How a lock file was held to when the act was checked: lenient or strict; null when unguarded. */
    val checked: String? = null,
    /** Whether the lock file pinned nothing for this relation, and a lenient run went on. */
    val unpinned: Boolean = false,
    /** What a lenient run went past: an unchanged form in a set the lock file didn't record, with both sets. */
    val warning: String? = null,
    /**
     * What held the act to the lock file, when it passed: `set`, offered in a set recorded for it, which covers every
     * form in it; `form`, its own integrity alone, its set new or none (a lookup form of the service index);
     * `values`, the form changed, but kept its method, and what the script sent fits it (`on-change: values`).
     */
    val held: String? = null,
)

/**
 * The affordance a selection used, as the client made it, for the run's record only
 * (spec/PLAN-LINE-BY-LINE-REPORT.md): a form or a link, its relation and title, the method, the [target] as
 * offered (a form's URI template, or a link's href), the [address] requested, the [fields] the form asks for,
 * and what a replay would guard on. A script never reads it.
 */
// A record names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class Used(
    val kind: String,
    val relation: String,
    val title: String? = null,
    val method: String,
    val target: String,
    val address: String,
    val fields: List<String> = emptyList(),
    val guard: Guard? = null,
    /** Each field's JSON Schema type, as the form's schema says, by name. */
    val types: Map<String, String> = emptyMap(),
)

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

    /**
     * The resource at [address] again, where the script reads it again for care, not because the task asks: on
     * leaving a block, and at sync(). A client off the rails (spec/ideas/off-the-rails.md) gives what it last held
     * there, reading nothing; any other reads it again.
     */
    fun recall(address: String): Answer = visit(address)

    /** Why the act [rel] is not offered, as the server says (SPEC §6.12); null when it says nothing. */
    fun why(rel: String): String?

    /** What the relation [rel] means, from its profile; null when it says nothing. */
    fun describe(rel: String): String?

    /**
     * The exchanges the client made since it was last asked, when it keeps them for a run's capture
     * (spec/PLAN-LINE-BY-LINE-REPORT.md): each request as sent and its answer, credentials never in them.
     */
    fun exchanges(): List<Exchange> = emptyList()

    /** What its model calls took, by count: calls, input, output, cache_read, cache_write; empty with no model. */
    val usage: Map<String, Long> get() = emptyMap()

    /** The client's planner for a goal run (spec/PLAN-PLAIN-TEXT-RUN.md); null when it has none. */
    fun planner(): Planner? = null

    /** Why the client has no planner, when it has none: for ARTIE, where the model key goes. */
    val noPlanner: String get() = "this client has no planner"

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

/**
 * A reason step's outcome: what was [chosen] (null when nothing was), the [answer] to making it, and each call
 * the client made to its [model] for it, for the run's record.
 */
class Reasoned(val chosen: Choice?, val answer: Answer, val model: List<ModelCall> = emptyList())

/** One exchange a client made: when, how long it took, the request it [sent] and what it [received]. */
class Exchange(val at: java.time.Instant, val ms: Long, val sent: Sent, val received: Received)

/** A request as sent: credentials written `<token>`. */
class Sent(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

/** An answer as received. */
class Received(val status: Int, val headers: Map<String, String>, val body: String?)

/** One call to a reasoner's model: which [model], what it was [asked], and what it [answered]. No key. */
class ModelCall(val model: String?, val asked: String, val answered: String)

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
 * [reasoner] to use for `act(intent = …)` steps (for ARTIE: `claude`). Null: the client's own choice, or none.
 */
class ClientSettings(
    val rung: String? = null,
    val reasoner: String? = null,
    /** Whether the client keeps its exchanges for the run's capture (`--exchanges`). */
    val exchanges: Boolean = false,
    /** The script's lock file, when it has one; the client checks each act against it before sending. */
    val pins: Pins? = null,
    /** How careful the run is: how it holds to the lock file, and what its acts carry. */
    val guarding: Guarding = Guarding(),
)

/** How careful a run is (spec/ideas/replay-guards-lenient-strict.md, spec/ideas/off-the-rails.md). */
class Guarding(
    /** How the lock file is held to: "lenient" (the default), "strict", "look-then-leap" or "off-the-rails". */
    val guard: String? = null,
    /**
     * What a lenient run does when a form it uses changed: "refuse" (the default), or "values", act when the form
     * keeps its pinned method and what the script sends fits its new schema.
     */
    val onChange: String? = null,
    /**
     * The validator each act carries (spec/ideas/off-the-rails.md): "last" (the default), the last one the run was
     * given for the resource, or "none", a blind write, which only a server's development switch takes.
     */
    val validators: String? = null,
)
