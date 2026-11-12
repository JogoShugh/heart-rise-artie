package heart

import heart.script.Links
import java.net.URI

/**
 * Where the links of what [client] holds lead, by relation and title, resolved against its address: a deployment a
 * build links, given to the script it links as the address to run it at (goal-rise.feature).
 */
internal class HeldLinks(private val client: HeartRiseClient) : Links {
    override fun href(rel: String, title: String?): String? {
        val held = client.current
        val link = held?.links(rel)?.firstOrNull { title == null || it.title == title }
        return link?.let { URI(held.url).resolve(it.href).toString() }
    }
}
