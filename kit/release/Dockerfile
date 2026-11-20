# syntax=docker/dockerfile:1
#
# The Merge Watch release in one image: its parties (the release desk, the offer watcher, the CI wrapper in front of
# Jenkins, Dev, and a small GitHub), with ARTIE inside them, and the HEART gateway that serves real GitHub or GitLab
# in scm's words. Jenkins and GitLab run beside it (kit/release/compose.yaml), in its network, so every party reaches
# every other at localhost, as on the machine the release was made on. Build it from the repository's root.

# The heart library compiles for Java 21; ARTIE's release for Java 22, which Gradle finds where the property says.
FROM eclipse-temurin:21-jdk AS build
COPY --from=eclipse-temurin:22-jdk /opt/java/openjdk /opt/jdk22
WORKDIR /src
COPY build-logic build-logic
COPY heart heart
COPY client client
COPY docs/reports/assets docs/reports/assets
WORKDIR /src/heart
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet :heart-gateway:installDist :heart-sims:installSims
WORKDIR /src/client
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet -Porg.gradle.java.installations.paths=/opt/jdk22 :artie-release:installDist

# The release's scripts need git, curl, node (seeding real GitHub), openssl and shasum (perl); docker's own client
# reaches GitLab's Rails console when GitLab is seeded.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl git nodejs openssl perl \
    && rm -rf /var/lib/apt/lists/* && git config --system --add safe.directory "*"
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker
# The repository's own layout, so the release's scripts find everything where they look on any other machine.
WORKDIR /artie
COPY --from=build /src/heart/heart-gateway/build/install/heart-gateway heart/heart-gateway/build/install/heart-gateway
COPY --from=build /src/heart/heart-sims/build/sims heart/heart-sims/build/sims
COPY --from=build /src/client/artie-release/build/install/artie-release client/artie-release/build/install/artie-release
COPY heart/heart-gateway/connectors heart/heart-gateway/connectors
COPY exhibits/merge-watch-release exhibits/merge-watch-release
COPY kit/release/in-release.sh /kit/in-release.sh
WORKDIR /artie/exhibits/merge-watch-release
CMD ["tail", "-f", "/dev/null"]
