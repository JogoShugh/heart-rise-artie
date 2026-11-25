# Try ARTIE on your own GitHub

ARTIE is a generic client for HEART, the way a browser is a generic client for HTML.

## Quickstart

Needs git, and Docker running with 10 GB of memory. No account, no key, no model.

```sh
git clone --branch feat/kit-model-free https://github.com/JogoShugh/heart-rise-artie.git
cd heart-rise-artie
```

**1. Kept scripts at a small GitHub**

```sh
kit/try.sh tour
```

Expected (first run builds for a few minutes):

```
seed: six issues, three waiting for triage and three ready to close
finished, 15 steps; acted: github:repos-get 1, github:open-issue 6

hello: open an issue, comment on it, close it
finished, 12 steps; acted: github:repos-get 1, github:open-issue 1, github:issues-get 1, github:comment 1, github:close 1

closeout: …
finished, 34 steps; acted: … github:close 1, github:close-not-planned 1, github:close-duplicate 1

release: queue a build, deploy it to staging, record it on GitHub
finished, 16 steps; acted: ci:build 1, cd:deploy 1, github:repos-get 1, github:open-issue 1
```

**2. The release at real Jenkins and GitLab**

```sh
kit/release.sh up gitlab
kit/release.sh run gitlab
```

Expected (GitLab takes several minutes the first time):

```
waiting for Jenkins... up at http://localhost:8171
waiting for GitLab (several minutes the first time)...... up at http://localhost:8929
…
pr-501: promoted to Dev at "…"
pr-502: promoted to Dev at "…"
pr-503: promoted to Dev at "…"
pr-504: not promoted to Dev
pr-505: promoted to Dev at "…"
Each release's trace: work/runs/gitlab/pr-N.yaml
```

The same five at the small GitHub instead: `kit/release.sh run github`.

**3. Look, then stop**

```sh
open work/runs/hello-kept/index.html   # every request and answer
kit/try.sh down
kit/release.sh down
```

Everything below is detail.

- Claude walks a goal written in plain words once.
- ARTIE keeps that walk as a script, and the script runs again with no model.
- Everything runs in one Docker container on your machine: at a small GitHub inside it by default, or on an empty
  GitHub repository of yours, with your own token.

<details>
<summary>Diagram: walk once, keep, replay with no model</summary>

```mermaid
sequenceDiagram
    actor You
    participant ARTIE
    participant Claude
    participant Gateway as GitHub gateway (HEART)
    participant GitHub
    You->>ARTIE: kit/try.sh walk hello
    loop each line of the goal
        ARTIE->>Gateway: GET the resource held
        Gateway->>GitHub: GitHub's API
        Gateway-->>ARTIE: state, links and forms offered now
        ARTIE->>Claude: the goal's line and what is offered
        Claude-->>ARTIE: the link or form to use, and its values
        ARTIE->>Gateway: follow the link or submit the form
    end
    You->>ARTIE: kit/try.sh keep hello
    ARTIE-->>You: hello.heart.yaml (steps) and hello.pins.yaml (what each step was offered)
    You->>ARTIE: kit/try.sh replay hello
    loop each kept step, no model
        ARTIE->>Gateway: GET, then check the offer against its pin
        alt offer matches the pin
            ARTIE->>Gateway: the same act
        else offer differs
            ARTIE-->>You: stopped by its guard, nothing sent
        end
    end
```

</details>

## You need

- A Mac or Linux machine with git, and Docker Desktop running, with about 10 GB of memory for Docker (6 without
  GitLab).
- That's all, by default. Everything runs in containers on your machine, with no account and no key, and no model:
  - the kit's kept scripts (seed, hello, closeout, release) replay at a small GitHub inside the kit, repository
    `acme/web`, through the same HEART gateway and GitHub connector as real GitHub;
  - the release kit releases five pull requests at a small GitHub, and the same five at GitLab.
- GitLab is on unless you opt out (`USE_GITLAB=no`): it wants about 4 GB more of Docker's memory.
- Opt in, if you like:
  - **a GitHub account**: the same kept scripts replayed on a repository of yours, on real GitHub (`USE_GITHUB`);
  - **an Anthropic API key** (console.anthropic.com): Claude walks goals written in words, billed to your key
    (`USE_ANTHROPIC`). Triage replays need it too: one of its steps is reasoned.

### The quick way: one script

It does every step below, saying what it does: it builds and starts the release kit and releases its five pull
requests, then builds the kit and replays its kept scripts (`kit/try.sh tour`), all with no model. It asks about
GitLab, defaulting to yes, and about each opt in, defaulting to no. A key you paste shows a `*` per character, then
how many characters it got and their last four.

Settle each ahead, if you like, in `kit/.env` (`cp kit/env.example kit/.env`):

| `USE_GITLAB`, `USE_GITHUB`, `USE_ANTHROPIC` | Setup |
|---|---|
| `ask` (the default) | asks; for a key `kit/.env` holds, asks whether to use it |
| `yes` | uses it, asking only for what `kit/.env` lacks, and says so |
| `no` | leaves it off, and says so |

What you opt in to is written back as `yes`, so `kit/try.sh` knows it and the next setup only says so.

To run things again from the start:

- `kit/try.sh tour` starts the small GitHub afresh each time; `kit/try.sh reset` does that alone.
- `kit/release.sh reset` puts the release's small GitHub and GitLab back as first seeded and clears their runs
  (`reset github` or `reset gitlab` for one).

- **Windows**, in PowerShell (it installs WSL and Ubuntu if need be, clones this repository inside Ubuntu, and
  carries on there):
  ```powershell
  irm https://raw.githubusercontent.com/JogoShugh/heart-rise-artie/feat/kit-model-free/kit/windows/setup.ps1 | iex
  ```
- **Ubuntu or macOS**, in a clone of this repository: `kit/setup.sh`

To opt in to GitHub, create the empty repository and the token first (Set up, once, steps 1 and 2); the script
asks for them.

### On Windows, by hand

Use WSL 2 with Ubuntu, and do everything below inside Ubuntu, not in PowerShell:

- **Clone inside Ubuntu**, in your home folder (`cd ~`), with Ubuntu's own git.
  - A clone under `/mnt/c` is slow to build from.
  - A clone made by Git for Windows may not run.
- **Docker**, one of:
  - Docker Desktop with WSL integration turned on for your Ubuntu (Settings, Resources, WSL integration).
  - Docker inside Ubuntu: `sudo apt install docker.io docker-buildx docker-compose-v2`, then
    `sudo usermod -aG docker $USER`, and open a new terminal. Without `docker-buildx` the build stops with "the
    --mount option requires BuildKit".
- **Memory** for WSL is set in `%UserProfile%\.wslconfig` (`[wsl2]`, `memory=12GB`), not in Docker Desktop.

## Set up, once

With no account and no key, there is nothing to set up: clone this repository and run `kit/try.sh tour`. What
follows is for opting in to your GitHub by hand, as `kit/setup.sh` does.

1. **On GitHub, create an empty repository**, named `kit-trial` say. Leave everything as it is: no README, no
   template.
2. **Create a token** at https://github.com/settings/personal-access-tokens/new
   - Repository access: **Only select repositories**, and pick `kit-trial`.
   - Permissions, Repository: **Issues**, **Pull requests** and **Contents**, each **Read and write**.
3. **Clone this repository and put your keys in `kit/.env`:**
   ```sh
   git clone --branch feat/kit-model-free https://github.com/JogoShugh/heart-rise-artie.git
   cd heart-rise-artie
   cp kit/env.example kit/.env
   ```
   Edit `kit/.env`:
   - `OWNER`: your GitHub user name.
   - `REPO`: `kit-trial`.
   - `GITHUB_TOKEN`, and `USE_GITHUB=yes`.
   - `ANTHROPIC_API_KEY` and `USE_ANTHROPIC=yes`, for Claude's walks.

   Git ignores this file, and the kit's image never holds it.
4. **Bootstrap:**
   ```sh
   kit/bootstrap.sh
   ```
   The first run builds the kit, which takes a few minutes. Then six issues appear in `kit-trial`.

## Run

With no model, the kept scripts the kit ships, at the small GitHub (or on yours, with `USE_GITHUB=yes`):

```sh
kit/try.sh tour                          # seed, hello, closeout and release, one after another
kit/try.sh replay hello                  # opens an issue, comments on it, closes it
kit/try.sh replay closeout version=1.0   # closes the three marked ready-to-close; kit/try.sh stop closeout ends it
kit/try.sh replay release version=1.1    # queues a build, deploys it, records the release on GitHub
kit/try.sh reset                         # the small GitHub and the services as first started
```

- Each script is held strictly to its pins: `NAME.sim.pins.yaml` at the small GitHub, `NAME.pins.yaml` on real
  GitHub. Each was made where it holds.
- `kit/try.sh up` keeps the kit running between commands; the small GitHub's state lasts until `down` or `reset`.
- `triage` isn't in the tour: one of its steps asks the model which label an issue's words call for.

With an Anthropic key, each line is one goal, walked by Claude, live:

```sh
kit/try.sh walk hello                  # opens an issue, comments on it, closes it
kit/try.sh walk triage                 # labels the three issues marked needs-triage
kit/try.sh walk closeout version=1.0   # closes the three marked ready-to-close, each its own way
kit/try.sh walk release version=1.1    # queues a build, deploys it, records the release on GitHub
```

`triage` and `closeout` keep watching until you stop them:

- Each opens an issue named "Triage watch" or "Close-out watch".
- When the issues it works on are done, close that watch issue: `kit/try.sh stop triage` (or `closeout`), or by
  hand on GitHub.
- The command ends a few seconds later.

Keep a walk as a script, and replay it with no model:

```sh
kit/try.sh keep release
kit/try.sh replay release version=1.0
```

## What you see

- **On GitHub**, in `kit-trial`: every issue, label, comment and close, made by your account.
- **On your machine**, `work/runs/NAME/run.html`: every request and answer of each run. Open it in a browser.
- **The kept scripts**, `work/NAME.heart.yaml` and `work/NAME.pins.yaml`: the steps, and what each step was offered.

---

## The goals

| Goal | What it does |
|---|---|
| `seed` | What the bootstrap ran: six issues, three labelled `needs-triage` (one also `documentation`), three `ready-to-close` with one of `shipped`, `wontfix` or `duplicate`. Kept as `seed.heart.yaml`, so it runs with no model. |
| `hello` | Opens an issue, comments on it and closes it as completed. |
| `triage` | A watch. Each issue with `needs-triage` gets one label, `bug`, `question` or `enhancement`, chosen by Claude from the issue's words, and then `triaged`. That one step is marked `reasoned`: a replay still asks Claude there and pins everything else. |
| `closeout` | A watch. Each issue labelled `ready-to-close` is closed the way its labels say: `shipped` as completed, with a comment naming the release; `wontfix` as not planned; `duplicate` as a duplicate. Give `version=`. |
| `release` | Across three systems that know nothing of each other: queues a build of the release job on the build service, deploys the release to staging on the deploy service, and records it on GitHub. Give `version=`: 1.0 and 1.1 succeeded; 1.2 failed, so the deploy service doesn't offer it. |

- To replay `triage` or `closeout`, run `kit/bootstrap.sh` again first, so there are new issues for them.
- `kit/try.sh plan NAME` reads and checks a goal and sends nothing.

<details>
<summary>Diagram: how the <code>triage</code> watch decides each issue</summary>

```mermaid
flowchart TD
    A[Open the Triage watch issue] --> B{Watch issue still open?}
    B -- no --> Z[End]
    B -- yes --> C[Wait for an issue labelled needs-triage to change]
    C --> D{Already labelled triaged?}
    D -- yes --> B
    D -- no --> E[Claude reads the issue's words<br/>the one reasoned step]
    E --> F[Label it bug, question or enhancement]
    F --> G[Label it triaged] --> B
```

</details>

## Across systems

Beside the GitHub gateway (port 8392), the container runs two more HEART services, simulated and held in memory:

- a build service (8401), a narrow Jenkins;
- a deploy service (8402), a narrow cloud.

How `release` crosses them:

- It starts from the workspace, `http://localhost:8400/workspace.json`, a plain resource that links to GitHub, the
  release job and the web app.
- Crossing from one system to another is following a link.
- Each system gets only its own credential: your token goes to GitHub alone.

<details>
<summary>Diagram: the <code>release</code> goal across three systems</summary>

```mermaid
sequenceDiagram
    participant ARTIE
    participant WS as Workspace (8400)
    participant Build as Build service (8401)
    participant Deploy as Deploy service (8402)
    participant GH as GitHub gateway (8392)
    ARTIE->>WS: GET workspace.json
    WS-->>ARTIE: links to GitHub, the release job, the web app
    ARTIE->>Build: follow the release job, queue a build of {version}
    Build-->>ARTIE: 201, the build
    ARTIE->>Deploy: follow the web app, deploy {version} to an available region of staging
    alt the version's build succeeded
        Deploy-->>ARTIE: 201, the deployment
        ARTIE->>GH: open an issue titled "Release {version}"
        GH-->>ARTIE: 201, the issue
    else the build failed (1.2)
        Deploy-->>ARTIE: deploy not offered, and why
    end
```

</details>

## Watch it in a browser

```sh
kit/try.sh up      # the systems keep running, and keep their state, until kit/try.sh down
```

While the kit is up:

- Each command runs in it.
- Each system answers a browser on your machine (`127.0.0.1` only) with HTML: its state, a button for each act it
  offers now, and a greyed one, saying why, for each it won't.
- The addresses are printed. `KIT_PORTS="18392 18400 18401 18402"` moves them (gateway, workspace, build, deploy).
- A browser has to send each system its credential as `Authorization: Bearer …`:
  - your GitHub token to the gateway;
  - `build-token` and `deploy-token` to the build and deploy services.

## The release: GitHub or GitLab, Jenkins, and Dev

A larger trial, beside the goals above. A pull request for Riverside Public Library's renewal policy is:

1. watched until it can merge;
2. merged;
3. built by a real Jenkins;
4. smoke-tested at the preview Jenkins deployed;
5. promoted to Dev, or, when the smoke test fails, left out of Dev, with a comment on the pull request saying why.

What makes it work:

- One kept script does it every time, with no model. Only the pull request changes.
- The forge is GitHub or GitLab: the script asks for `scm:` relations, which the gateway serves over either.
- Every party speaks HEART.

```sh
kit/release.sh up              # builds the parties and Jenkins, and starts them (the first time, a few minutes)
kit/release.sh run github      # five pull requests at a small GitHub inside the kit
kit/release.sh up gitlab       # GitLab too: about 4 GB more memory, several minutes the first time
kit/release.sh run gitlab      # the same five as GitLab merge requests
kit/release.sh run github-live # the same five at OWNER/REPO on real GitHub
kit/release.sh reset           # the small GitHub and GitLab back as first seeded, runs cleared
kit/release.sh down
```

What to expect:

- Of the five, four are promoted to Dev.
- The fifth, "Renewals: none during the audit", fails the smoke test's renewal scenario and stays out.
- Each release's trace is in `work/runs/SCM/pr-N.yaml`.

About `run github-live`:

- It pushes `library/main` and `library/pr-501` to `library/pr-505` to your repository.
- It opens, merges and comments on pull requests between them. Your own `main` is never touched.
- It needs `OWNER`, `REPO` and `GITHUB_TOKEN` in `kit/.env`.

What answers a browser on this machine:

- While it is up: Jenkins on http://localhost:8171/ (user `artie`, the password in
  `exhibits/merge-watch-release/jenkins/jenkins.env.example`), and GitLab on http://localhost:8929/.
- While a release runs: the release desk (http://localhost:8160/) and Dev (http://localhost:8190/). Each release
  starts its parties afresh.

On Linux and in WSL, Docker needs its Compose plugin: `sudo apt install docker-compose-v2`.

<details>
<summary>Diagram: one release, from pull request to Dev</summary>

```mermaid
sequenceDiagram
    actor You
    participant Desk as Release desk (8160)
    participant ARTIE
    participant SCM as Forge gateway (scm:)
    participant Watch as Offer watcher (8099)
    participant CI as CI wrapper (8170)
    participant Jenkins
    participant Preview as Preview (8180)
    participant Dev as Dev (8190)
    You->>Desk: release pull request N
    Desk->>ARTIE: the kept script, with N
    ARTIE->>SCM: open pull request N
    ARTIE->>Watch: tell me when it offers scm:merge
    Watch-->>ARTIE: reply: scm:merge is offered
    ARTIE->>SCM: merge it by squashing
    SCM-->>ARTIE: the merge commit
    ARTIE->>CI: build that commit, tell me how it goes
    CI->>Jenkins: start the app job
    Jenkins->>Preview: deploy the build
    Jenkins-->>CI: webhook: build done
    CI-->>ARTIE: reply, linking the build
    ARTIE->>CI: follow the build: ci:smoke-test, ci:deployment
    ARTIE->>Preview: run the smoke script on the deployment
    alt every scenario passed
        ARTIE->>Dev: promote the commit, citing the scenarios passed
    else a scenario failed
        ARTIE->>SCM: scm:comment, "Not promoted to Dev: smoke scenarios … failed."
    end
    ARTIE-->>Desk: the trace
```

</details>

<details>
<summary>Diagram: the release's decisions</summary>

```mermaid
flowchart TD
    A[Pull request N] --> B{Offers scm:merge?}
    B -- not yet --> W[Watcher waits for the offer] --> B
    B -- yes --> C[Merge by squashing]
    C --> D[Jenkins builds the merge commit<br/>and deploys a preview]
    D --> E[Smoke script runs on the preview]
    E --> F{All scenarios pass?}
    F -- yes --> G[Promote to Dev]
    F -- no --> H[Comment on the pull request<br/>with the failed scenarios]
```

</details>

<details>
<summary>Diagram: what runs where in <code>kit/release.sh</code></summary>

```mermaid
flowchart LR
    subgraph net [One network: every party at localhost]
        R[heart-release container<br/>desk, watcher, CI wrapper, preview, Dev,<br/>small GitHub, both gateways]
        J[Jenkins 8171]
        G[GitLab 8929<br/>with up gitlab only]
    end
    You[Your browser and shell] -- 127.0.0.1 ports --> R
    R -- github-live only --> GH[(real GitHub)]
```

</details>

## Write your own

Copy a goal in `./work` and change its words:

- A line is one step.
- A step with sub-steps works inside the resource it led to.
- `` `as name` `` keeps that resource for later lines, read as `{name}` or `{name.number}`.
- The full syntax is in `client/README.md`.
- The GitHub gateway offers issues, pull requests, branches, commits to a branch, comments and labels, and nothing
  else of GitHub's API.

## Another repository

- `OWNER` and `REPO` name any repository your token can write to.
- A fork of this one works too: turn its issues on first, since a new fork has them off.
- With both unset, the bootstrap takes your fork from git's origin.

## When something stops

- **A walk stopped partway.** Claude finds the way afresh each walk, and now and then stops.
  - Close the watch issue it opened, if any ("Triage watch", "Close-out watch"), and walk the goal again.
  - A replay of a kept script runs the same way every time.
- **`missing input: …`**: give it as `name=value` after the goal's name.
- **`stopped … by its guard`**: the replay found a form that differs from the pins, and sent nothing. Walk the goal
  again and keep it, or replay with `--guard lenient`.
- **`GITHUB_TOKEN can't read …`**: check `OWNER`, `REPO`, and that the token was given that repository.
- **The build is killed**: give Docker more memory, in Docker Desktop's Settings, under Resources (on Windows, in
  `.wslconfig`).
- The gateway's log is `work/runs/gateway.log`.

## Known limits

- The kept `release` script reads the deploy region from the app's state, and marks that value for review.
- A `release` replay refused at the deploy has queued its build already.
- A browser sign-in for the build and deploy services is still to come.

The trial these goals were run in, with every run's output: `exhibits/kit-trial/`.
