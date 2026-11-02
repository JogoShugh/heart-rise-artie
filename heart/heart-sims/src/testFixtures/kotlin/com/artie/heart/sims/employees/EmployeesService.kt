package com.artie.heart.sims.employees

import org.http4k.core.HttpHandler

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

    fun reset() {
        staff.reset()
        handicapped = false
        heart = HeartEmployees(staff)
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

    val handler: HttpHandler = { request -> if (handicapped) spring.handler(request) else heart.handler(request) }
}
