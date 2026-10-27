package heart.cli.generate

import heart.cli.BadArguments
import heart.cli.generate.BareText.declaration
import heart.cli.generate.RailsRun.Companion.CANT

/**
 * A watch run brought down to a bare watch (spec/ideas/bare-runs.md, "Rails"): the kept script's watch, word by
 * word, each act the call it made to the service ([RailsRun]), each address a template ([Templates]). [names] are
 * the capture's and the upstream record's, for the script's first lines.
 */
internal class RailsWriter(private val evidence: RailsRun, private val names: Pair<String, String>) {
    private val capture = evidence.capture
    private val templates = Templates(capture.inputs)
    private val out = mutableListOf<String>()
    private val kept = mutableListOf<Source>()

    /** How many places the script calls the service. */
    var calls = 0
        private set

    fun write(): String {
        val script = capture.watchScript ?: throw BadArguments("$CANT the run has no watch")
        val units = UNITS.filter { ".$it" in script }.map { "import kotlin.time.Duration.Companion.$it" }
        val declared = capture.inputs.map { (name, input) -> declaration(name, input) }
        evidence.before.forEach { (name, call) ->
            val line = call(call, kept)
            emit(1, if (name == null) line else "val $name = $line")
            if (name != null) kept += Source(name, listOf(call.answer))
        }
        Words(script.lines().map { it.trim() }.filter { it.isNotEmpty() }).translate()
        val head = HEAD.format(names.first, names.second).lines() + units + listOf("").takeIf { units.isNotEmpty() }
            .orEmpty()
        return (head + declared + listOf("", "bare {") + out + "}").joinToString("\n") + "\n"
    }

    /** [call] as the script makes it: its method, its address and its body, each value read from [sources]. */
    private fun call(call: UpstreamCall, sources: List<Source>, words: String? = null): String {
        calls++
        val body = call.body?.let { ", " + templates.value(it, sources, words) }.orEmpty()
        return "call(\"${call.method}\", ${templates.text(call.url, sources)}$body)"
    }

    private fun emit(depth: Int, text: String) {
        out += INDENT.repeat(depth) + text
    }

    /** The kept script's watch, its [lines] one word at a time. */
    private inner class Words(private val lines: List<String>) {
        private var at = 0
        private var depth = 1
        private val closes = ArrayDeque<String?>()
        private var list: String? = null
        private var scope: List<Source> = kept

        fun translate() {
            while (at < lines.size) word(lines[at++])
        }

        private fun word(line: String) {
            val until = WATCH.matchEntire(line)
            val has = HAS.matchEntire(line)
            val leave = LEAVE.matchEntire(line)
            val once = ONCE.matchEntire(line)
            val act = ACT.matchEntire(line)
            when {
                line == "}" -> closes.removeLast()?.let { depth--; emit(depth, it) }
                until != null -> looks(until.groupValues[1], until.groupValues[2])
                line == "whenever {" -> whenever()
                has != null -> emit(depth, "has(${condition(has.groupValues[1], MEMBER)})")
                leave != null -> leave.destructured.let { (words, facts) ->
                    emit(depth, "leaveWhere($words, ${condition(facts, MEMBER)})")
                }
                once != null -> once.destructured.let { (_, giveUp, then) -> once(giveUp, then.startsWith(",")) }
                act != null && line.endsWith("{") -> {
                    list = act.groupValues[1]
                    closes.addLast(null)
                }
                else -> throw BadArguments("$CANT \"$line\" is no word a bare watch knows")
            }
        }

        /** Until: the resource kept as [name] read again, at the address its answer gave, until [facts] hold. */
        private fun looks(name: String, facts: String) {
            val source = kept.firstOrNull { it.name == name } ?: throw BadArguments("$CANT nothing was kept as $name")
            val url = (source.answers.firstOrNull() as? Map<*, *>)?.get("url")?.toString()
                ?: throw BadArguments("$CANT the answer kept as $name gives no url to read it again at")
            calls++
            emit(depth, "watch(until = { ${condition(facts, "call(\"GET\", ${templates.text(url, kept)})")} }) {")
            closes.addLast("}")
            depth++
        }

        /** Whenever: the list read as the run read it, and each member read as the gateway read it. */
        private fun whenever() {
            val relation = list ?: throw BadArguments("$CANT a whenever with no list before it")
            val listed = evidence.list(relation)
            val item = Source(ITEM, listOf(listed.answer), listed = true)
            emit(depth, "whenever(${call(listed, kept)}) { $ITEM ->")
            closes.addLast("}")
            depth++
            emit(depth, "val $MEMBER = ${call(evidence.member(), listOf(item) + kept)}")
            scope = listOf(Source(MEMBER, evidence.members.map { it.answer }), item) + kept
        }

        /** Once: its act's call, tried until taken or [giveUp]; its Otherwise's calls, if [otherwise]. */
        private fun once(giveUp: String, otherwise: Boolean) {
            val instead = if (otherwise) until("}) {") else emptyList()
            val body = until("}")
            val relation = body.firstNotNullOfOrNull { ACT.matchEntire(it)?.groupValues?.get(1) }
                ?: throw BadArguments("$CANT a once with no act in it")
            val tried = call(evidence.act(relation), scope).removePrefix("call(").removeSuffix(")")
            val waited = giveUp.takeIf { it.isNotEmpty() }?.let { ", giveUp = $it" }.orEmpty()
            if (!otherwise) return emit(depth, "once($tried$waited)")
            emit(depth, "once($tried$waited, otherwise = { why ->")
            instead.forEach { emit(depth + 1, otherwiseCall(it)) }
            emit(depth, "})")
        }

        /** An Otherwise's act as its call: a reasoned one sends the words the reasoner writes as it asks. */
        private fun otherwiseCall(line: String): String {
            val reasoned = REASONED.matchEntire(line)?.groupValues?.get(1)
            val act = ACT.matchEntire(line)?.groupValues?.get(1)
            return when {
                reasoned != null -> call(evidence.said(opening(reasoned)), scope, words = reasoned)
                act != null -> call(evidence.act(act), scope)
                else -> throw BadArguments("$CANT \"$line\" is no word a bare watch knows")
            }
        }

        /** The lines up to the one that is [end], which is passed over. */
        private fun until(end: String): List<String> {
            val taken = lines.drop(at).takeWhile { it != end }
            at += taken.size + 1
            return taken
        }
    }

    private companion object {
        const val INDENT = "    "
        const val ITEM = "item"
        const val MEMBER = "member"
        val UNITS = listOf("minutes", "seconds")
        val WATCH = Regex("""^watch\(until = \{ at\("(\w+)"\) \{ (.+) \} \}\) \{$""")
        val HAS = Regex("""^has \{ (.+) \}$""")
        val LEAVE = Regex("""^leaveWhere\(("(?:[^"\\]|\\.)*")\) \{ (.+) \}$""")
        val ONCE = Regex("""^once\("([^"]+)"(?:, giveUp = ([^,)]+))?(, otherwise = \{ why ->|\) \{)$""")
        val ACT = Regex("""^act\("([^"]+)".*""")
        val REASONED = Regex("""^act\(intent = (".*")\)$""")
        const val HEAD = "// Made by artie generate --bare from %s and %s (spec/ideas/bare-runs.md, \"Rails\"):\n" +
            "// the kept script's watch, its words kept, each act brought down to the call it made to the " +
            "service, at a\n// fixed address. No entry, no forms, no validators, no reasons for absence: " +
            "where the kept script waits for a\n// form, this tries the call, and the service's refusal is the " +
            "only gate."
    }
}

/** A reasoned step's words up to where the reason it was told goes, as the step said them. */
private fun opening(literal: String): String = literal.removeSurrounding("\"").substringBefore("\${why")
    .replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\")

/**
 * The kept script's [facts] (`"name" eq value; "rel" has value`) as a condition on the answer [receiver] reads:
 * a relation's profile prefix left off, as the service names the member the gateway embedded.
 */
private fun condition(facts: String, receiver: String): String {
    val parts = facts.split("; ").map { fact ->
        val (name, verb, value) = FACT.matchEntire(fact)?.destructured
            ?: throw BadArguments("$CANT \"$fact\" is no fact a bare watch knows")
        val member = "it[\"${name.substringAfter(':')}\"]"
        if (verb == "eq") "$member.text == $value" else "$member has $value"
    }
    val single = parts.singleOrNull()
    return single?.replaceFirst("it[", "$receiver[") ?: "$receiver.let { ${parts.joinToString(" && ")} }"
}

private val FACT = Regex("""^"([^"]+)" (eq|has) (.+)$""")
