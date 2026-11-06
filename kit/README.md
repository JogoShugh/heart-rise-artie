# Try ARTIE on your own GitHub

ARTIE is a generic client for HEART, the way a browser is a generic client for HTML. This kit runs it against
GitHub, through a HEART gateway that reads GitHub's own API description and offers, for each issue and pull request,
only the acts that apply to it now. Everything runs in one Docker container on your machine, and GitHub is called
with your own token.

Your fork of this repository is what it works on. The bootstrap fills the fork with issues about the repository's
own code; the goals then triage them, close them, open pull requests, all as you'd see a person do on GitHub.

A goal is a job written in plain words. Claude walks it once, choosing among what each resource offers. `artie`
keeps that walk as a script in YAML, and the script then runs again with no model, held strictly to what the walk
was offered.

## Start

1. **Fork this repository** on GitHub, and clone your fork: `gh repo fork JogoShugh/heart-rise-artie --clone`.
   Turn on its issues, which a new fork has off: `gh repo edit <you>/heart-rise-artie --enable-issues`.
2. **Make a fine-grained token** for your fork alone, with Contents, Issues and Pull requests set to read and write.
3. **Have Docker running**, and an Anthropic API key for the goals Claude walks.
4. **Bootstrap**, from your clone's root. The first run builds the image, which takes a few minutes.

```sh
export GITHUB_TOKEN=…            # the fine-grained token
kit/bootstrap.sh                 # six issues in your fork, opened by a kept script with no model
```

The tokens are passed to the container when it starts, never written to disk or into the image.

## Then

```sh
export OWNER=<you> REPO=heart-rise-artie ANTHROPIC_API_KEY=…
kit/try.sh walk triage           # Claude labels the three issues waiting for triage
kit/try.sh keep triage           # triage.heart.yaml and triage.pins.yaml, from that walk
kit/try.sh replay triage         # the same watch again: Claude only at its one reasoned step
kit/try.sh walk closeout version=1.0
kit/try.sh walk hello
```

A watch keeps going until you close the issue it opened ("Triage watch", "Close-out watch") on GitHub.
`kit/try.sh plan NAME` reads and checks a goal and sends nothing. Everything lands in `./work`: the goals, which you
can edit, the kept scripts, their lock files, and a trace of every run in `work/runs/`.

## The goals

| Goal | What it does |
|---|---|
| `seed` | What the bootstrap ran: six issues, three labelled `needs-triage`, three `ready-to-close` with one of `shipped`, `wontfix` or `duplicate`. Kept as `seed.heart.yaml`, so it runs with no model. |
| `triage` | A watch. Each issue labelled `needs-triage` gets one label, `bug`, `question` or `enhancement`, chosen by Claude from the issue's words, and then `triaged`. That one step is marked `reasoned`, so a replay still asks Claude there and pins everything else. |
| `closeout` | A watch. Each issue labelled `ready-to-close` is closed the way its labels say: `shipped` as completed, with a comment naming the release; `wontfix` as not planned; `duplicate` as a duplicate. Each case is decided once on the walk and kept as facts on the issue's labels, so the replay needs no model. Give `version=`. |
| `hello` | Opens an issue, comments on it and closes it as completed. |
| `release` | Across three systems that know nothing of each other: queues a build of the release job on the build service, deploys the release to an available region of staging on the deploy service, and records it on GitHub. Give `version=`: 1.0 and 1.1 succeeded; 1.2 failed, so the deploy service doesn't offer it. |

## Across systems

Beside the GitHub gateway, the container runs two more HEART services, simulated and held in memory: a build
service, a narrow Jenkins with jobs and builds, on `http://localhost:8401`; and a deploy service, a narrow cloud with
an app, its regions and releases, on `http://localhost:8402`. Each run of the kit starts them afresh.

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

`OWNER` and `REPO` name any repository your token can write to: `OWNER=you REPO=scratch kit/bootstrap.sh`.

## Known limits

- The kept `triage` watch matches an issue whose labels are exactly `needs-triage`; one with another label besides
  isn't triaged by the replay.
- The kept `release` script reads the deploy region from the app's state, and marks that value for review.

The trial these goals were run in, with every run's output: `exhibits/kit-trial/`.

## When something stops

- `missing input: …`: give it as `name=value` after the goal's name.
- `stopped … by its guard`: the replay found a form that differs from the lock file, and sent nothing. Walk the goal
  again and keep it, or replay with `--guard lenient`.
- The gateway's log is `work/runs/gateway.log`.
