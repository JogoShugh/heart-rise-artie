package com.artie.heart.sims

import org.http4k.server.Jetty
import org.http4k.server.asServer

/**
 * The simulator as a process, for clients that are not on the JVM: it serves
 * on the port given (0 for any free one) and prints the one it took. A second
 * argument names one service, such as `employees.test`, to serve alone at the
 * root, for a client that names no service in `X-Sim-Host`.
 */
fun main(args: Array<String>) {
    val server = Simulator(args.getOrNull(1)).handler.asServer(Jetty(args.firstOrNull()?.toInt() ?: 0)).start()
    println("heart-sims listening on ${server.port()}")
    server.block()
}
