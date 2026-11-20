package com.artie.basketball

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Youth basketball scoring, served as HEART by the library. */
@SpringBootApplication
class BasketballApplication

// Spring Boot's own entry point; the arguments are copied once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<BasketballApplication>(*args)
}
