package com.artie.heart.gateway

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.io.File

/**
 * Starts a gateway from its environment ([GatewayConfig]): reads the
 * upstream's description once, builds the resource types, and serves them on
 * its port until stopped.
 */
fun main() {
    val config = GatewayConfig.from(System.getenv())
    val upstream = ClientFilters.SetBaseUriFrom(Uri.of(config.upstreamUrl)).then(JavaHttpClient())
    val spec = if (config.spec.startsWith("http://") || config.spec.startsWith("https://")) {
        val request = Request(Method.GET, config.spec)
        val signed = config.auth.authorization()?.let { request.header("Authorization", it) } ?: request
        JavaHttpClient()(signed).also { check(it.status.successful) { "${config.spec}: ${it.status}" } }.bodyString()
    } else {
        File(config.spec).readText()
    }
    val gateway = Gateway(GatewaySpec(spec), upstream, config.profile, config.auth)
    gateway.handler(config.credentials).asServer(Jetty(config.port)).start().block()
}
