package com.artie.heart.gateway

import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.http.UriCompliance
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.http4k.server.Jetty

/**
 * The Jetty a gateway listens with: plain http on its port, or https from its keystore when [Serving.tls] names one.
 * HTTP/1.1 either way, which every client speaks. A local certificate's name need not match the host it is reached
 * by: a trial trusts the certificate itself. Either way it takes an encoded slash in a path, which Jetty refuses by
 * default: a branch named "deps/left-pad" is one path segment, "deps%2Fleft-pad", as GitHub takes it.
 */
fun listener(serving: Serving): Jetty = Jetty(serving.port, { server: Server ->
    val config = HttpConfiguration().apply { uriCompliance = ENCODED_SLASH }
    val tls = serving.tls
    if (tls == null) {
        ServerConnector(server, HttpConnectionFactory(config))
    } else {
        val keys = SslContextFactory.Server().apply {
            keyStorePath = tls.keystore.toString()
            keyStorePassword = tls.password
        }
        config.addCustomizer(SecureRequestCustomizer(false))
        val ssl = SslConnectionFactory(keys, HttpVersion.HTTP_1_1.asString())
        ServerConnector(server, ssl, HttpConnectionFactory(config))
    }.apply { port = serving.port }
})

/** Jetty's default URI rules, but for an encoded slash in a path segment. */
private val ENCODED_SLASH: UriCompliance =
    UriCompliance.DEFAULT.with("encoded slash", UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR)
