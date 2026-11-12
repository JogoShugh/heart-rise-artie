package heart.release

import com.artie.heart.sims.rise.ReplyService
import heart.script.Replies
import heart.script.Reply
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
 * The replies the desk keeps for the runs it hosts (release-desk.feature), served at [origin] by heart-core's reply
 * resource ([service]): each opened before the request that names it, told what that request brought into being,
 * and, once a party answers it, the run waiting on it woken. The run then reads the reply itself.
 */
class DeskReplies(private val service: ReplyService, private val origin: String) : Replies {
    private val next = AtomicLong(1)
    private val changes = mutableMapOf<String, Long>()
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    override fun open(): Reply {
        val reference = "r${next.getAndIncrement()}-${System.currentTimeMillis()}"
        service.open(reference)
        return Reply("$origin$REPLIES$reference", "$origin/keys")
    }

    override fun asked(reply: Reply, request: String) = service.asked(reference(reply.address), request)

    override fun await(reply: Reply, atMost: Duration): Boolean {
        val reference = reference(reply.address)
        val end = System.currentTimeMillis() + atMost.inWholeMilliseconds
        return lock.withLock {
            val before = changes[reference] ?: 0
            while ((changes[reference] ?: 0) == before && end > System.currentTimeMillis()) {
                changed.await(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            }
            (changes[reference] ?: 0) != before
        }
    }

    /** The replies, served; an answer the reply took wakes whoever waits on it. */
    val handler: HttpHandler = { request ->
        val answered = service.handler(request)
        if (request.method != Method.GET && answered.status.successful) {
            val reference = reference(request.uri.path)
            lock.withLock {
                changes[reference] = (changes[reference] ?: 0) + 1
                changed.signalAll()
            }
        }
        answered
    }

    private fun reference(address: String) = address.substringAfter(REPLIES).substringBefore('/')

    private companion object {
        const val REPLIES = "/replies/"
    }
}
