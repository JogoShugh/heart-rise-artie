package heart.script

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * Each step of a script held as data, made through the function a Kotlin script calls for it
 * (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 4). A maker names the keys its step may hold, and no others: the
 * vocabulary of spec/schemas/heart-script.draft.schema.json, closed.
 */

private val WORDS = setOf("intent", "inferred", "from-intent")

/** The steps in [step]'s [key], made where the script stands then, with the same reason, if any. */
private fun Here.inner(step: Map<*, *>, key: String, reading: Reading) =
    (step[key] as? List<*>)?.let { made(it, reading.why) }

/** A block of [step]'s [key], for a function that takes one; null when it has none. */
private fun block(step: Map<*, *>, key: String, reading: Reading): (Here.() -> Unit)? =
    (step[key] as? List<*>)?.let { steps -> { made(steps, reading.why) } }

/** The values of [step]'s fields, each reference read where the script stands, before its act. */
private fun values(step: Map<*, *>, reading: Reading): List<Pair<String, Any?>> =
    (step["fields"] as? Map<*, *>).orEmpty().map { (k, v) -> k.toString() to reading.value(v) }

/** A span of time, as data says one: `15s`, `3m`, `2h`. */
private fun span(text: Any?): Duration? = text?.toString()?.let { said ->
    val n = said.dropLast(1).toLongOrNull()
    when (said.lastOrNull()) {
        's' -> n?.seconds
        'm' -> n?.minutes
        'h' -> n?.hours
        else -> null
    } ?: stop("not a span of time: $said")
}

/** Facts as data, for a function that reads them: grouped by the resource kept they are read on, if any. */
internal fun facts(list: Any?, reading: Reading): Facts.() -> Unit = {
    val all = (list as? List<*>).orEmpty().map { it as? Map<*, *> ?: stop("a fact is not a map: $it") }
    all.groupBy { it["at"]?.toString() }.forEach { (name, group) ->
        val hold: Facts.() -> Unit = { group.forEach { fact(it, reading.at(here)) } }
        if (name == null) hold() else at(name, hold)
    }
}

private fun Facts.fact(fact: Map<*, *>, reading: Reading) {
    val field = fact["field"]?.toString() ?: stop("a fact names no field: $fact")
    if ("is" in fact) field eq reading.value(fact["is"]) else field has reading.value(fact["has"])
}

/** A check's facts, as Kotlin's `check("merged" eq true)` tests them. */
private fun checked(list: Any?, reading: Reading): List<Fact> =
    (list as? List<*>).orEmpty().map { it as? Map<*, *> ?: stop("a fact is not a map: $it") }.map { fact ->
        val field = fact["field"].toString()
        val matcher = if ("is" in fact) equalTo(reading.value(fact["is"])) else contains(reading.value(fact["has"]))
        Fact(field, matcher)
    }

/** A step with words and no relation: the reasoner chooses what is offered here, on every run. */
internal val REASONED = Maker(WORDS + setOf("reasoned", "then")) { step, reading ->
    val words = said(step, reading)
    act(
        intent = words.intent, inferred = words.inferred, fromIntent = words.fromIntent,
        block = block(step, "then", reading),
    )
}

/** The makers, by the key that names each step. */
internal val MAKERS: Map<String, Maker> = linkedMapOf(
    "act" to Maker(setOf("act", "fields", "then") + WORDS) { step, reading ->
        val values = values(step, reading)
        val words = said(step, reading)
        act(
            step["act"].toString(),
            intent = words.intent, inferred = words.inferred, fromIntent = words.fromIntent,
            fields = { values.forEach { (name, value) -> name to value } },
            block = block(step, "then", reading),
        )
    },
    "act-once" to Maker(setOf("act-once", "fields", "listed-in")) { step, reading ->
        val values = values(step, reading)
        actOnce(
            step["act-once"].toString(),
            listedIn = step["listed-in"].toString(),
            fields = { values.forEach { (name, value) -> name to value } },
            block = {},
        )
    },
    "follow" to Maker(setOf("follow", "title", "then") + WORDS) { step, reading ->
        val words = said(step, reading)
        follow(
            step["follow"].toString(), step["title"]?.let { reading.text(it.toString()) },
            intent = words.intent, inferred = words.inferred, fromIntent = words.fromIntent,
            block = block(step, "then", reading),
        )
    },
    "keep-as" to Maker(setOf("keep-as")) { step, _ -> keepAs(step["keep-as"].toString()) },
    "sync" to Maker(setOf("sync")) { _, _ -> sync() },
    "note" to Maker(setOf("note")) { step, reading -> note(reading.text(step["note"].toString())) },
    "check" to Maker(setOf("check")) { step, reading -> checkFacts(checked(step["check"], reading)) },
    "until" to Maker(setOf("until", "tries", "every")) { step, reading ->
        val until = step["until"] as? Map<*, *> ?: stop("until holds a relation offered, or facts")
        val tries = (step["tries"] as? Number)?.toInt() ?: UNTIL_TRIES
        val offered = until["offered"]?.toString()
        until(tries, span(step["every"]) ?: 1.seconds) {
            if (offered != null) offers(offered) else holds(facts(until["facts"], reading))
        }
    },
    "watch" to Maker(setOf("watch")) { step, reading ->
        val watch = step["watch"] as? Map<*, *> ?: stop("a watch holds its steps")
        watch(
            every = span(watch["every"]) ?: 15.seconds,
            atMost = span(watch["at-most"]) ?: 2.hours,
            until = if ("until" in watch) facts(watch["until"], reading) else ({ that(false) }),
        ) { inner(watch, "steps", reading) }
    },
    "at" to Maker(setOf("at", "steps")) { step, reading ->
        at(reading.text(step["at"].toString())) { inner(step, "steps", reading) }
    },
    "listeners" to Maker(setOf("listeners")) { step, reading ->
        val listeners = step["listeners"] as? Map<*, *> ?: stop("listeners hold what each listens for")
        listeners(
            every = span(listeners["every"]) ?: 15.seconds,
            atMost = span(listeners["at-most"]) ?: 2.hours,
            until = if ("until" in listeners) facts(listeners["until"], reading) else ({ that(false) }),
        ) {
            (listeners["listen"] as? List<*>).orEmpty().map { it as Map<*, *> }.forEach { listened ->
                listen(reading.text(listened["match"].toString()), from = listened["from"]?.toString()) {
                    inner(listened, "steps", reading)
                }
            }
        }
    },
    "whenever" to Maker(setOf("whenever")) { step, reading -> whenever { inner(step, "whenever", reading) } },
    "has" to Maker(setOf("has")) { step, reading -> has(facts(step["has"], reading)) },
    "made-by" to Maker(setOf("made-by")) { step, reading -> madeBy(reading.text(step["made-by"].toString())) },
    "saying" to Maker(setOf("saying")) { step, reading -> saying(reading.text(step["saying"].toString())) },
    "leave-where" to Maker(setOf("leave-where")) { step, reading ->
        val leave = step["leave-where"] as? Map<*, *> ?: stop("leave-where holds what to say, and facts")
        leaveWhere(reading.text(leave["say"].toString()), facts(leave["facts"], reading))
    },
    "once" to Maker(setOf("once")) { step, reading ->
        val once = step["once"] as? Map<*, *> ?: stop("once holds the relation it waits for, and steps")
        val otherwise = once["otherwise"] as? List<*>
        once(
            once["offered"].toString(),
            giveUp = span(once["give-up"]) ?: 10.minutes,
            every = span(once["every"]) ?: 1.seconds,
            otherwise = { why -> otherwise?.let { made(it, why) } },
        ) { inner(once, "steps", reading) }
    },
    "given" to Maker(setOf("given", "then")) { step, reading ->
        given(facts(step["given"], reading)) then { inner(step, "then", reading) }
    },
    "doing" to Maker(setOf("doing", "steps")) { step, reading ->
        doing(reading.text(step["doing"].toString())) { inner(step, "steps", reading) }
    },
    "done-where" to Maker(setOf("done-where")) { step, reading -> doneWhere(facts(step["done-where"], reading)) },
)

/** How many times a wait looks, as Kotlin's `until` does by default. */
private const val UNTIL_TRIES = 10
