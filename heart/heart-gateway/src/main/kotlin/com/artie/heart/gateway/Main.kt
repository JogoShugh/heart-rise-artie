package com.artie.heart.gateway

import com.artie.heart.http4k.HeartSettings
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
 * upstream's description once, and its connector if it has one, builds the
 * resource types, and serves them on its port until stopped.
 */
fun main() {
    val config = GatewayConfig.from(System.getenv())
    val settings = config.upstream
    val upstream = ClientFilters.SetBaseUriFrom(Uri.of(settings.url)).then(JavaHttpClient())
    val spec = if (settings.spec.startsWith("http://") || settings.spec.startsWith("https://")) {
        val request = Request(Method.GET, settings.spec)
        val signed = settings.auth.authorization()?.let { request.header("Authorization", it) } ?: request
        JavaHttpClient()(signed).also { check(it.status.successful) { "${settings.spec}: ${it.status}" } }.bodyString()
    } else {
        File(settings.spec).readText()
    }
    val mapper = HeartSettings.defaultMapper()
    val connector = config.connector?.let { Connector.load(it, mapper) }
    val profile = connector?.profile ?: checkNotNull(config.profile)
    val gateway = Gateway(
        GatewaySpec(spec, connector?.reads),
        upstream,
        profile,
        settings.auth,
        mapper,
        connector,
        upstreamUrl = settings.url,
        record = config.record?.let { UpstreamRecord(it, mapper, settings.url) },
    )
    gateway.handler(config.credentials).asServer(Jetty(config.port)).start().block()
}
