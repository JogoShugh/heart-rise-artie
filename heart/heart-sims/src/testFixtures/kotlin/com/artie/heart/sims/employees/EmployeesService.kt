package com.artie.heart.sims.employees

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Status

/**
 * The employees service: Spring HATEOAS's example employees, served as a
 * HEART service by heart-core ([HeartEmployees]): both dialects, a profile,
 * ETags and the reason an act is absent. Switched to stock Spring
 * ([answerAs]), the same employees are served as Spring HATEOAS's own
 * example serves them out of the box instead ([StockSpring]). Changed ([change]), it is
 * the service as it became after a release its clients were not written for.
 * A reset seeds Frodo and Bilbo, as the example's loader does, serves them
 * as HEART again and undoes every change.
 */
class EmployeesService {
    private val staff = Staff()
    private val spring = StockSpring(staff)

    @Volatile
    private var heart = HeartEmployees(staff)

    @Volatile
    private var mode = Mode.HEART

    /** Who someone else will change, and to what role, right after a client next reads them; null when no one. */
    @Volatile
    private var meddler: Pair<Long, String>? = null

    fun reset() {
        staff.reset()
        mode = Mode.HEART
        meddler = null
        heart = HeartEmployees(staff)
    }

    /**
     * Once, until a reset: right after a client next reads the employee [id], someone else changes their role to
     * [role], as a person at another desk would, before the client can act on what it read.
     */
    fun meddle(id: Long, role: String) {
        meddler = id to role
    }

    /** From now on, until a reset, the employees are served in [mode]: as HEART, or as stock Spring HATEOAS. */
    fun answerAs(mode: Mode) {
        this.mode = mode
    }

    /** How the employees are served: by heart-core, or as Spring HATEOAS's example serves them out of the box. */
    enum class Mode(val value: String) {
        HEART("heart"),
        STOCK_SPRING("stock-spring"),
        ;

        companion object {
            fun of(value: String): Mode =
                requireNotNull(entries.firstOrNull { it.value == value }) {
                    "the employees service answers as ${entries.joinToString(" or ") { it.value }}"
                }
        }
    }

    /** From now on, until a reset, the service is as it became after [changes], with the same employees. */
    fun change(changes: Set<Change>) {
        heart = HeartEmployees(staff, changes)
    }

    /** Every employee not dismissed, in id order. */
    fun all(): List<Employee> = staff.working()

    val handler: HttpHandler = { request ->
        val response = if (mode == Mode.STOCK_SPRING) spring.handler(request) else heart.handler(request)
        // The read is answered as it was; the change lands after it, before the client's next request.
        meddler?.takeIf { (id) -> request.method == Method.GET && request.uri.path == "/employees/$id" }
            ?.takeIf { response.status == Status.OK || response.status == Status.NOT_MODIFIED }
            ?.let { (id, role) ->
                meddler = null
                staff.changeRole(id, role)
            }
        response
    }
}
