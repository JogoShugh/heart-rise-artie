package com.artie.heart.http4k

import com.artie.heart.OpenApiDocument
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.static

/**
 * Swagger UI at [PATH], for a person reading the OpenAPI description. It is
 * served from the swagger-ui webjar in this binding's own jar, pinned, so
 * nothing is fetched from elsewhere. The page itself holds no credential.
 */
object SwaggerUi {
    const val PATH = "/docs"

    /** The swagger-ui webjar's version, as in heart-http4k's build. */
    const val VERSION = "5.17.14"
    const val FILES = "/webjars/swagger-ui/$VERSION"

    fun page(@Suppress("UNUSED_PARAMETER") request: Request): Response =
        Response(Status.OK).header("Content-Type", "text/html;charset=UTF-8").body(HTML)

    /** The webjar's files under [FILES], served by http4k's own static routing. */
    fun files(): RoutingHttpHandler = FILES bind static(ResourceLoader.Classpath("/META-INF/resources$FILES"))

    private val HTML = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>API description</title>
          <link rel="stylesheet" href="$FILES/swagger-ui.css">
        </head>
        <body>
          <div id="swagger-ui"></div>
          <script src="$FILES/swagger-ui-bundle.js"></script>
          <script>
            SwaggerUIBundle({ url: "${OpenApiDocument.PATH}", dom_id: "#swagger-ui" });
          </script>
        </body>
        </html>
    """.trimIndent()
}
