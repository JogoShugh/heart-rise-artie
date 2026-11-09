package com.artie.heart.sims.library

import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler

/**
 * Ana's side of the exchange: a HEART service of replies, each awaiting until the library answers it. Only the library,
 * with its Bearer token, may act on a reply. The reply resource itself is heart-core's (Replies), served here over HTTP.
 */
class CallerService {
    private val store = ReplyStore()

    /** Opens the reply named [reference], awaiting. */
    fun open(reference: String) = store.open(reference)

    /** Whether a reply named [reference] is open. */
    fun has(reference: String): Boolean = store.get(reference) != null

    /** The caller's HEART service: the replies, served to the library's Bearer token. */
    val handler: HttpHandler = HeartHttp(
        listOf(Replies.resource(store)),
        Credentials.parse(users = "", tokens = Tokens.sha256(LIBRARY_TOKEN)),
        HeartSettings(title = "Ana's replies", realm = Replies.PROFILE.name),
    )

    companion object {
        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"
    }
}
