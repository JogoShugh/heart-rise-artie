package com.artie.heart.sims.release

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Hypermedia
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.RuleRefusedException
import com.artie.heart.StateView
import com.artie.heart.Violation
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler
import java.time.Instant

/** An environment: the commit it serves, when it was promoted, and the smoke run that was cited for it. */
data class Environment(
    val id: Long,
    val name: String,
    val commit: String,
    val promotedAt: String? = null,
    val smokeRun: String? = null,
    val version: Int = 0,
)

/**
 * The Dev environment as a HEART service: `/environments/1`, Dev, offers `env:promote`, a commit and the smoke run
 * that passed for it, conditional on its ETag. Its rule refuses a commit whose smoke run did not pass every scenario.
 */
class DevEnvironment(commit: String, tokens: List<String> = listOf(TOKEN)) {
    @Volatile
    private var dev = Environment(DEV, "dev", commit)

    private val type = ResourceType(
        collection = "environments",
        profile = PROFILE,
        acts = listOf(Act("promote", "promote", PROMOTE_INPUT)),
        offers = Offers(keys = listOf(SERVING), offered = { listOf("promote") }, whyAbsent = { _, _ -> "" }),
        state = StateView<Environment, String>(
            keyOf = { SERVING },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "commit" to it.commit, "promotedAt" to it.promotedAt,
                    "standing" to SERVING,
                )
            },
            hypermediaOf = { env -> Hypermedia(links = env.smokeRun?.let { mapOf("env:smoke-run" to mapOf("href" to it)) }.orEmpty()) },
            keyField = "standing",
        ),
        finding = Finding(
            noun = "environment",
            search = Named("searchEnvironments", "search-environments"),
            lookup = Named("getEnvironment", "get-environment"),
            idTitle = "Environment number",
            idDescription = "Dev is 1",
        ),
    )

    private val resource = HeartResource(
        layers = Layers(type),
        load = { id -> dev.takeIf { it.id == id } ?: throw ResourceNotFoundException("no environment $id") },
        perform = { current, _, input ->
            val smoke = input.json["smoke"]
            val passed = smoke["passed"].asInt()
            val of = smoke["of"].asInt()
            if (passed != of) {
                throw RuleRefusedException(listOf(Violation("/smoke", "only $passed of $of smoke scenarios passed")))
            }
            dev = current.copy(
                commit = input.json["commit"].asText(),
                promotedAt = Instant.now().toString(),
                smokeRun = input.json["_links"]?.get("env:smoke-run")?.get("href")?.asText(),
                version = current.version + 1,
            )
            Acted(dev)
        },
        find = { name, _ -> listOf(dev).filter { name == null || it.name.contains(name) }.map { Found(it.id, it.name) } },
    )

    val handler: HttpHandler = HeartHttp(
        listOf(resource),
        Credentials.parse(users = "", tokens = tokens.joinToString(",") { Tokens.sha256(it) }),
        HeartSettings(title = "Environments", realm = PROFILE.name),
    )

    /** Dev as it is now. */
    fun dev(): Environment = dev

    companion object {
        const val DEV = 1L
        const val TOKEN = "env-token"
        private const val SERVING = "serving"

        private const val PROMOTE_INPUT = """{
  "type": "object",
  "required": ["commit", "smoke"],
  "properties": {
    "commit": {"type": "string", "minLength": 7, "title": "Commit", "description": "The commit to promote"},
    "smoke": {
      "type": "object", "required": ["passed", "of"], "title": "Smoke run",
      "properties": {"passed": {"type": "integer", "minimum": 0}, "of": {"type": "integer", "minimum": 1}}
    },
    "_links": {
      "type": "object", "additionalProperties": true,
      "properties": {"env:smoke-run": {"type": "object", "required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}}}}
    }
  }
}"""

        val PROFILE = Profile(
            name = "env",
            title = "Environments",
            doc = "Where a commit is deployed, and how it got there.",
            relations = listOf(
                Relation("promote", AlpsType.UNSAFE, "Promote a commit", "Deploys the commit to the environment, citing the smoke run that passed for it.", "Offered while the environment serves; refused unless every smoke scenario passed."),
                Relation("smoke-run", AlpsType.SAFE, "Smoke run", "The smoke run cited when the commit was promoted.", "Linked once a commit was promoted."),
                Relation("search-environments", AlpsType.SAFE, "Find environments", "Lists the environments.", "Always."),
                Relation("get-environment", AlpsType.SAFE, "Get an environment", "One environment, by its number.", "Always."),
            ),
        )
    }
}
