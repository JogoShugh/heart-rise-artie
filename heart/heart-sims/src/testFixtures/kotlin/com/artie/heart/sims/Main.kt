package com.artie.heart.sims

import org.http4k.server.Jetty
import org.http4k.server.asServer

/**
 * The simulator as a process, for clients that are not on the JVM: it serves
 * on the port given (0 for any free one) and prints the one it took.
 */
fun main(args: Array<String>) {
    val server = Simulator().handler.asServer(Jetty(args.firstOrNull()?.toInt() ?: 0)).start()
    println("heart-sims listening on ${server.port()}")
    server.block()
}
