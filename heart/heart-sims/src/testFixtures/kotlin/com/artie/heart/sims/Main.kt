package com.artie.heart.sims

import com.artie.heart.sims.library.LibraryPolicy
import com.artie.heart.sims.library.LibraryService
import com.artie.heart.sims.release.Release
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.nio.file.Path

/**
 * The simulator as a process, for clients that are not on the JVM: it serves
 * on the port given (0 for any free one) and prints the one it took. A second
 * argument names one service, such as `employees.test`, to serve alone at the
 * root, for a client that names no service in `X-Sim-Host`.
 *
 * `rise` serves the RISE example's two parties from one simulator, each alone
 * at its own origin, as their addresses name them: Riverside Public Library on
 * 8140, Ana's replies on 8141.
 *
 * `library PORT POLICY` serves Riverside Public Library alone on PORT, by the rules its policy file at POLICY says
 * (LibraryPolicy): how a build deploys the library at a commit, as a preview.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "release") {
        Release(
            host = System.getenv("RELEASE_HOST") ?: "localhost",
            repository = Path.of(checkNotNull(System.getenv("RELEASE_REPOSITORY")) { "RELEASE_REPOSITORY" }),
            jenkinsUser = System.getenv("JENKINS_ADMIN_USER") ?: "artie",
            jenkinsPassword = checkNotNull(System.getenv("JENKINS_ADMIN_PASSWORD")) { "JENKINS_ADMIN_PASSWORD" },
            hookSecret = checkNotNull(System.getenv("JENKINS_HOOK_SECRET")) { "JENKINS_HOOK_SECRET" },
        ).serve()
        return
    }
    if (args.firstOrNull() == "library") {
        val policy = LibraryPolicy.read(Path.of(args[2]))
        val server = LibraryService(policy = policy).handler.asServer(Jetty(args[1].toInt())).start()
        println("heart-sims serving the library on ${server.port()}, renewing ${policy.renewalWeeks} weeks at a time")
        server.block()
        return
    }
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
