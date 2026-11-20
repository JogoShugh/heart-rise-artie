package com.artie.heart.sims.build

import java.util.concurrent.ConcurrentHashMap

/** A job as the read side keeps it: its id in the store, its name, and its parameters. */
data class JobDefinition(val id: Long, val name: String, val parameters: String)

/**
 * The read side's job directory, a projection of [JobEvent.Configured]: each
 * job's id by name, and its parameters, from which its build form is declared.
 * Fed the events after they are stored; never read by the write side.
 */
class JobDefinitions {
    private val byName = ConcurrentHashMap<String, JobDefinition>()

    fun project(id: Long, event: JobEvent) {
        if (event is JobEvent.Configured) byName[event.name] = JobDefinition(id, event.name, event.parameters)
    }

    fun named(name: String): JobDefinition? = byName[name]

    fun all(): List<JobDefinition> = byName.values.sortedBy { it.id }

    fun clear() = byName.clear()
}
