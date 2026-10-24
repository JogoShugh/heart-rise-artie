package com.artie.heart.gateway

import org.eclipse.jetty.http.HttpVersion
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
 * by: a trial trusts the certificate itself.
 */
fun listener(serving: Serving): Jetty {
    val tls = serving.tls ?: return Jetty(serving.port)
    return Jetty(serving.port, { server: Server ->
        val keys = SslContextFactory.Server().apply {
            keyStorePath = tls.keystore.toString()
            keyStorePassword = tls.password
        }
        val https = HttpConfiguration().apply { addCustomizer(SecureRequestCustomizer(false)) }
        val ssl = SslConnectionFactory(keys, HttpVersion.HTTP_1_1.asString())
        ServerConnector(server, ssl, HttpConnectionFactory(https)).apply { port = serving.port }
    })
}
