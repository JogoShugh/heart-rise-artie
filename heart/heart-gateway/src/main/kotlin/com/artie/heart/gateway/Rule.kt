package com.artie.heart.gateway

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode

/**
 * One of a reader's rules: when every condition holds, the acts it names
 * are absent, for the reasons it gives. A condition is a JSON value at a
 * JSON Pointer, equal: into the answer (`/draft`), or into one of its facts
 * (`repository:/permissions/push`). A JSON null matches a member that is
 * there and null, as GitHub's `mergeable` is while it is still working it
 * out; a member that isn't there matches nothing.
 */
class Rule(private val conditions: Map<String, JsonNode>, val absent: AbsenceReasons) {

    fun holds(answer: Answer): Boolean = conditions.all { (at, value) ->
        val member = at(answer, at)
        !member.isMissingNode && member == value
    }

    private fun at(answer: Answer, term: String): JsonNode {
        val fact = term.substringBefore(':', missingDelimiterValue = "")
        val body = if (fact.isEmpty()) answer.body else answer.facts[fact]
        return body?.at(JsonPointer.compile(term.substringAfter(':'))) ?: MissingNode.getInstance()
    }

    companion object {
        /**
         * [allowed], with [rules] applied in order: each rule that holds makes its
         * acts absent, and the first reason given for an act is the one it keeps.
         */
        fun applied(allowed: Allowed, rules: List<Rule>, answer: Answer): Allowed =
            rules.filter { it.holds(answer) }.fold(allowed) { now, rule ->
                Allowed(now.operations - rule.absent.keys, rule.absent + now.reasons)
            }
    }
}
