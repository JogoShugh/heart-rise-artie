package com.artie.heart.sims.build

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import java.util.concurrent.atomic.AtomicLong

/**
 * A narrow Jenkins, event-sourced: jobs whose owners write their parameters,
 * and builds queued with them. A job's parameters are the schema of its
 * build act, so each job is declared as a type of its own, from the read
 * side's [JobDefinitions]; the service is declared again whenever a job is
 * configured. Acts run as commands: decided into events, stored at the
 * version they were decided from (a lost race is a 412), then projected.
 */
class BuildService(private val store: EventStore = InMemoryEventStore()) {
    private val definitions = JobDefinitions()
    private val jobIds = AtomicLong()
    private val queueIds = AtomicLong()
    private val mapper = ObjectMapper()

    @Volatile
    private var declared: HttpHandler? = null

    /** The service as declared from the jobs there are now. */
    val handler: HttpHandler = { request -> (declared ?: declare().also { declared = it })(request) }

    /** Back to the seed: every stream forgotten, then the seed's jobs configured. */
    suspend fun reset() {
        store.forget(JOBS.stream)
        definitions.clear()
        jobIds.set(0)
        queueIds.set(0)
        declared = null
        SEED.forEach { (name, parameters) -> configure(name, parameters) }
    }

    /** The owner writes job [name]'s parameters; a job not configured before is brought into being. */
    suspend fun configure(name: String, parameters: String) {
        val id = definitions.named(name)?.id ?: jobIds.incrementAndGet()
        record(id, store.nextSequenceNo(JOBS, id), JobDecisions.configure(name, parameters))
        declared = null
    }

    private suspend fun record(id: Long, from: Int, events: List<JobEvent>): JobState {
        events.forEachIndexed { offset, event ->
            store.append(JOBS, id, from + offset, event)
            definitions.project(id, event)
        }
        return store.state(JOBS, id)
    }

    private fun declare(): HttpHandler = HeartHttp(
        definitions.all().map(::resourceOf),
        Credentials.parse(users = "", tokens = Tokens.sha256(TOKEN)),
        HeartSettings(title = "Build", realm = PROFILE.name),
    )

    private fun resourceOf(job: JobDefinition) = HeartResource(
        layers = Layers(typeOf(job)),
        perform = { state, _, input ->
            val parameters = mapper.convertValue(input.json, Map::class.java).mapKeys { "${it.key}" }
            Acted(record(state.id, state.version + 1, JobDecisions.build(queueIds.incrementAndGet(), parameters)))
        },
        loadAt = { store.state(JOBS, job.id) },
    )

    private fun typeOf(job: JobDefinition) = ResourceType(
        collection = "job-${job.name}",
        profile = PROFILE,
        acts = listOf(Act("build", "build", job.parameters)),
        offers = Offers(keys = listOf(READY), offered = { listOf("build") }, whyAbsent = { _, _ -> "" }),
        state = StateView<JobState, String>(
            keyOf = { READY },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { it.body() },
            itemPath = ItemPath("/jobs/${job.name}", emptyMap()),
        ),
    )

    companion object {
        /** The Bearer token the build service admits. */
        const val TOKEN = "build-token"

        private const val READY = "ready"

        /** The jobs a reset leaves: a name and its parameters. */
        private val SEED = listOf("nightly" to """{"type": "object"}""", "release" to """{"type": "object"}""")

        val PROFILE = Profile(
            name = "ci",
            title = "Build",
            doc = "A narrow Jenkins: jobs, their parameters, and the builds queued with them.",
            relations = listOf(
                Relation(
                    "build", AlpsType.UNSAFE, "Build",
                    "Queues a build of the job with these parameters.",
                    "Queues a build; the job's parameters are the schema of its body.",
                ),
            ),
        )
    }
}
