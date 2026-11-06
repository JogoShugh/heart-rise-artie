# syntax=docker/dockerfile:1
#
# ARTIE and the HEART gateway for GitHub in one image, so anyone can try ARTIE on a GitHub repository of their own.
# Build it from the repository's root; kit/try.sh builds it the first time it runs:
#   docker build -f kit/Dockerfile -t heart-kit .
# Inside, the gateway serves GitHub as HEART on http://localhost:8392, calling GitHub with the caller's own token,
# and artie runs beside it. Both are on one machine, so no certificate is needed.

# The heart library and ARTIE compile for Java 21; artie-cli's Java part, with a Java 22 toolchain, which Gradle
# finds where the property below says. The cache mount keeps Gradle's downloads across builds.
FROM eclipse-temurin:21-jdk AS build
COPY --from=eclipse-temurin:22-jdk /opt/java/openjdk /opt/jdk22
WORKDIR /src
COPY heart heart
COPY client client
COPY docs/reports/assets docs/reports/assets
RUN --mount=type=cache,target=/root/.gradle cd heart && \
    ./gradlew --no-daemon --quiet :heart-gateway:installDist :heart-sims:installSims
RUN --mount=type=cache,target=/root/.gradle cd client && \
    ./gradlew --no-daemon --quiet -Porg.gradle.java.installations.paths=/opt/jdk22 :artie-cli:installDist

# GitHub's OpenAPI description (MIT, github/rest-api-description), which the gateway reads to learn GitHub's operations.
FROM alpine:3.20 AS description
ARG GITHUB_DESCRIPTION_REF=main
RUN apk add --no-cache curl && curl -fsSL -o /api.github.com.json \
    "https://raw.githubusercontent.com/github/rest-api-description/${GITHUB_DESCRIPTION_REF}/descriptions/api.github.com/api.github.com.json"

# artie runs on Java 22 or newer; the gateway on 21 or newer.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/heart/heart-gateway/build/install/heart-gateway /opt/heart-gateway
COPY --from=build /src/client/artie-cli/build/install/artie /opt/artie
# The simulated build and deploy services (heart-sims), each served alone; the simulator finds the GitHub connector
# its own small GitHub needs from its working directory.
COPY --from=build /src/heart/heart-sims/build/sims/lib /opt/heart-sims/lib
COPY heart/heart-gateway/connectors/github /opt/heart-sims/heart-gateway/connectors/github
COPY kit/workspace /kit/workspace
COPY --from=description /api.github.com.json /kit/github/api.github.com.json
COPY kit/goals /kit/goals
COPY kit/scripts /kit/scripts
COPY kit/entrypoint.sh /kit/entrypoint.sh
ENV PATH=/opt/artie/bin:$PATH
WORKDIR /work
ENTRYPOINT ["/kit/entrypoint.sh"]
CMD ["artie", "--help"]
