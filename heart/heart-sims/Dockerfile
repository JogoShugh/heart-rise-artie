# syntax=docker/dockerfile:1
#
# The simulated services (heart-sims) as an image: the build service, the
# deploy service and the GitHub gateway in front of its small GitHub, on one
# port, for any client the shared conformance features hold, in any language.
# Build from the heart library's folder, since the simulator is built from it:
#   docker build -f heart/heart-sims/Dockerfile -t heart-sims heart
# Run it, and point a client's steps at it:
#   docker run --rm -p 8099:8099 heart-sims
#   HEART_SIMS_URL=http://localhost:8099 (heart-ts: implementations/conformance)
# A request names the service it means in X-Sim-Host (build.test, deploy.test,
# github.test, elsewhere.test); any other request reaches the control routes
# under /_sim/ (reset, faults, set up, the request log).

# The heart library compiles for Java 21, so build and run on 21. The cache
# mount keeps Gradle's downloads across builds, so a change costs a compile.
FROM eclipse-temurin:21.0.5_11-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet :heart-sims:installSims

FROM eclipse-temurin:21.0.5_11-jre
WORKDIR /sims
COPY --from=build /src/heart-sims/build/sims/lib lib
# The GitHub service is the gateway as shipped: its connector folder, found from the working directory.
COPY heart-gateway/connectors/github heart-gateway/connectors/github
EXPOSE 8099
ENTRYPOINT ["java", "-cp", "lib/*", "com.artie.heart.sims.MainKt"]
CMD ["8099"]
