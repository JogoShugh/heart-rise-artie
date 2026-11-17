package com.artie.tolls.declaration

import com.artie.heart.ActInput
import com.artie.tolls.domain.AccountEvent
import com.artie.tolls.domain.AccountService
import com.artie.tolls.domain.AccountService.Companion.requireDeposit
import com.artie.tolls.domain.AccountService.Companion.requireNewTag
import com.artie.tolls.domain.AccountService.Companion.requireTrip
import com.artie.tolls.domain.AccountState
import com.artie.tolls.domain.Rules

/**
 * What each act records. The HEART library has already checked that the act
 * is offered and that its body satisfies the act's schema, its choices included.
 */
class AccountActs(private val accounts: AccountService) {

    /** Act [act] on [account], the state the client's validator was checked against. */
    suspend fun perform(account: AccountState, act: String, input: ActInput): AccountState =
        accounts.record(account) { event(act, input, it) }

    private fun event(act: String, input: ActInput, account: AccountState): AccountEvent =
        when (act) {
            "addFunds" -> (checkNotNull(input.bind(AddFunds::class.java).amount) * Rules.CENTS).let {
                requireDeposit(account, it)
                AccountEvent.FundsAdded(it)
            }
            "addTag" -> checkNotNull(input.bind(AddTag::class.java).tag).let {
                requireNewTag(account, it)
                AccountEvent.TagAdded(it)
            }
            "recordTrip" -> input.bind(RecordTrip::class.java).let {
                val fare = Rules.fare(it.plaza!!, it.vehicleClass!!, it.time!!)
                AccountEvent.TripCharged(it.tag!!, it.plaza, it.vehicleClass, it.time, fare)
            }
            "reportTagLost" -> AccountEvent.TagLost(checkNotNull(input.bind(ReportTagLost::class.java).tag))
            "disputeToll" -> checkNotNull(input.bind(DisputeToll::class.java).sequenceNo).let {
                requireTrip(account, it)
                AccountEvent.TripRefunded(it)
            }
            "closeAccount" -> AccountEvent.AccountClosed(account.balance)
            else -> error("no act $act")
        }
}
