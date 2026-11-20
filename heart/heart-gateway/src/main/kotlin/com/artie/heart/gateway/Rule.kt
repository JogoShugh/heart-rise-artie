package com.artie.heart.gateway

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode

/**
 * One condition of a rule, or of a resource file's `skip`: a term, a JSON
 * Pointer into the answer (`/draft`) or into one of its facts
 * (`repository:/permissions/push`), and a test:
 * - a JSON value: the member is there and equal to it; `null` matches a
 *   member that is there and null, as GitHub's `mergeable` is while it is
 *   still working it out;
 * - `{present: true}` or `{present: false}`: the member is there, or not;
 * - `{not: value}`: the member is missing, or not equal to the value;
 * - `{same-as: term}` and `{not-same-as: term}`: equal to another term, or
 *   not, as an issue's author to the caller: `{same-as: "user:/login"}`.
 */
class Condition(private val term: String, private val test: JsonNode) {

    fun holds(answer: Answer): Boolean {
        val member = at(answer, term)
        val operator = test.takeIf { it.isObject && it.size() == 1 }?.fieldNames()?.next().orEmpty()
        val operand = test.path(operator)
        return when (operator) {
            "present" -> !member.isMissingNode == operand.asBoolean()
            "not" -> member.isMissingNode || member != operand
            "same-as" -> !member.isMissingNode && member == at(answer, operand.asText())
            "not-same-as" -> member.isMissingNode || member != at(answer, operand.asText())
            else -> !member.isMissingNode && member == test
        }
    }

    private fun at(answer: Answer, term: String): JsonNode {
        val fact = term.substringBefore(':', missingDelimiterValue = "")
        val body = if (fact.isEmpty()) answer.body else answer.facts[fact]
        return body?.at(JsonPointer.compile(term.substringAfter(':'))) ?: MissingNode.getInstance()
    }

    companion object {
        /** Each member of [node] a condition: the term, and its test. */
        fun all(node: JsonNode): List<Condition> =
            node.fields().asSequence().map { Condition(it.key, it.value) }.toList()
    }
}

/**
 * One of a reader's rules: when every condition holds, the acts it names
 * are absent, for the reasons it gives.
 */
class Rule(private val conditions: List<Condition>, val absent: AbsenceReasons) {

    fun holds(answer: Answer): Boolean = conditions.all { it.holds(answer) }

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
