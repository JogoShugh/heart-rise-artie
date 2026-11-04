package com.artie.heart.sims.library

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
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler

/**
 * Riverside Public Library, the library of "The Web That Wasn't", declared once for heart-core: a member's account,
 * found by library card number and linking each book out, and the loans, each offering renew and return as its
 * state allows, and saying why when it doesn't. The same declaration serves
 * HTML to a person in a browser, HAL-FORMS, HAL Schema Forms, a profile and an OpenAPI twin. Ana Torres signs in
 * with her library card number and PIN; an agent, with the library's Bearer token.
 */
class HeartLibrary(private val ledger: Ledger) {
    private val type = ResourceType(
        collection = "loans",
        profile = PROFILE,
        acts = listOf(Act("renew", "renew", NO_INPUT), Act("return", "return", NO_INPUT)),
        offers = Offers(
            keys = listOf(Loan.RENEWABLE, Loan.ON_HOLD, Loan.AT_LIMIT, Loan.RETURNED),
            offered = ::offered,
            whyAbsent = { standing, _ -> WHY.getValue(standing) },
        ),
        state = StateView<Loan, String>(
            keyOf = { it.standing },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = ::body,
            keyField = "standing",
        ),
        finding = Finding(
            noun = "loan",
            search = Named("searchLoans", "search-loans"),
            lookup = Named("getLoan", "get-loan"),
            nameDescription = "Part of the book's title; case is ignored",
            idTitle = "Loan number",
            idDescription = "The loan's number, on the receipt",
        ),
    )

    private val loans = HeartResource(
        layers = Layers(type),
        load = { id -> ledger.get(id) ?: throw ResourceNotFoundException("no loan $id") },
        perform = { current, act, _ ->
            Acted(ledger.replace(current, if (act == "renew") current.renewed() else current.returnedNow()))
        },
        find = { title, id ->
            ledger.all()
                .filter { id == null || it.id == id }
                .filter { title == null || it.title.contains(title, ignoreCase = true) }
                .map { Found(it.id, it.title) }
        },
    )

    private val accountType = ResourceType(
        collection = "accounts",
        profile = PROFILE,
        acts = emptyList(),
        offers = Offers(keys = listOf(OPEN), offered = { emptyList() }, whyAbsent = { _, _ -> "" }),
        state = StateView<Account, String>(
            keyOf = { OPEN },
            idOf = { it.card },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.card, "name" to it.name, "booksOut" to it.loans.size) },
            // Each loan she has out, by its book's title: where a person or an agent goes from her account.
            hypermediaOf = { account ->
                val loans = account.loans.map { mapOf("href" to "/loans/${it.id}", "title" to it.title) }
                Hypermedia(links = mapOf("item" to loans))
            },
        ),
        finding = Finding(
            noun = "account",
            search = Named("searchAccounts", "search-accounts"),
            lookup = Named("getAccount", "get-account"),
            nameDescription = "Part of the member's name; case is ignored",
            idTitle = "Library card number",
            idDescription = "The number on the back of the library card",
        ),
    )

    private val accounts = HeartResource(
        layers = Layers(accountType),
        load = { id ->
            ledger.account().takeIf { it.card == id } ?: throw ResourceNotFoundException("no account $id")
        },
        perform = { current, _, _ -> Acted(current) },
        find = { name, id ->
            listOf(ledger.account())
                .filter { id == null || it.card == id }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .map { Found(it.card, it.name) }
        },
    )

    val handler: HttpHandler = HeartHttp(
        listOf(accounts, loans),
        Credentials.parse(users = "$CARD:$PIN_HASH", tokens = Tokens.sha256(TOKEN)),
        HeartSettings(title = "Riverside Public Library", realm = PROFILE.name),
    )

    /** The acts each standing offers, in the order of their relations, so every rung lists them alike. */
    private fun offered(standing: String): List<String> = when (standing) {
        Loan.RENEWABLE -> listOf("renew", "return")
        Loan.RETURNED -> emptyList()
        else -> listOf("return")
    }

    private fun body(loan: Loan): Map<String, Any?> = linkedMapOf(
        "id" to loan.id,
        "title" to loan.title,
        "author" to loan.author,
        "due" to loan.due.toString(),
        "renewals" to loan.renewals,
        "standing" to loan.standing,
    )

    companion object {
        /** Ana Torres's library card number; her PIN is 1234, kept only as its bcrypt hash. */
        const val CARD = "21000451"
        private const val PIN_HASH = "\$2y\$10\$poLdiGaLqp2v7aGDsC72r.ToVv5GgwfArGnIGmJB7fkFhQiPXgwYy"

        /** The Bearer token the library admits, for an agent working for Ana. */
        const val TOKEN = "library-token"

        private const val NO_INPUT = """{"type": "object", "properties": {}}"""

        /** An account's one standing: it offers no acts of its own, only its loans. */
        private const val OPEN = "open"

        /** Why a loan in each standing can't do what it doesn't offer. */
        private val WHY = mapOf(
            Loan.RENEWABLE to "It can be renewed and returned.",
            Loan.ON_HOLD to "A hold is waiting for this book, so it can't be renewed.",
            Loan.AT_LIMIT to "It has been renewed ${Loan.MOST_RENEWALS} times, the most allowed.",
            Loan.RETURNED to "It was returned.",
        )

        val PROFILE = Profile(
            name = "library",
            title = "Riverside Public Library",
            doc = "A member's loans at Riverside Public Library: renewing and returning books.",
            relations = listOf(
                Relation(
                    "renew", AlpsType.UNSAFE, "Renew",
                    "Keeps the book three weeks more.",
                    "Offered while the book is out, renewed fewer than three times, and no one's hold is waiting.",
                ),
                Relation(
                    "return", AlpsType.UNSAFE, "Return",
                    "Gives the book back to the library.",
                    "Offered while the book is out; nothing is offered after.",
                ),
                Relation(
                    "search-accounts", AlpsType.SAFE, "Find accounts by name",
                    "Lists the accounts whose member's name has the text.",
                    "A safe search; each result links to the account, titled with the member's name.",
                ),
                Relation(
                    "get-account", AlpsType.SAFE, "Get an account by library card number",
                    "Goes to one member's account, given the number on their library card.",
                    "A safe lookup; the account links each book the member has out, titled with the book.",
                ),
                Relation(
                    "search-loans", AlpsType.SAFE, "Find loans by title",
                    "Lists the loans whose book's title has the text.",
                    "A safe search; each result links to the loan, titled with the book.",
                ),
                Relation(
                    "get-loan", AlpsType.SAFE, "Get a loan by number",
                    "Goes to one loan, given its number.",
                    "A safe lookup; the result is the loan, with what may be done now.",
                ),
            ),
        )
    }
}
