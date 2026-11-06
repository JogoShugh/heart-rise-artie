# Try ARTIE on your own GitHub

ARTIE is a generic client for HEART, the way a browser is a generic client for HTML. This kit runs it against
GitHub, through a HEART gateway that reads GitHub's own API description and offers, for each issue and pull request,
only the acts that apply to it now. Everything runs in one Docker container on your machine, and GitHub is called
with your own token.

A repository of yours is what it works on: an empty one is enough. The bootstrap fills it with issues about this
project's code; the goals then triage them and close them, as you'd see a person do on GitHub, and one goal ships a
release across GitHub and two more systems that run in the kit.

A goal is a job written in plain words. Claude walks it once, choosing among what each resource offers. `artie`
keeps that walk as a script in YAML, and the script then runs again with no model, held strictly to what the walk
was offered.

## Start, on a Mac or Linux machine

You need Docker running (Docker Desktop with 8 GB of memory, the default on most Macs), git, and two keys.

1. **Clone this repository:** `git clone https://github.com/JogoShugh/heart-rise-artie.git && cd heart-rise-artie`.
2. **Make an empty repository** of your own on GitHub for the kit to work on, `kit-trial` say. Its issues are on, as
   in any new repository; it needs no files.
3. **Make a fine-grained token** at github.com/settings/personal-access-tokens/new: repository access "Only select
   repositories", that one; permissions Issues, Pull requests and Contents, read and write.
4. **Have an Anthropic API key** (console.anthropic.com), for the goals Claude walks.
5. **Put them in `kit/.env`:** `cp kit/env.example kit/.env`, then fill in `OWNER`, `REPO`, `GITHUB_TOKEN` and
   `ANTHROPIC_API_KEY`. Git ignores the file and Docker builds never see it; the keys go to the kit's container when
   it starts, never into the image. Anything you export instead wins over the file.
6. **Bootstrap:** `kit/bootstrap.sh`. The first run builds the kit's image, about ten minutes; then six issues appear
   in your repository, opened by a kept script with no model.

## Then

```sh
kit/try.sh walk triage           # Claude labels the three issues waiting for triage
kit/try.sh keep triage           # triage.heart.yaml and triage.pins.yaml, from that walk
kit/try.sh walk closeout version=1.0
kit/try.sh walk hello
kit/try.sh walk release version=1.1
```

`triage` and `closeout` are watches: each opens an issue ("Triage watch", "Close-out watch") and keeps watching until
you close it on GitHub. Close it once the issues it works on are done; the run ends within a few seconds. Replay a
kept goal with `kit/try.sh replay NAME`, after a second `kit/bootstrap.sh` gives triage and closeout new issues.

`kit/try.sh plan NAME` reads and checks a goal and sends nothing. Everything lands in `./work`: the goals, which you
can edit, the kept scripts, their lock files, and for every run its trace, with each request and answer, and a
report of it to open in a browser: `work/runs/NAME/run.html`.

## Watch it in a browser

```sh
kit/try.sh up                    # the systems keep running, and keep their state, until kit/try.sh down
```

While the kit is up, each command runs in it, so the build and deploy services keep what earlier commands did, and
each system answers a browser on your machine (`127.0.0.1` only) with HTML: its state, a button for each act it
offers now, and a greyed one, saying why, for each it won't. The addresses are printed; `KIT_PORTS` moves them
(`KIT_PORTS="18392 18400 18401 18402"`), in the order gateway, workspace, build, deploy. A browser has to send each
system its credential, as `Authorization: Bearer …`: your GitHub token to the gateway, `build-token` and
`deploy-token` to the build and deploy services. A browser sign-in for them is still to come.

## The goals

| Goal | What it does |
|---|---|
| `seed` | What the bootstrap ran: six issues, three labelled `needs-triage` (one also `documentation`), three `ready-to-close` with one of `shipped`, `wontfix` or `duplicate`. Kept as `seed.heart.yaml`, so it runs with no model. |
| `triage` | A watch. Each issue with `needs-triage` among its labels gets one label, `bug`, `question` or `enhancement`, chosen by Claude from the issue's words, and then `triaged`. That one step is marked `reasoned`, so a replay still asks Claude there and pins everything else. |
| `closeout` | A watch. Each issue labelled `ready-to-close` is closed the way its labels say: `shipped` as completed, with a comment naming the release; `wontfix` as not planned; `duplicate` as a duplicate. Each case is decided once on the walk and kept as facts on the issue's labels, so the replay needs no model. Give `version=`. |
| `hello` | Opens an issue, comments on it and closes it as completed. |
| `release` | Across three systems that know nothing of each other: queues a build of the release job on the build service, deploys the release to an available region of staging on the deploy service, and records it on GitHub. Give `version=`: 1.0 and 1.1 succeeded; 1.2 failed, so the deploy service doesn't offer it. |

## Across systems

Beside the GitHub gateway, the container runs two more HEART services, simulated and held in memory: a build
service, a narrow Jenkins with jobs and builds, on `http://localhost:8401`; and a deploy service, a narrow cloud with
an app, its regions and releases, on `http://localhost:8402`. Each command starts them afresh, unless the kit is up.

A goal starts from one address. `release` starts from the workspace, `http://localhost:8400/workspace.json`, a plain
file that links to GitHub, the release job and the web app (`kit/workspace/`). Crossing from one system to another
is following a link. Each system gets only its own credential: your token goes to GitHub alone.

## Write your own

Copy a goal in `./work` and change its words. A line is one step; a step with sub-steps works inside the resource it
led to; `` `as name` `` keeps that resource for later lines, read as `{name}` or `{name.number}`. The full syntax is
in `client/README.md`.

The GitHub gateway offers issues, pull requests, branches, commits to a branch, comments and labels, and nothing
else of GitHub's API; a goal that needs something else finds it isn't offered.

## Another repository

`OWNER` and `REPO` name any repository your token can write to; a fork of this one works too (turn its issues on
first: a new fork has them off). Leave both unset and the bootstrap takes your fork from git's origin.

## Known limits

- The kept `release` script reads the deploy region from the app's state, and marks that value for review.
- A `release` replay refused at the deploy has queued its build already.

The trial these goals were run in, with every run's output: `exhibits/kit-trial/`.

## When something stops

- `missing input: …`: give it as `name=value` after the goal's name.
- `stopped … by its guard`: the replay found a form that differs from the lock file, and sent nothing. Walk the goal
  again and keep it, or replay with `--guard lenient`.
- The gateway's log is `work/runs/gateway.log`.
