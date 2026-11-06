package heart

/**
 * Reading again while a server is still catching up (heart-script's CatchesUp): [client] reads the resource it
 * holds again, at most [TRIES] times, [PAUSE_MS] apart, while the act it is about to send isn't offered, or, with a
 * strict [guard], is offered unchanged in a set the lock file didn't record. A long wait is not catching up; after
 * those tries, what the client holds is refused as before.
 */
internal class CatchUp(private val client: HeartRiseClient, private val guard: () -> ScriptGuard?) {
    /** Whether [client] read again before [rel] could be sent as the run's care asks. */
    fun until(rel: String): Boolean {
        val address = client.current?.url ?: return false
        var tries = 0
        while (tries < TRIES && !ready(rel)) {
            Thread.sleep(PAUSE_MS)
            client.enter(address)
            tries++
        }
        return tries > 0
    }

    private fun ready(rel: String): Boolean =
        client.current?.affordance(rel) != null && guard()?.waiting(client.current, rel) != true

    private companion object {
        const val TRIES = 4
        const val PAUSE_MS = 500L
    }
}
