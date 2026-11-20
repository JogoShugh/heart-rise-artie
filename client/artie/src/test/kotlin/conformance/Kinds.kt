package conformance

import heart.IllegalTransition
import heart.InvalidPayload
import heart.NotAChoice
import heart.NotAuthenticated
import heart.OutsideSchema
import heart.ReadRefused
import heart.RuleRefused
import heart.StaleAct
import heart.UnexpectedResponse
import heart.ValidatorRequired

/** A refusal as the conformance features name it: its kind, and the status that brought it (0 when none was sent). */
object Kinds {
    private const val HTTP_UNAUTHENTICATED = 401
    private const val HTTP_CONFLICT = 409
    private const val HTTP_STALE = 412
    private const val HTTP_UNPROCESSABLE = 422
    private const val HTTP_PRECONDITION = 428

    fun of(refusal: Exception): Pair<String, Int> = when (refusal) {
        is StaleAct -> "stale" to HTTP_STALE
        is IllegalTransition -> "not-offered" to HTTP_CONFLICT
        is ValidatorRequired -> "precondition-required" to HTTP_PRECONDITION
        is NotAuthenticated -> "not-authenticated" to HTTP_UNAUTHENTICATED
        is RuleRefused -> "rule-refused" to HTTP_UNPROCESSABLE
        is InvalidPayload -> "invalid-input" to HTTP_UNPROCESSABLE
        is NotAChoice -> "not-a-choice" to 0
        is OutsideSchema -> "outside-schema" to 0
        is UnexpectedResponse -> "failed" to refusal.code.toInt()
        is ReadRefused -> "failed" to refusal.code.toInt()
        else -> "failed" to 0
    }
}
