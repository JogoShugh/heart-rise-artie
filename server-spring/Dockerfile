# syntax=docker/dockerfile:1
#
# Dockerfile
#
# Multi-stage: build with a full JDK+Gradle image, run on a slim JRE.
# UNVERIFIED, same as the rest of this project's Docker pieces --
# no Docker available in the environment this was written in.
#
# The --mount=type=cache on the gradle step is the important addition:
# without it, EVERY code change invalidates Docker's layer cache for
# this step, and Gradle re-downloads every dependency from Maven
# Central from scratch each time (not just recompiles) -- because
# nothing about Gradle's own ~/.gradle cache survives between builds
# otherwise. The cache mount persists that directory across builds
# even when the layer itself gets invalidated, so only genuinely new
# or changed dependencies get re-fetched, and compilation is the only
# real cost on a normal "I changed one file" rebuild.
#
# Requires BuildKit, which `docker compose up --build` uses by default
# on any reasonably current Docker Desktop install (the `docker:
# desktop-linux` driver shown in build output confirms this).

# The build context is the repo root (see docker-compose.yml), because the
# server includes the HEART library build from ../heart.
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY heart ./heart
COPY server-spring/build.gradle.kts server-spring/settings.gradle.kts ./server-spring/
COPY server-spring/src ./server-spring/src
WORKDIR /app/server-spring
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server-spring/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
