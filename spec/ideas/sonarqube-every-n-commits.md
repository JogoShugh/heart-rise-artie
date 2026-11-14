# Idea: a Dockerized SonarQube in the bdd-gates flow, scanning every N commits

Noted 2026-11-13. Not started. It adds a hook to the bdd-gates skill, and changing the gates is the author's call.

## Why

detekt runs after every edit (complexity lint) and is fast enough for the inner loop, but it is narrow: Kotlin
only, mostly smells and complexity, little security, no dependency checks. SonarQube adds breadth on a slower
cadence: bugs, duplication, coverage, trends and a quality gate, across languages.

| Tool | Finds | Security | Where it runs |
|---|---|---|---|
| detekt | Kotlin complexity, smells, style, some likely bugs | Little: no taint tracing, no dependencies | Local, seconds: after every edit |
| SonarQube | Bugs, smells, duplication, coverage, quality gates; Kotlin rules overlap detekt's | Hotspots; taint analysis only in paid editions | A server (Docker), minutes: every N commits |
| Veracode | Security only: binary SAST, dependencies, DAST | Strong, compliance-grade | Commercial SaaS; not for the inner loop |
| Semgrep | Pattern rules, many languages | Good security rules, Kotlin supported | Fast CLI; could sit in a hook |
| Snyk / OWASP Dependency-Check | Vulnerable dependencies | Strong on dependencies | CLI |

Recommendation: keep detekt in the inner loop, add SonarQube every N commits, add one dependency scanner.
Veracode only if compliance requires it.

## How

- **Run it:** `docker run -d -p 9000:9000 sonarqube:community`. The embedded database is fine for a trial; use
  Postgres to keep data across upgrades. Needs about 2–4 GB of RAM.
- **Scan:** the `org.sonarqube` Gradle plugin, `./gradlew sonar` with a token from the environment (never printed,
  never committed). Import detekt's report so findings are not duplicated.
- **Every N commits:** a post-commit hook keeps the last scanned commit in the skill's state. When
  `git rev-list --count <last>..HEAD` reaches N, it starts the scan in the background and records the quality
  gate's result. A commit never waits on the scan.
- **Configured in the skill:** `SONAR_EVERY=10` (say); `SONAR_EVERY=0` turns it off.
- **The result:** a warning at the next stop, or a blocking gate. The author chooses which.

## Open

1. Warn or block on a failed quality gate?
2. Which dependency scanner, if any: Snyk or OWASP Dependency-Check?
3. Where the server runs when the laptop is not the only machine.
