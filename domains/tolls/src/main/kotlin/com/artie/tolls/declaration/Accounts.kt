package com.artie.tolls.declaration

import com.artie.heart.Act
import com.artie.heart.Finding
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Options
import com.artie.heart.Pick
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.tolls.domain.AccountState
import com.artie.tolls.domain.Phase
import com.artie.tolls.domain.Rules

/** An account, declared once for the HEART library. */
object Accounts {

    /**
     * HAL-SCHEMA-FORMS-HR Rule 8: the tag is one of the account's own tags, at /tags, leaving out the lost ones.
     */
    private val aTagThatPays = mapOf(
        "tag" to Options.fromState(
            "/tags",
            Pick(value = "id", prompt = "id", where = mapOf("lost" to false), maxItems = 1),
        ),
    )

    private val open = listOf("addFunds", "addTag", "recordTrip", "reportTagLost", "disputeToll", "closeAccount")

    val type = ResourceType(
        collection = "accounts",
        profile = TollsProfile.profile,
        acts = listOf(
            Act.of("addFunds", "add-funds", AddFunds::class.java),
            Act.of("addTag", "add-tag", AddTag::class.java),
            Act.of("recordTrip", "record-trip", RecordTrip::class.java).withOptions(aTagThatPays),
            Act.of("reportTagLost", "report-tag-lost", ReportTagLost::class.java).withOptions(aTagThatPays),
            Act.of("disputeToll", "dispute-toll", DisputeToll::class.java),
            Act.of("closeAccount", "close-account", NoInput::class.java),
        ),
        offers = Offers(
            keys = Phase.entries,
            offered = { phase ->
                when (phase) {
                    Phase.NEW -> listOf("addFunds")
                    Phase.ACTIVE, Phase.LOW_BALANCE -> open
                    Phase.SUSPENDED -> listOf("addFunds", "disputeToll")
                    Phase.CLOSED -> emptyList()
                }
            },
            whyAbsent = TollsProfile::whyAbsent,
        ),
        state = StateView<AccountState, Phase>(
            keyOf = { it.phase() },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = ::body,
        ),
        finding = Finding(
            noun = "account",
            search = Named("searchAccounts", "search-accounts"),
            lookup = Named("getAccount", "get-account"),
            nameDescription = "Part of the holder's name; case is ignored",
            idTitle = "Account number",
            idDescription = "The account's number",
        ),
    )

    val layers = Layers(type)

    private fun body(account: AccountState): Map<String, Any?> =
        linkedMapOf(
            "id" to account.id,
            "holder" to checkNotNull(account.setup).holder,
            "phase" to account.phase().name,
            "balance" to Rules.dollars(account.balance),
            "lowBalanceBelow" to Rules.dollars(Rules.LOW_BALANCE),
            "refunded" to Rules.dollars(account.refunded),
            "tags" to account.tags.map { mapOf("id" to it.id, "lost" to it.lost) },
            "statement" to account.entries.map { entry ->
                linkedMapOf(
                    "sequenceNo" to entry.sequenceNo,
                    "kind" to entry.kind,
                    "amount" to Rules.dollars(entry.cents),
                    "tag" to entry.tag,
                    "plaza" to entry.plaza,
                    "vehicleClass" to entry.vehicleClass,
                    "time" to entry.time,
                    "refunded" to entry.refunded,
                ).filterValues { it != null }
            },
        )
}
