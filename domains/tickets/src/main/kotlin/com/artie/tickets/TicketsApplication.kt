package com.artie.tickets

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Support tickets, served as HEART by the library. */
@SpringBootApplication
class TicketsApplication

// Spring Boot's own entry point; the arguments are copied once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<TicketsApplication>(*args)
}
