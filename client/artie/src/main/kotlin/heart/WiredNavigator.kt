package heart

import heart.script.Navigator
import heart.script.Reads
import heart.script.Received
import heart.script.Sent
import heart.script.Wire
import heart.script.Writes
import java.net.URI

/**
 * ARTIE as a script's client, sending a bare script's calls too (spec/ideas/bare-runs.md): [navigator] does every
 * HEART selection, and [wire] sends each bare call as written. The [wire] is ARTIE's own below its HEART care: its
 * credentials, its name and trace, and its exchange log, with no cache and no integrity check above them, and no
 * validator, since nothing but the script sets a header.
 */
class WiredNavigator(val navigator: Navigator, private val wire: Transport) :
    Navigator by navigator, Wire, Writes, Reads {
    /** The words [navigator]'s reasoner writes, when it writes. */
    override fun write(intent: String): String? = (navigator as? Writes)?.write(intent)

    /** What the link [rel] titled [title] in what ARTIE holds leads to, read as it is (linked-scripts.feature). */
    override fun read(rel: String, title: String?): Received? {
        val held = (navigator as? ArtieNavigator)?.holding
        val link = held?.links(rel)?.firstOrNull { title == null || it.title == title }
        return link?.let { send(Sent("GET", URI(held.url).resolve(it.href).toString(), ACCEPT_ANY, null)) }
    }

    override fun send(sent: Sent): Received {
        val answer = wire.send(HeartRequest(sent.method, sent.url, sent.headers, sent.body))
        val headers = answer.headers.mapValues { (_, values) -> values.joinToString(", ") }
        return Received(answer.status, headers, answer.body)
    }

    private companion object {
        val ACCEPT_ANY = mapOf("Accept" to "application/yaml, text/plain;q=0.9, */*;q=0.1")
    }
}
