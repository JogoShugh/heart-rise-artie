package com.artie.baseball

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Youth baseball scoring, 8U coach pitch, served as HEART by the library. */
@SpringBootApplication
class BaseballApplication

// Spring Boot's own entry point; the arguments are copied once, at startup.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<BaseballApplication>(*args)
}
