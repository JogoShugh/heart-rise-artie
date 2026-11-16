// The quality plugins (artie.kotlin-quality, artie.coverage-merge), defined once for every Kotlin build here.
pluginManagement { includeBuild("../build-logic") }

// heart-mcp: HEART through MCP. The library holds the six tools; the server
// app runs them over stdio; mcp/Dockerfile packages the app.
rootProject.name = "heart-mcp-build"

// The ARTIE client (heart:artie), built from source.
includeBuild("../client")

include("heart-mcp", "heart-mcp-server")
