package com.artie.heart.sims.rise

import org.http4k.core.Response

/**
 * How a party answers the replies it was asked on (RISE): [Answerer], by hand, or a HEART client run inside the
 * party, as the release desk's parties do (client/artie-release, release-desk.feature).
 */
fun interface Answers {
    /** Answers [reply] with [relation] (`progress`, `completed`, `failed`) and [body]; the reply service's answer. */
    fun answer(reply: String, relation: String, body: Map<String, Any?>): Response
}
