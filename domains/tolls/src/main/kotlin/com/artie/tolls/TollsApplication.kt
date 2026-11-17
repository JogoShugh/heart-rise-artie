package com.artie.tolls

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Toll accounts and fares, E-ZPass style, served as HEART by the library. */
@SpringBootApplication
class TollsApplication

// Spring Boot's own entry point; the arguments are copied once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<TollsApplication>(*args)
}
