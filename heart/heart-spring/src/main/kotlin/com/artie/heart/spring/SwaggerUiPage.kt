package com.artie.heart.spring

import com.artie.heart.OpenApiDocument
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

/**
 * Swagger UI at [PATH], for a person reading the OpenAPI description. It is
 * served from this server's own jar, pinned, so nothing is fetched from
 * elsewhere. Its Authorize button takes a credential from the person reading,
 * for trying acts out; the page itself holds none.
 */
class SwaggerUiPage {
    suspend fun serve(@Suppress("UNUSED_PARAMETER") request: ServerRequest): ServerResponse =
        ServerResponse.ok().contentType(MediaType("text", "html", Charsets.UTF_8)).bodyValueAndAwait(HTML)

    companion object {
        const val PATH = "/docs"

        /** The swagger-ui webjar's version, as in heart-spring's build. */
        const val VERSION = "5.17.14"
        const val FILES = "/webjars/swagger-ui/$VERSION"

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
}
