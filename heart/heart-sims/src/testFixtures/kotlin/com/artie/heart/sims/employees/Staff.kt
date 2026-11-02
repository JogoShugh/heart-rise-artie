package com.artie.heart.sims.employees

import com.artie.heart.ConcurrentWriteException
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/**
 * An employee as Spring's example keeps it, with what HEART adds when the
 * service answers as one: a version, changed by every act, and a status.
 */
data class Employee(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val role: String,
    val version: Int = 1,
    val status: String = WORKING,
) {
    companion object {
        const val WORKING = "working"
        const val DISMISSED = "dismissed"
    }
}

/** The employees, kept once for both ways the Spring service answers. */
class Staff {
    private val employees = ConcurrentSkipListMap<Long, Employee>()
    private val ids = AtomicLong()

    /** Back to the seed: Frodo and Bilbo, as the example's loader saves them. */
    fun reset() {
        employees.clear()
        ids.set(0)
        hire("Frodo", "Baggins", "ring bearer")
        hire("Bilbo", "Baggins", "burglar")
    }

    /** Every employee still working, in id order. */
    fun working(): List<Employee> = employees.values.filter { it.status == Employee.WORKING }

    /** Every employee, dismissed ones too, in id order. */
    fun everyone(): List<Employee> = employees.values.toList()

    /** The employee [id], whether working or not. */
    fun get(id: Long): Employee? = employees[id]

    fun hire(firstName: String, lastName: String, role: String): Employee =
        Employee(ids.incrementAndGet(), firstName, lastName, role).also { employees[it.id] = it }

    /** Spring's save: the employee [id] as given, whatever was there before. */
    fun save(id: Long, firstName: String, lastName: String, role: String): Employee =
        Employee(id, firstName, lastName, role, (employees[id]?.version ?: 0) + 1).also { employees[id] = it }

    /** Spring's delete: the employee [id] is no more. */
    fun remove(id: Long) {
        employees.remove(id)
    }

    /** [next] in place of [current], only if nothing else changed the employee since; a stale write otherwise. */
    fun replace(current: Employee, next: Employee): Employee {
        if (!employees.replace(current.id, current, next)) {
            throw ConcurrentWriteException("employee ${current.id} changed since it was read")
        }
        return next
    }
}
