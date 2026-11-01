package com.artie.heart.sims.deploy

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.Listing
import com.artie.heart.Offers
import com.artie.heart.Options
import com.artie.heart.Pick
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler
import java.util.concurrent.atomic.AtomicLong

/**
 * A narrow cloud deploy service, event-sourced: apps, the regions each
 * environment offers them, their releases, and deployments. Deploying takes
 * its region from the app's own state, by the environment chosen, and its
 * release from the releases list, where only those that succeeded count.
 */
class DeployService(private val store: EventStore = InMemoryEventStore()) {
    private val directory = AppDirectory()
    private val appIds = AtomicLong()

    /** Back to the seed: every stream forgotten, then the app "web" imported, with no regions and no releases. */
    suspend fun reset() {
        store.forget(APPS.stream)
        directory.clear()
        appIds.set(0)
        import("web")
    }

    suspend fun import(name: String) {
        val id = appIds.incrementAndGet()
        record(id, 1, AppDecisions.import(name))
    }

    suspend fun setRegions(app: String, regions: Map<String, List<Region>>) = append(app, AppDecisions.setRegions(regions))

    suspend fun publish(app: String, version: String, status: String) = append(app, AppDecisions.publish(version, status))

    private suspend fun append(app: String, events: List<AppEvent>) {
        val id = idOf(app)
        record(id, store.nextSequenceNo(APPS, id), events)
    }

    private fun idOf(app: String) = directory.idOf(app) ?: throw ResourceNotFoundException("no app $app")

    private suspend fun record(id: Long, from: Int, events: List<AppEvent>): AppState {
        events.forEachIndexed { offset, event ->
            store.append(APPS, id, from + offset, event)
            directory.project(id, event)
        }
        return store.state(APPS, id)
    }

    private val apps = HeartResource(
        layers = Layers(type()),
        perform = { app, _, input ->
            val (release, environment, region) = listOf("release", "environment", "region").map { input.json.path(it).asText() }
            Acted(record(app.id, app.version + 1, AppDecisions.deploy(release, environment, region)))
        },
        loadAt = { variables -> store.state(APPS, idOf(variables.getValue("name"))) },
    )

    private val releases = Listing("/releases", "releases") { query -> directory.releasesOf(query["app"]).map(Release::json) }

    val handler: HttpHandler = HeartHttp(
        listOf(apps),
        Credentials.parse(users = "", tokens = Tokens.sha256(TOKEN)),
        HeartSettings(title = "Deploy", realm = PROFILE.name, listings = listOf(releases)),
    )

    private fun type() = ResourceType(
        collection = "apps",
        profile = PROFILE,
        acts = listOf(Act("deploy", "deploy", DEPLOY_SCHEMA).withOptions(mapOf("region" to REGION, "release" to release()))),
        offers = Offers(
            keys = listOf(AppState.IDLE, AppState.DEPLOYING),
            offered = { status -> if (status == AppState.IDLE) listOf("deploy") else emptyList() },
            whyAbsent = { _, _ -> "A deployment is in progress." },
        ),
        state = StateView<AppState, String>(
            keyOf = { it.status },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { it.body() },
            keyField = "status",
            itemPath = ItemPath("/apps/{name}", mapOf("name" to "/name")),
        ),
    )

    /** A release of this app that succeeded, from the releases list. */
    private fun release() = Options.fromLink(
        href = "/releases{?app}",
        templatePointers = mapOf("app" to "/name"),
        items = "/releases",
        pick = Pick(value = "version", prompt = "title", where = mapOf("status" to "succeeded"), maxItems = 1),
        resolve = { state, _ -> directory.releasesOf((state as AppState).name).map(Release::json) },
    )

    companion object {
        /** The Bearer token the deploy service admits. */
        const val TOKEN = "deploy-token"

        /** A region of the environment chosen, from the app's own state, that takes deployments now. */
        private val REGION = Options.fromState(
            "/regions/{environment}",
            Pick(value = "name", prompt = "label", where = mapOf("available" to true), maxItems = 1),
        )

        private const val DEPLOY_SCHEMA = """{"type": "object", "required": ["environment", "region", "release"],
            "properties": {"environment": {"type": "string", "enum": ["staging", "production"]},
            "region": {"type": "string"}, "release": {"type": "string"}}}"""

        val PROFILE = Profile(
            name = "cd",
            title = "Deploy",
            doc = "A narrow cloud deploy service: apps, their regions and releases, and deployments.",
            relations = listOf(
                Relation(
                    "deploy", AlpsType.UNSAFE, "Deploy",
                    "Deploys a release of the app to a region of an environment.",
                    "Offered while the app is idle; the app is deploying after.",
                ),
            ),
        )
    }
}
