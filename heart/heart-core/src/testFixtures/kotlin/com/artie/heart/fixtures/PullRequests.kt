package com.artie.heart.fixtures

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Embedded
import com.artie.heart.Hypermedia
import com.artie.heart.Finding
import com.artie.heart.ItemPath
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.SearchField
import com.artie.heart.StateView

/**
 * A pull request: identified by its owner, its repository and its number, as GitHub does. Its [author]
 * and [reviewers], when it has them, are other resources its state holds; [nullAuthor] writes the author
 * as JSON null.
 */
data class PullRequest(
    val owner: String,
    val repo: String,
    val number: Long,
    val state: String,
    val version: Int,
    val author: Map<String, Any?>? = null,
    val reviewers: List<String> = emptyList(),
    val nullAuthor: Boolean = false,
) {
    /** The state as plain JSON values. */
    fun body(): Map<String, Any?> = buildMap {
        put("number", number)
        put("state", state)
        put("base", mapOf("owner" to owner, "repo" to repo))
        if (nullAuthor) put("author", null) else author?.let { put("author", it) }
        if (reviewers.isNotEmpty()) put("reviewers", reviewers.map { mapOf("login" to it) })
    }
}

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
            Relation("split", AlpsType.UNSAFE, "Split", "Split off a new pull request.", "Makes a new pull request."),
            Relation("list", AlpsType.SAFE, "List pull requests", "A repository's pull requests.", "Lists them."),
            Relation("get", AlpsType.SAFE, "Get a pull request", "One pull request, by number.", "Reads it."),
            Relation("stack", AlpsType.SAFE, "Stack", "The pull requests split off it.", "Lists them."),
            Relation("comments", AlpsType.SAFE, "Comments", "The pull request's comments.", "Lists them."),
            Relation("author", AlpsType.SAFE, "Author", "Who wrote the pull request.", "Reads them."),
            Relation("reviewers", AlpsType.SAFE, "Reviewers", "Who is asked to review it.", "Reads them."),
            Relation("organization", AlpsType.SAFE, "Organization", "The author's organization.", "Reads it."),
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
        subCollections: Map<String, String> = emptyMap(),
        embedded: List<Embedded> = emptyList(),
        hypermedia: Hypermedia = Hypermedia.NONE,
    ): ResourceType<PullRequest, String> = ResourceType(
        collection = "pulls",
        profile = profile,
        // Closing and reopening are idempotent: their relations say so, and they are PUTs.
        acts = listOf(
            Act("close", "close", "{}", method = Act.PUT),
            Act("reopen", "reopen", "{}", method = Act.PUT),
            Act("split", "split", "{}"),
        ),
        offers = Offers(
            keys = listOf("open", "closed"),
            offered = { if (it == "open") listOf("close", "split") else listOf("reopen") },
            whyAbsent = { key, _ -> "The pull request is already $key." },
        ),
        state = StateView(
            keyOf = { it.state },
            idOf = { it.number },
            versionOf = { it.version },
            bodyOf = { it.body() },
            keyField = "state",
            itemPath = itemPath,
            hypermediaOf = { hypermedia },
        ),
        finding = finding,
        subCollections = subCollections,
        embedded = embedded,
    )
}
