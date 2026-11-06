package com.artie.heart.sims.library

import com.artie.heart.Act
import com.artie.heart.ActInput
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Hypermedia
import com.artie.heart.Layers
import com.artie.heart.Listing
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Options
import com.artie.heart.Pick
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.SearchField
import com.artie.heart.ServingLimits
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler

/**
 * Riverside Public Library, the library of "The Web That Wasn't", declared once for heart-core: a member's account,
 * found by library card number and linking each book out, and the loans, each offering renew, return and reporting
 * a problem as its state allows, and saying why when it doesn't. The same declaration serves
 * HTML to a person in a browser, HAL-FORMS, HAL Schema Forms, a profile and an OpenAPI twin. Ana Torres signs in
 * with her library card number and PIN; an agent, with the library's Bearer token.
 */
class HeartLibrary(private val ledger: Ledger) {
    private val type = ResourceType(
        collection = "loans",
        profile = PROFILE,
        acts = listOf(
            Act("renew", "renew", NO_INPUT),
            Act("report-problem", "report-problem", REPORT_INPUT).withOptions(PROBLEM_KIND),
            Act("return", "return", NO_INPUT),
        ),
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
        perform = { current, act, input -> Acted(ledger.replace(current, acted(current, act, input))) },
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
        load = { id -> ledger.account(id) ?: throw ResourceNotFoundException("no account $id") },
        perform = { current, _, _ -> Acted(current) },
        find = { name, id ->
            ledger.accounts()
                .filter { id == null || it.card == id }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .map { Found(it.card, it.name) }
        },
    )

    // The company groups: each member found by the company they work for, five to a page, linking their books.
    private val memberType = ResourceType(
        collection = "members",
        profile = PROFILE,
        acts = emptyList(),
        offers = Offers(keys = listOf(OPEN), offered = { emptyList() }, whyAbsent = { _, _ -> "" }),
        state = StateView<Account, String>(
            keyOf = { OPEN },
            idOf = { it.card },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.card, "name" to it.name, "company" to it.company, "booksOut" to it.loans.size) },
            hypermediaOf = { member ->
                Hypermedia(links = mapOf("item" to member.loans.map { mapOf("href" to "/loans/${it.id}", "title" to it.title) }))
            },
        ),
        finding = Finding(
            noun = "member",
            search = Named("searchMembers", "search-members"),
            lookup = Named("getMember", "get-member"),
            collectionPath = "/members",
            fields = listOf(
                SearchField("company", "Company", "Part of the company's name; case is ignored"),
                SearchField(
                    "id", "Library card number", "The number on the back of the library card",
                    mapOf("type" to "integer", "minimum" to 1),
                ),
            ),
        ),
    )

    private val members = HeartResource(
        layers = Layers(memberType),
        load = { id -> ledger.account(id)?.takeIf { it.company != null } ?: throw ResourceNotFoundException("no member $id") },
        perform = { current, _, _ -> Acted(current) },
        search = { values ->
            val company = values["company"]
            ledger.accounts()
                .filter { it.company != null && (company == null || it.company.contains(company, ignoreCase = true)) }
                .map { Found(it.card, it.name) }
        },
    )

    val handler: HttpHandler = HeartHttp(
        listOf(accounts, members, loans),
        Credentials.parse(users = "$CARD:$PIN_HASH", tokens = Tokens.sha256(TOKEN)),
        // The affordance link: each plain JSON answer names the set its state offers, for OpenAPI clients that opt in.
        HeartSettings(
            title = "Riverside Public Library",
            realm = PROFILE.name,
            limits = ServingLimits(pageSize = PAGE_SIZE, affordanceLinks = true),
            listings = listOf(Listing(PROBLEM_KINDS_PATH, "kinds") { PROBLEM_KINDS }),
        ),
    )

    /** The acts each standing offers, in the order of their relations, so every rung lists them alike. */
    private fun offered(standing: String): List<String> = when (standing) {
        Loan.RENEWABLE -> listOf("renew", "report-problem", "return")
        Loan.RETURNED -> emptyList()
        else -> listOf("report-problem", "return")
    }

    /** The loan after [act]. A problem's kind is one of the library's list: heart-core checked it against it. */
    private fun acted(loan: Loan, act: String, input: ActInput): Loan = when (act) {
        "renew" -> loan.renewed()
        "report-problem" -> loan.reportedNow(Report(input.json["problem"].asText(), input.json["comment"]?.asText()))
        else -> loan.returnedNow()
    }

    private fun body(loan: Loan): Map<String, Any?> = linkedMapOf(
        "id" to loan.id,
        "title" to loan.title,
        "author" to loan.author,
        "due" to loan.due.toString(),
        "renewals" to loan.renewals,
        "standing" to loan.standing,
    ) + reported(loan)

    /** The problem last reported with [loan], when there is one: a loan nobody reported on reads as it did. */
    private fun reported(loan: Loan): Map<String, Any?> = loan.reported?.let { report ->
        val said = linkedMapOf("problem" to report.problem, "comment" to report.comment).filterValues { it != null }
        mapOf("reported" to said)
    }.orEmpty()

    companion object {
        /** Ana Torres's library card number; her PIN is 1234, kept only as its bcrypt hash. */
        const val CARD = "21000451"
        private const val PIN_HASH = "\$2y\$10\$poLdiGaLqp2v7aGDsC72r.ToVv5GgwfArGnIGmJB7fkFhQiPXgwYy"

        /** The Bearer token the library admits, for an agent working for Ana. */
        const val TOKEN = "library-token"

        private const val NO_INPUT = """{"type": "object", "properties": {}}"""

        /** Reporting a problem: its kind, one of the library's list, and a comment if the member has one. */
        private const val REPORT_INPUT = """{"type": "object", "required": ["problem"], "properties": {""" +
            """"problem": {"type": "string", "title": "Problem", "description": "What is wrong with the book"}, """ +
            """"comment": {"type": "string", "maxLength": 200, "title": "Comment", """ +
            """"description": "Anything the desk should know, in a sentence or two"}}}"""

        /** Where the library lists the kinds of problem a member can report. */
        private const val PROBLEM_KINDS_PATH = "/problem-kinds"

        /** The kinds of problem a member can report: the library's own list, changed without changing a form. */
        private val PROBLEM_KINDS = listOf(
            mapOf("value" to "damaged", "prompt" to "It's damaged"),
            mapOf("value" to "missing-pages", "prompt" to "Pages are missing"),
            mapOf("value" to "wrong-book", "prompt" to "It's the wrong book"),
            mapOf("value" to "lost", "prompt" to "I can't find it"),
        )

        /** HAL-SCHEMA-FORMS-HR Rule 8: the problem is one of the kinds the library lists, another resource. */
        private val PROBLEM_KIND = mapOf(
            "problem" to Options.fromLink(
                href = PROBLEM_KINDS_PATH,
                items = "/kinds",
                pick = Pick(maxItems = 1),
                resolve = { _, _ -> PROBLEM_KINDS },
            ),
        )

        /** The most results one page of a search lists. */
        private const val PAGE_SIZE = 5

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
                    "report-problem", AlpsType.UNSAFE, "Report a problem",
                    "Tells the library what is wrong with the book: one of the kinds it lists, and a comment.",
                    "Offered while the book is out; the kind must be one the library lists at /problem-kinds.",
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
                    "search-members", AlpsType.SAFE, "Find members by company",
                    "Lists the members in the company group whose name has the text, five to a page.",
                    "A safe search; each result links to the member, titled with their name; each page links the next.",
                ),
                Relation(
                    "get-member", AlpsType.SAFE, "Get a member of a company group by library card number",
                    "Goes to one member of a company group, given the number on their library card.",
                    "A safe lookup; the member links each book they have out, titled with the book.",
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
