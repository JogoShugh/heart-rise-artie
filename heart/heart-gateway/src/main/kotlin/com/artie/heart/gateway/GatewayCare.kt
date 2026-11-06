package com.artie.heart.gateway

import java.time.Duration

/**
 * The care the gateway takes over each act and read, every kind by default (spec/ideas/off-the-rails.md). A trial
 * that measures what each kind costs may turn it off: [offers], reading what works out the acts offered now (a
 * repository, the caller), else every form is shown; [race], reading a resource again just before acting on it;
 * [reread], reading it again after, else the act's own answer is the state where it is the resource; and
 * [trustCopies], how long a copy is used without asking the upstream, none by default.
 */
data class GatewayCare(
    val offers: Boolean = true,
    val race: Boolean = true,
    val reread: Boolean = true,
    val trustCopies: Duration? = null,
) {
    override fun toString(): String = listOfNotNull(
        "no-offers".takeUnless { offers },
        "no-race".takeUnless { race },
        "no-reread".takeUnless { reread },
        trustCopies?.let { "trust-copies=${it.seconds}" },
    ).joinToString(", ").ifEmpty { "every kind" }

    companion object {
        /** Every kind of care: the gateway's default. */
        val EVERY = GatewayCare()

        /** The header a request names its care by, for itself alone. */
        const val HEADER = "X-Gateway-Care"

        private const val OFF_THE_RAILS_TRUST = 60L
        private val OFF_THE_RAILS = GatewayCare(offers = false, race = false, reread = false,
            trustCopies = Duration.ofSeconds(OFF_THE_RAILS_TRUST))

        /** The care that [words] name, comma separated; a word it doesn't know is refused, naming it. */
        fun parse(words: String): GatewayCare = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .fold(EVERY) { care, word ->
                when {
                    word == "off-the-rails" -> OFF_THE_RAILS
                    word == "no-offers" -> care.copy(offers = false)
                    word == "no-race" -> care.copy(race = false)
                    word == "no-reread" -> care.copy(reread = false)
                    word.startsWith(TRUST) && word.removePrefix(TRUST).toLongOrNull() != null ->
                        care.copy(trustCopies = Duration.ofSeconds(word.removePrefix(TRUST).toLong()))
                    else -> throw IllegalArgumentException(
                        "names no-offers, no-race, no-reread, trust-copies=SECONDS or off-the-rails, not $word",
                    )
                }
            }

        private const val TRUST = "trust-copies="

        private val holder = ThreadLocal<GatewayCare>()

        /** The care the request being served asked for, or every kind outside one. */
        fun now(): GatewayCare = holder.get() ?: EVERY

        /** Runs [block] taking [care], then restores the care before. */
        fun <T> during(care: GatewayCare, block: () -> T): T {
            val before = holder.get()
            holder.set(care)
            try {
                return block()
            } finally {
                holder.set(before)
            }
        }
    }
}
