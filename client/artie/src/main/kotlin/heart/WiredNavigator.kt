package heart

import heart.script.Navigator
import heart.script.Received
import heart.script.Sent
import heart.script.Wire

/**
 * ARTIE as a script's client, sending a bare script's calls too (spec/ideas/bare-runs.md): [navigator] does every
 * HEART selection, and [wire] sends each bare call as written. The [wire] is ARTIE's own below its HEART care: its
 * credentials, its name and trace, and its exchange log, with no cache and no integrity check above them, and no
 * validator, since nothing but the script sets a header.
 */
class WiredNavigator(val navigator: Navigator, private val wire: Transport) : Navigator by navigator, Wire {
    override fun send(sent: Sent): Received {
        val answer = wire.send(HeartRequest(sent.method, sent.url, sent.headers, sent.body))
        val headers = answer.headers.mapValues { (_, values) -> values.joinToString(", ") }
        return Received(answer.status, headers, answer.body)
    }
}
