package com.artie.tolls.domain

import com.artie.heart.ResourceNotFoundException

/** The toll rules, in one place: amounts in cents. */
object Rules {
    const val OPENING_DEPOSIT = 2_500L
    const val LOW_BALANCE = 1_000L
    const val CENTS = 100L

    /** Off-peak is 80 % of the peak fare. */
    const val OFF_PEAK_PERCENT = 80L
    const val PERCENT = 100L

    private const val HARBOR_BRIDGE = 700L
    private const val NORTH_TUNNEL = 650L
    private const val TURNPIKE_EXIT_9 = 325L
    private const val TWO_AXLES = 2L
    private const val FIVE_AXLES = 5L

    /** A car's fare at peak, by plaza. */
    val peakCarFare = mapOf(
        "harbor-bridge" to HARBOR_BRIDGE,
        "north-tunnel" to NORTH_TUNNEL,
        "turnpike-exit-9" to TURNPIKE_EXIT_9,
    )

    /** What a vehicle pays, as a multiple of a car's fare. */
    val classMultiple = mapOf(
        "car" to 1L,
        "two-axle-truck" to TWO_AXLES,
        "five-axle-truck" to FIVE_AXLES,
    )

    /** The fare for a trip at [plaza] by a vehicle of [vehicleClass] at [time] ("peak" or "off-peak"). */
    fun fare(plaza: String, vehicleClass: String, time: String): Long {
        val car = checkNotNull(peakCarFare[plaza]) { "no plaza $plaza" }
        val atTime = if (time == "peak") car else car * OFF_PEAK_PERCENT / PERCENT
        return atTime * checkNotNull(classMultiple[vehicleClass]) { "no vehicle class $vehicleClass" }
    }

    /** Cents as dollars, as a person reads them: 2500 is "25.00", -1000 is "-10.00". */
    fun dollars(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val whole = Math.abs(cents)
        return "$sign${whole / CENTS}.${(whole % CENTS).toString().padStart(2, '0')}"
    }
}

/** An account's identity: who holds it. Not an event: it is set up when the account is applied for. */
data class AccountSetup(val id: Long, val holder: String)

/** Where an account is: what may be done depends on this alone. */
enum class Phase { NEW, ACTIVE, LOW_BALANCE, SUSPENDED, CLOSED }

/** 404: no account with that id. */
class AccountNotFoundException(id: Long) : ResourceNotFoundException("No account with id $id")

/** The accounts that have been set up. */
interface AccountDirectory {
    suspend fun find(id: Long): AccountSetup?

    suspend fun all(): List<AccountSetup>
}
