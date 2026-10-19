package heart

import heart.script.Answer
import heart.script.Guard
import heart.script.Pinned
import heart.script.Used
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What ARTIE tells a script's run of the affordance a step used (spec/PLAN-LINE-BY-LINE-REPORT.md): the form or
 * link, as the representation it chose from offered it, the address it filled in, and what a replay would guard
 * on (spec/ideas/replay-guards-lenient-strict.md). For the run's record only.
 */
internal object UsedAffordances {

    /**
     * The form [rel] of [held], acted on with [fields]; null when [held] offers no such form. [rung] is the rung
     * the server applied to [held] (SPEC §6.10): a bookmark's lookup forms come inline at every rung.
     */
    fun form(
        held: HeartRepresentation?,
        rel: String,
        fields: JsonObject,
        rung: Rung?,
        guard: ScriptGuard? = null,
    ): Used? {
        val form = held?.affordance(rel) ?: return null
        val sends = form.method != "GET"
        return Used(
            kind = "form",
            relation = rel,
            title = form.title,
            method = form.method,
            target = form.target,
            address = targetOf(form, held, fields),
            fields = form.schema["properties"]?.jsonObject?.keys?.toList().orEmpty(),
            // Each field's type as the schema says, so a script made from the run writes 91, not "91".
            types = form.schema["properties"]?.jsonObject.orEmpty().mapNotNull { (name, property) ->
                ((property as? JsonObject)?.get("type") as? JsonPrimitive)?.content?.let { name to it }
            }.toMap(),
            guard = Guard(
                rung = rung?.name,
                set = held.forms?.let { Pinned(it.href, it.integrity) },
                form = form.integrity,
                schema = form.schemaRef?.let { Pinned(it.href, it.integrity) },
                ifMatch = held.etag.takeIf { sends },
                checked = guard?.mode,
                unpinned = guard != null && !guard.strict && guard.unpinned(rel),
            ),
        )
    }

    /** The link [rel] titled [title] of [held], or the embedded resource followed as one; null when none. */
    fun link(held: HeartRepresentation?, rel: String, title: String?, guard: ScriptGuard? = null): Used? {
        val link = held?.links(rel)?.firstOrNull { it.title == title }
            ?: held?.embedded(rel)?.firstOrNull { it.title == title }?.self
            ?: return null
        val checked = guard?.let {
            Guard(checked = it.mode, unpinned = !it.strict && it.unpinnedLink(rel))
        }
        return Used("link", rel, title, "GET", target = link.href, address = link.href, guard = checked)
    }

    /** The affordance a reasoner's [decision] used, made from [before], read at [rung]; null with no selection. */
    fun chosen(before: HeartRepresentation?, decision: Decision?, rung: Rung?): Used? {
        val selection = when (decision) {
            is Decision.Acted -> decision.selection
            is Decision.Followed -> decision.selection
            is Decision.Refused -> decision.selection
            else -> null
        }
        return when (selection) {
            is Selection.Act -> form(before, selection.affordance, selection.fields, rung)
            is Selection.Follow -> link(before, selection.rel, selection.title)
            else -> null
        }
    }

    /** [answer], with the affordance it used. */
    fun with(answer: Answer, used: Used?): Answer = when {
        used == null -> answer
        answer is Answer.Done -> Answer.Done(answer.now, used)
        answer is Answer.NewResource -> Answer.NewResource(answer.now, used)
        answer is Answer.Refused -> Answer.Refused(answer.kind, answer.by, answer.detail, used)
        else -> answer
    }
}
