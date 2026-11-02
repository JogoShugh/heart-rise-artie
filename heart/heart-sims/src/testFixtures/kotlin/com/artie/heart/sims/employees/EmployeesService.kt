package com.artie.heart.sims.employees

import org.http4k.core.HttpHandler

/**
 * The employees service: Spring HATEOAS's example employees, served as a
 * HEART service by heart-core ([HeartEmployees]): both dialects, a profile,
 * ETags and the reason an act is absent. Handicapped as Spring
 * ([handicapAsSpring]), the same employees are served as Spring HATEOAS's own
 * example serves them instead ([SpringHandicap]). A reset seeds Frodo and
 * Bilbo, as the example's loader does, and lifts the handicap.
 */
class EmployeesService {
    private val staff = Staff()
    private val heart = HeartEmployees(staff)
    private val spring = SpringHandicap(staff)

    @Volatile
    private var handicapped = false

    fun reset() {
        staff.reset()
        handicapped = false
    }

    /** From now on, until a reset, the employees are served as Spring HATEOAS serves them. */
    fun handicapAsSpring() {
        handicapped = true
    }

    /** Every employee still working, in id order. */
    fun all(): List<Employee> = staff.working()

    val handler: HttpHandler = { request -> if (handicapped) spring.handler(request) else heart.handler(request) }
}
