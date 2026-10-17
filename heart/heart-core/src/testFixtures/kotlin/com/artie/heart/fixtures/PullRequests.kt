package com.artie.heart.fixtures

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.ItemPath
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.SearchField
import com.artie.heart.StateView

/** A pull request: identified by its owner, its repository and its number, as GitHub does. */
data class PullRequest(val owner: String, val repo: String, val number: Long, val state: String, val version: Int)

/** A resource type whose item path is more than `/{collection}/{id}`. */
object PullRequests {
    const val TEMPLATE = "/repos/{owner}/{repo}/pulls/{number}"
    val POINTERS = mapOf("owner" to "/base/owner", "repo" to "/base/repo", "number" to "/number")

    val profile = Profile(
        name = "pulls",
        title = "Pull requests",
        doc = "Changes proposed to a repository.",
        relations = listOf(
            Relation("close", AlpsType.IDEMPOTENT, "Close", "Close the pull request.", "Closes it unmerged."),
            Relation("reopen", AlpsType.IDEMPOTENT, "Reopen", "Reopen the pull request.", "Opens it again."),
            Relation("list", AlpsType.SAFE, "List pull requests", "A repository's pull requests.", "Lists them."),
            Relation("get", AlpsType.SAFE, "Get a pull request", "One pull request, by number.", "Reads it."),
        ),
    )

    /** The fields of a search and a lookup of pull requests, as GitHub's list and get operations take them. */
    val FIELDS = listOf(
        SearchField("owner", "Owner", "The account that owns the repository"),
        SearchField("repo", "Repository", "The repository's name"),
        SearchField("number", "Number", "The pull request's number", mapOf("type" to "integer", "minimum" to 1)),
        SearchField("state", "State", "Open or closed", mapOf("type" to "string", "enum" to listOf("open", "closed"))),
    )

    /** Found as GitHub lists them: a repository's pull requests, filtered by state. */
    fun finding(collectionPath: String = "/repos/{owner}/{repo}/pulls", fields: List<SearchField> = FIELDS) = Finding(
        noun = "pull",
        search = Named("listPulls", "list"),
        lookup = Named("getPull", "get"),
        collectionPath = collectionPath,
        fields = fields,
    )

    fun type(
        itemPath: ItemPath = ItemPath(TEMPLATE, POINTERS),
        finding: Finding? = null,
    ): ResourceType<PullRequest, String> = ResourceType(
        collection = "pulls",
        profile = profile,
        acts = listOf(Act("close", "close", "{}"), Act("reopen", "reopen", "{}")),
        offers = Offers(
            keys = listOf("open", "closed"),
            offered = { if (it == "open") listOf("close") else listOf("reopen") },
            whyAbsent = { key, _ -> "The pull request is already $key." },
        ),
        state = StateView(
            keyOf = { it.state },
            idOf = { it.number },
            versionOf = { it.version },
            bodyOf = {
                mapOf("number" to it.number, "state" to it.state, "base" to mapOf("owner" to it.owner, "repo" to it.repo))
            },
            keyField = "state",
            itemPath = itemPath,
        ),
        finding = finding,
    )
}
