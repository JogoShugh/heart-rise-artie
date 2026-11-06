# heart-mcp as an image: an MCP server over stdio. Build from the repo root,
# since it needs the ARTIE client's source:
#   docker build -f mcp/Dockerfile -t heart-mcp .
# Run it as an MCP host would, with stdin open:
#   docker run -i --rm -e HEART_BOOKMARKS=garden=http://host.docker.internal:8080/ heart-mcp
# Inside a container, localhost is the container itself; host.docker.internal is the host.

# The client and heart-mcp compile for Java 22, so build and run on 22.
FROM eclipse-temurin:22.0.2_9-jdk AS build
WORKDIR /src
COPY client client
COPY mcp mcp
WORKDIR /src/mcp
RUN ./gradlew --no-daemon --quiet :heart-mcp-server:installDist

FROM eclipse-temurin:22.0.2_9-jre
COPY --from=build /src/mcp/heart-mcp-server/build/install/heart-mcp /opt/heart-mcp
ENTRYPOINT ["/opt/heart-mcp/bin/heart-mcp"]
