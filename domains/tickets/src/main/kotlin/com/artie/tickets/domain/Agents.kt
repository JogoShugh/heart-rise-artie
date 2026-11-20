package com.artie.tickets.domain

/** A support agent: the priorities they handle, and whether they are on duty now. */
data class Agent(val name: String, val handles: List<String>, val onDuty: Boolean)

/**
 * The desk's agents, another resource beside the tickets (/agents). The assign
 * act takes its choices from them (HAL-SCHEMA-FORMS-HR Rule 8): those who handle
 * the ticket's priority and are on duty.
 */
object Agents {
    val all: List<Agent> = listOf(
        Agent("priya", listOf("low", "normal", "high", "urgent"), onDuty = true),
        Agent("marco", listOf("normal", "high"), onDuty = true),
        Agent("lena", listOf("high", "urgent"), onDuty = false),
        Agent("tom", listOf("low", "normal"), onDuty = true),
    )

    /** The agents who handle [priority], or every agent when it is null. */
    fun handling(priority: String?): List<Agent> = all.filter { priority == null || priority in it.handles }

    /** An agent as the /agents resource shows them. */
    fun json(agent: Agent): Map<String, Any> =
        linkedMapOf("name" to agent.name, "handles" to agent.handles, "onDuty" to agent.onDuty)
}
