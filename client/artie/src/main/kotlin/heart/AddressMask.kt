package heart

import java.net.InetAddress

/**
 * A network, written as a mask (CIDR, RFC 4632; RFC 4291 §2.3 for IPv6): `192.168.0.0/16`. A host names the networks
 * ARTIE may send credentials to over plain HTTP (hosted.feature). Only an address written as numbers is in a mask:
 * a name never is, whatever it resolves to, so nothing is looked up.
 */
class AddressMask(written: String) {
    private val network: ByteArray
    private val bits: Int

    init {
        val (address, length) = written.split('/').let { it[0] to it.getOrNull(1) }
        network = requireNotNull(literal(address)) { "not a network written as numbers: $written" }
        bits = length?.toIntOrNull() ?: (network.size * BITS_PER_BYTE)
        require(bits in 0..network.size * BITS_PER_BYTE) { "not a mask: $written" }
    }

    /** Whether [host], as a URL writes it, is an address in this network. */
    operator fun contains(host: String): Boolean {
        val address = literal(host.removePrefix("[").removeSuffix("]"))
        return address != null && address.size == network.size && (0 until bits).all { bit ->
            val mask = 1 shl (BITS_PER_BYTE - 1 - bit % BITS_PER_BYTE)
            address[bit / BITS_PER_BYTE].toInt() and mask == network[bit / BITS_PER_BYTE].toInt() and mask
        }
    }

    private companion object {
        const val BITS_PER_BYTE = 8
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        val IPV6 = Regex("""^[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*$""")

        /** The address [text] writes as numbers; null for a name, which is never looked up. */
        fun literal(text: String): ByteArray? =
            if (IPV4.matches(text) || IPV6.matches(text)) InetAddress.getByName(text).address else null
    }
}
