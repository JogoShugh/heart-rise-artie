package com.artie.heart.sims.employees

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Status

/**
 * The employees service: Spring HATEOAS's example employees, served as a
 * HEART service by heart-core ([HeartEmployees]): both dialects, a profile,
 * ETags and the reason an act is absent. Handicapped as Spring
 * ([handicapAsSpring]), the same employees are served as Spring HATEOAS's own
 * example serves them instead ([SpringHandicap]). Changed ([change]), it is
 * the service as it became after a release its clients were not written for.
 * A reset seeds Frodo and Bilbo, as the example's loader does, lifts the
 * handicap and undoes every change.
 */
class EmployeesService {
    private val staff = Staff()
    private val spring = SpringHandicap(staff)

    @Volatile
    private var heart = HeartEmployees(staff)

    @Volatile
    private var handicapped = false

    /** Who someone else will change, and to what role, right after a client next reads them; null when no one. */
    @Volatile
    private var meddler: Pair<Long, String>? = null

    fun reset() {
        staff.reset()
        handicapped = false
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

    /** From now on, until a reset, the employees are served as Spring HATEOAS serves them. */
    fun handicapAsSpring() {
        handicapped = true
    }

    /** From now on, until a reset, the service is as it became after [changes], with the same employees. */
    fun change(changes: Set<Change>) {
        heart = HeartEmployees(staff, changes)
    }

    /** Every employee not dismissed, in id order. */
    fun all(): List<Employee> = staff.working()

    val handler: HttpHandler = { request ->
        val response = if (handicapped) spring.handler(request) else heart.handler(request)
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
