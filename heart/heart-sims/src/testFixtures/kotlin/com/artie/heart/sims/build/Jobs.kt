package com.artie.heart.sims.build

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded

/** What happens to a job, in order: its state is the fold of these. */
sealed interface JobEvent {
    /** The job's owner wrote its parameters: the JSON Schema of a build's parameters, as text. */
    data class Configured(val name: String, val parameters: String) : JobEvent

    /** A build was asked for with [parameters]; it waits in the queue as [queueId]. */
    data class BuildQueued(val queueId: Long, val parameters: Map<String, Any?>) : JobEvent
}

/** A job as its events leave it: its name, its parameters, the builds waiting, and its version. */
data class JobState(
    val id: Long,
    val name: String,
    val parameters: String,
    val queued: List<Long>,
    val version: Int,
) {
    /** The job as its representation shows it. */
    fun body(): Map<String, Any?> = mapOf("id" to id, "name" to name, "queued" to queued.size)

    companion object {
        fun empty(id: Long) = JobState(id, "", "{}", emptyList(), 0)

        fun apply(state: JobState, recorded: Recorded<JobEvent>): JobState {
            val next = when (val event = recorded.event) {
                is JobEvent.Configured -> state.copy(name = event.name, parameters = event.parameters)
                is JobEvent.BuildQueued -> state.copy(queued = state.queued + event.queueId)
            }
            return next.copy(version = recorded.sequenceNo)
        }
    }
}

/** How a job is event-sourced. */
val JOBS = EventSourcing(
    stream = "job",
    types = mapOf("Configured" to JobEvent.Configured::class.java, "BuildQueued" to JobEvent.BuildQueued::class.java),
    stateClass = JobState::class.java,
    empty = JobState::empty,
    apply = JobState::apply,
)

/**
 * The commands a job takes, each decided from its state into events, or
 * refused. Pure: nothing here reads or writes a store.
 */
object JobDecisions {
    /** Writes a job's parameters; a job that has none yet is brought into being by it. */
    fun configure(name: String, parameters: String): List<JobEvent> = listOf(JobEvent.Configured(name, parameters))

    /** Queues a build with [parameters], as queue item [queueId]. */
    fun build(queueId: Long, parameters: Map<String, Any?>): List<JobEvent> =
        listOf(JobEvent.BuildQueued(queueId, parameters))
}
