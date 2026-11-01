package com.artie.heart.sims.deploy

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded

/** A region an app can be deployed to in one environment, and whether it takes deployments now. */
data class Region(val name: String, val label: String, val available: Boolean)

/** What happens to an app, in order: its state is the fold of these. */
sealed interface AppEvent {
    /** The app came to the service, idle, with nothing deployed. */
    data class Imported(val name: String) : AppEvent

    /** The regions each environment offers the app changed. */
    data class RegionsSet(val regions: Map<String, List<Region>>) : AppEvent

    /** A release of the app was built: [status] says whether it succeeded. */
    data class ReleasePublished(val version: String, val status: String) : AppEvent

    /** A deployment of [release] to [region] of [environment] began. */
    data class DeploymentStarted(val release: String, val environment: String, val region: String) : AppEvent
}

/** An app as its events leave it. */
data class AppState(
    val id: Long,
    val name: String,
    val status: String,
    val environment: String?,
    val region: String?,
    val release: String?,
    val regions: Map<String, List<Region>>,
    val version: Int,
) {
    /** The app as its representation shows it. */
    fun body(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "status" to status,
        "environment" to environment,
        "region" to region,
        "release" to release,
        "regions" to regions.mapValues { (_, listed) ->
            listed.map { mapOf("name" to it.name, "label" to it.label, "available" to it.available) }
        },
    )

    companion object {
        const val IDLE = "idle"
        const val DEPLOYING = "deploying"

        fun empty(id: Long) = AppState(id, "", IDLE, null, null, null, emptyMap(), 0)

        fun apply(state: AppState, recorded: Recorded<AppEvent>): AppState {
            val next = when (val event = recorded.event) {
                is AppEvent.Imported -> state.copy(name = event.name)
                is AppEvent.RegionsSet -> state.copy(regions = event.regions)
                is AppEvent.ReleasePublished -> state
                is AppEvent.DeploymentStarted -> state.copy(
                    status = DEPLOYING, release = event.release, environment = event.environment, region = event.region,
                )
            }
            return next.copy(version = recorded.sequenceNo)
        }
    }
}

/** How an app is event-sourced. */
val APPS = EventSourcing(
    stream = "app",
    types = mapOf(
        "Imported" to AppEvent.Imported::class.java,
        "RegionsSet" to AppEvent.RegionsSet::class.java,
        "ReleasePublished" to AppEvent.ReleasePublished::class.java,
        "DeploymentStarted" to AppEvent.DeploymentStarted::class.java,
    ),
    stateClass = AppState::class.java,
    empty = AppState::empty,
    apply = AppState::apply,
)

/** The commands an app takes, each decided from its state into events. Pure. */
object AppDecisions {
    fun import(name: String): List<AppEvent> = listOf(AppEvent.Imported(name))

    fun setRegions(regions: Map<String, List<Region>>): List<AppEvent> = listOf(AppEvent.RegionsSet(regions))

    fun publish(version: String, status: String): List<AppEvent> = listOf(AppEvent.ReleasePublished(version, status))

    /** Starts a deployment; which values may be chosen, the declaration's choices have already checked. */
    fun deploy(release: String, environment: String, region: String): List<AppEvent> =
        listOf(AppEvent.DeploymentStarted(release, environment, region))
}
