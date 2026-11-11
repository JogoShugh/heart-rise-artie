package com.artie.heart.sims

import org.http4k.server.Jetty
import org.http4k.server.asServer

/**
 * The simulator as a process, for clients that are not on the JVM: it serves
 * on the port given (0 for any free one) and prints the one it took. A second
 * argument names one service, such as `employees.test`, to serve alone at the
 * root, for a client that names no service in `X-Sim-Host`.
 *
 * `rise` serves the RISE example's two parties from one simulator, each alone
 * at its own origin, as their addresses name them: Riverside Public Library on
 * 8140, Ana's replies on 8141.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "rise") {
        val simulator = Simulator()
        val servers = listOf(LIBRARY_PORT to Simulator.LIBRARY, REPLIES_PORT to Simulator.REPLIES).map { (port, name) ->
            simulator.handlerFor(name).asServer(Jetty(port)).start().also {
                println("heart-sims serving $name on ${it.port()}")
            }
        }
        servers.last().block()
        return
    }
    val server = Simulator(args.getOrNull(1)).handler.asServer(Jetty(args.firstOrNull()?.toInt() ?: 0)).start()
    println("heart-sims listening on ${server.port()}")
    server.block()
}

private const val LIBRARY_PORT = 8140
private const val REPLIES_PORT = 8141
