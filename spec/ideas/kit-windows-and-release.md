# Idea: the kit on Windows (WSL), and the Merge Watch release in the kit

**Status:** idea only, saved 2026-11-14. Nothing is built. Read from the code; nothing here was run on Windows.
Decisions marked **D?** are the author's; each carries a recommendation.

## Where things stand

| | `kit/` (2026-11-06) | Merge Watch release (`exhibits/merge-watch-release`, 2026-11-11 to 14) |
|---|---|---|
| For whom | Anyone: a README a coworker follows | This Mac only; no README, steps live in script headers |
| Packaging | One Docker image; the host needs git, Docker and `sh` | Host-run: a JDK, node, openssl, `shasum`, four Gradle installs first; Docker only for Jenkins and GitLab |
| SCM | GitHub only, the older `github` connector (`github:` relations) | `scm:` over a simulated GitHub (8098), real GitHub (8093) or GitLab in a container (8094) |
| Build | Simulated narrow Jenkins (8401), in memory | Real Jenkins in Docker (8171), webhook to the CI wrapper (8170) |
| Deploy | Simulated narrow cloud (8402), staging | Preview (8180), smoke test by link, promote to Dev (8190), 422 and 412 rules |
| RISE | None | Watcher (8099), signed replies, `once-answered` |
| Entry | `workspace.json` linking three systems | Release desk (8160), `desk:release` |
| Story | Claude walks a goal once; ARTIE keeps it; the kept script replays with no model | One kept script run by the desk; only the inputs vary (variants 501 to 505) |
| Credentials | The user's own token and repository | `~/.artie/credentials`; defaults to `JogoShugh/heart-github-test` |

The kit shows walk, keep and replay well, but predates `scm:`, RISE, Jenkins and the desk. The release is the
stronger demo, and only this Mac can run it.

## Gaps on Windows with WSL

### The kit: probably works, with gaps

1. **Line endings.** No `.gitattributes`. A clone made by Windows Git (`core.autocrlf=true`) gives every `.sh`
   CRLF endings, `entrypoint.sh` inside the image too, and the container won't start. A clone made inside WSL
   avoids it. `kit/.env` edited in Notepad is already fine: `env.sh` strips the trailing `\r`.
2. **Clone location.** A clone under `/mnt/c` is slow to build from and can lose the scripts' executable bit. The
   README doesn't say to clone in the WSL home folder.
3. **Memory advice.** The README points to Docker Desktop's Resources settings; on the WSL2 backend, memory is
   set in `.wslconfig`.
4. **Docker setup.** Docker Desktop with WSL integration on for the distro, or docker-ce inside WSL: not said.
5. **Browser.** Ports bound to `127.0.0.1` reach a Windows browser. Fine.

### The release: not ready anywhere but this Mac

1. `JAVA_HOME` defaults to a sdkman Graal 25 path on this Mac.
2. `gitlab/run.sh` defaults its host to `192.168.1.18`; every other script uses `localhost`. A plain bug.
3. Host tools: node (`github/seed.sh`), openssl, `shasum` (`sha256sum` on most Linux), four Gradle installs by
   hand (`heart-gateway`, `artie-release`, `artie-cli`, `heart-sims`), GitHub's OpenAPI description fetched by hand
   to `~/.cache/artie/ghdesc`.
4. Jenkins posts to `host.docker.internal:8170`, the CI wrapper on the host. With docker-ce inside WSL that
   reaches it. With Docker Desktop, `host-gateway` is Docker Desktop's own VM, not the user's distro: unverified.
5. Memory: GitLab CE (4 GB or more), Jenkins and five JVMs need about 12 GB in `.wslconfig`.

## Plan

1. **Line endings and docs.** `.gitattributes` with `*.sh`, `gradlew`, `kit/**` and the Jenkins templates
   `eol=lf`, `*.bat` `eol=crlf`. A "Windows (WSL)" section in `kit/README.md`: clone in `~`, Docker's WSL
   integration, `.wslconfig` memory.
2. **No Mac-only defaults** in the exhibit's scripts: `JAVA_HOME` only when that path exists, `RELEASE_HOST`
   `localhost` everywhere, `sha256sum` when `shasum` is missing, the repository slug required.
3. **Kit 2.0, with Docker Compose.** One image runs the release parties (desk, watcher, CI wrapper, preview, Dev)
   and the `github-scm` gateway, with Jenkins beside it on one Compose network: Jenkins reaches the wrapper by
   service name, which removes the `host.docker.internal` question. GitLab is an optional profile
   (`--profile gitlab`), being the memory hog. Seeding moves into the container, so the host needs no node. The
   variants are pull requests in the user's own repository. `kit/try.sh release 501`, and `scm=gitlab`.
4. **Test.** The kit and kit 2.0 in Linux CI (an Ubuntu runner) for the WSL path, plus one check on real Windows,
   for the Docker Desktop networking above. Hosted Windows runners can't run Docker in WSL2.
5. **Two sizes.** "kit": GitHub only, about 4 GB. "kit + GitLab": about 12 GB.

**D?** Recommendation: 1 and 2 first (small, safe), then 3.

## Testing without a Windows machine

The author has no Windows machine but a work one, which is better left alone (corporate policy, and Docker
Desktop needs a paid subscription in large companies). This Mac is an M3 Max, 36 GB, macOS 14.3.

| Way | Covers | Cost |
|---|---|---|
| **Linux VM or container on the Mac** (OrbStack, Lima, UTM), clone made with `git -c core.autocrlf=true` | Line endings, `sha256sum`, no sdkman, docker-ce's `host-gateway`: most of the gaps | Free, an hour |
| **Windows 11 on Arm in Parallels**, with WSL2 and Docker Desktop | The real thing, Docker Desktop's networking included. WSL2 inside a VM needs nested virtualization, which Apple Silicon offers only on M3 or later with macOS 15: this Mac needs macOS 15 first | Parallels (trial, then paid); macOS upgrade |
| **A Windows VM in the cloud** with nested virtualization (Azure Dv5 or Ev5, for instance), by the hour | The real thing, on x86 like most Windows users | A few dollars for an afternoon |

**D?** Recommendation: the Linux VM now; one cloud Windows afternoon once kit 2.0 exists.

## Done, 2026-11-15: tested in an Ubuntu VM on the Mac

The multi-system release now runs on a machine with only Docker. It was tested in an OrbStack Ubuntu 24.04 VM on the
Mac, which is the same setup as WSL. On a fresh clone: `kit/release.sh up gitlab`, then `kit/release.sh run github`,
`run gitlab` or `run github-live`. No JDK, node or Gradle is needed on the machine.

At all three forges (the small simulated GitHub, GitLab, and real GitHub), all five pull requests ended as expected:
501, 502, 503 and 505 were promoted to Dev, and 504 was not, because its smoke scenario S3 failed. The real-GitHub
runs opened PRs #2363–2367 in `JogoShugh/heart-github-test`, on `library/*` branches only.

### What broke on Linux, and the fixes

| Problem | Fix | Commit |
|---|---|---|
| Windows line endings break every script | `.gitattributes` | `d0dac2ec` |
| Ubuntu's own Docker has no BuildKit or Compose | README: `docker.io docker-buildx docker-compose-v2` | `77790f4f`, `a6db86e9` |
| The kit's kept seed script was out of date for everyone, the Mac included | Walked again, kept, strict replay passes | `54bfe4e0` |
| The release scripts pointed at this Mac's sdkman JDK, and GitLab at `192.168.1.18` | That JDK used only where it exists; GitLab at `localhost` | `9e16798e` |
| Jenkins refused the mounted repo on Linux ("dubious ownership"), so every build failed | The Jenkins image trusts the mounted repos | `6eff810f` |
| The real-GitHub gateway needed GitHub's API description in a Mac-only cache | Fetched when missing, at the exact version the pins were made against (`bb75494`) | `2aad674a` |

### How kit 2.0 is built (`a6db86e9`)

- One image holds the release services and both gateways (`kit/release/Dockerfile`).
- Jenkins and GitLab share that container's network (`kit/release/compose.yaml`), so the scripts' `localhost`
  addresses work unchanged. Jenkins listens on 8171, since GitLab's Puma takes 8080.
- GitLab is optional (a Compose profile), since it needs about 4 GB more memory.
- The kit README has a new section, "The release: GitHub or GitLab, Jenkins, and Dev".

### Things to know

- **Docker socket:** the release container gets access to Docker itself, because seeding GitLab runs its Rails
  console through Docker. That's fine for a local demo, but it means the container has full control of Docker on
  that machine.
- **When things answer:** the release desk and Dev answer only while a release is running; each release starts its
  services afresh. Jenkins and GitLab stay up until `kit/release.sh down`.
- **GitLab on Apple Silicon:** its image is amd64 only, so on the Mac it runs emulated. It's slower but works. On a
  normal Windows PC it runs natively.
- **Not yet tested:** Docker Desktop on real Windows. That still needs one real Windows box (the cloud VM above).

### Next

- CI on an Ubuntu runner that replays the kit's kept scripts and runs kit 2.0, to catch pin drift like the seed's.
- One afternoon on a cloud Windows VM, for Docker Desktop's own behaviour.
