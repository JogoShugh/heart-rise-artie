# ARTIE

ARTIE is a generic agent for any HEART server, the way a browser is a generic client for any website. It reads
what a server answers, follows the links it offers and fills the forms it offers, and it knows nothing about any
one service. `artie` is its command line: it runs a goal written in plain English, a script kept from such a run,
or a bare script that calls an ordinary API directly, and it writes the scripts from the runs.

This README documents every option of `artie run` and `artie generate`, with one job as the example throughout:
the merge watch in `exhibits/merge-watch/`.

## The modules

| Module | What it is |
|---|---|
| `heart-script` | The scripting language: `heart { act(…) follow(…) }`, waits and watches, facts, and the bare vocabulary |
| `artie` | The client: HTTP and CoAP bindings, caching, validators, credentials, the reasoner, the guards |
| `artie-cli` | The `artie` command: `artie run` and `artie generate` |
| `heart-script-host` | Compiles and runs `.kts` scripts |
| `heart-script-test` | Expectations a script can state about a run, for tests written as scripts |

## Build and set up

ARTIE builds with Java 21 and runs on Java 21 or later.

```sh
cd client
JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.12-graal ./gradlew :artie-cli:installDist
. scripts/.env          # puts artie on the PATH and reads the token and model key from where they live
```

`scripts/.env` holds no secret itself. It reads each one, when sourced, from where it already lives.

**Credentials** are by origin, never in a script. ARTIE looks in three places, in order:

1. Environment variables named by origin: `https://localhost:8392` reads `ARTIE_TOKEN_LOCALHOST_8392` (a bearer
   token), or `ARTIE_USER_…` and `ARTIE_PASSWORD_…` (Basic). The scheme is not in the name.
2. `~/.artie/credentials`, one section per origin, readable by its owner only:
   ```ini
   [https://localhost:8392]
   bearer = …
   ```
3. Vault, when `ARTIE_VAULT_ADDR` is set.

**A local gateway over HTTPS** needs its certificate trusted:

```sh
export JAVA_OPTS="-Djavax.net.ssl.trustStore=$HOME/.cache/artie/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"
```

Without it, a run against `https://localhost:8392` stops with "PKIX path building failed".

**The model**, for `--reasoner claude`, reads `ANTHROPIC_API_KEY`, or else the bearer entry for
`https://api.anthropic.com` in the credentials, as for any origin.

## From plain text to rails

One job can be run at five levels, each made from the one before by `artie generate`, from a single recorded run.
Going down, the model does less and the script says more; what stays the same is the person's words.

| Level | File | Who decides each step | Model on every run |
|---|---|---|---|
| 1. The goal, in plain text | `merge.goal.md` | The model, reading each line | Every step |
| 2. The run in words | `merge.intent.kts` | The model, picking from what is offered; the shape is code | Every act |
| 3. Relations, with their words | `merge.kts` | The script; held to its lock file | Only a `reasoned` line |
| 4. The same script, at a level of care | `merge.kts --guard …` | The script, trusting more or less | Only a `reasoned` line |
| 5. Bare | `merge.bare.kts` | The script, at fixed addresses, no checks | Only a `reasoned` line |

The same act at each level:

```kotlin
// 1. merge.goal.md
//    - Once it can be merged, merge it by squashing.

// 2. merge.intent.kts
act(intent = "merge it by squashing.") {}

// 3. merge.kts
act("github:merge", "merge_method" to "squash", intent = "merge it by squashing.") {}

// 5. merge.bare.kts
once("PUT", "https://api.github.com/repos/${owner}/${repo}/pulls/${member["number"].text}/merge",
    mapOf("merge_method" to "squash", "sha" to member["head"]["sha"].text), giveUp = 3.minutes, ...)
```

**Whose words.** An act's words say what it is for, and whose they are:

| An act | Carries |
|---|---|
| does one of a goal's steps | `intent = "merge it by squashing."`, the person's words |
| serves a goal line that names no act: `In {owner}/{repo}`, a Whenever, or an act on the way to a step's job | `inferred = "get the repository $owner/$repo", fromIntent = "In ${owner}/${repo}"` |
| has no goal line at all: a goal in plain prose | `inferred = "close it"` |

- `intent`: a person's words, from a line of the goal or written in a script, each input as the variable the script
  reads.
- `inferred`: words a model wrote while it ran, each input's value put back as its variable.
- `fromIntent`: the person's words that inferred words serve. It is kept for the reader, and never reasoned on alone.

With no relation, the reasoner is handed the intent, or else the inferred words.

## The merge watch, start to finish

The merge watch is one job, run every way ARTIE can run it. A HEART gateway in front of GitHub
(`heart/heart-gateway`, at `https://localhost:8392`) serves the repository; everything below is run from
`exhibits/merge-watch/`.

### 1. The goal, in plain English

`merge.goal.md`:

```markdown
---
start: https://localhost:8392/
inputs:
  owner: { question: "Whose repository?" }
  repo:  { default: heart-github-test, question: "Which repository?" }
  label: { default: dependencies, question: "Which label marks a dependency bump?" }
  hold:  { default: hold, question: "Which label means a person objects?" }
---

Merge dependency bumps once they can be merged. Keep watching until the watch's issue is closed.

- In {owner}/{repo}
  - Open an issue titled "Merge watch", saying "Close this issue to stop the watch." `as watch`
  - Whenever a pull request labelled {label} changes `as pr`
    - If it's labelled {hold}, leave it, and say so once.
    - Once it can be merged, merge it by squashing.
      Or give up after 3 minutes.
      Otherwise, tell its author in one friendly sentence why it wasn't merged and what to do next. `reasoned`
- Until someone closes {watch}
```

The header names the entry point and the inputs. Each `{name}` is an input. The marks in backquotes are ARTIE's:
`as watch` names what a line made, so a later line can refer to it, and `reasoned` marks a line whose words the
model writes every time.

Check how ARTIE reads it, with nothing sent:

```sh
artie run merge.goal.md --plan-only
```
```
start https://localhost:8392/
inputs owner, repo, label, hold
line 13:   Open an issue titled "Merge watch", saying "Close this issue to stop the watch." (as watch)
line 14:   whenever: a pull request labelled {label} changes (as pr)
line 15:     if: it's labelled {hold}, then: leave it, and say so once.
line 16:     once: it can be merged, then: merge it by squashing.
line 17:       give up after 3 minutes
line 18:       otherwise: tell its author in one friendly sentence why it wasn't merged and what to do next.
line 19: until: someone closes {watch}
a goal in steps: 6 steps, nothing sent
```

### 2. Run the goal: wayfinding

The model reads each line and picks, from the links and forms the gateway offers at that moment, the step that
does what the line says.

```sh
artie run merge.goal.md owner=JogoShugh --reasoner claude --rung L2 --exchanges --trace runs/live-6.yaml
```

The watch runs until someone closes its issue. `--trace` keeps every step; `--exchanges` keeps each request and
answer in it, which `artie generate --bare` needs later.

### 3. Keep the run in words, then the script it amounts to

```sh
artie generate runs/live-6.yaml --script merge.goal.md --intents --out merge.intent.kts
artie generate runs/live-6.yaml --script merge.goal.md --out merge.kts
```

The first keeps the run's shape as code, each act as its words alone, for the reasoner to choose on every run:

```kotlin
act(inferred = "get the repository $owner/$repo", fromIntent = "In ${owner}/${repo}") {
    act(intent = """Open an issue titled "Merge watch", saying "Close this issue to stop the watch."""") {
        keepAs("watch")
    }
}
watch(until = { at("watch") { "state" eq "closed" } }) {
    act(inferred = "list pull requests of ${owner}/${repo} filtered by label ${label}", fromIntent = "a pull request labelled ${label} changes") {
        whenever {
            has { "github:labels" has label }
            leaveWhere("leave it, and say so once.") { "github:labels" has hold }
            once("github:merge", giveUp = 3.minutes, otherwise = { why -> act(intent = "tell its author ...") }) {
                act(intent = "merge it by squashing.") {}
            }
        }
    }
}
```

The second fixes each choice the run made.

This writes three files:

- `merge.kts`: the script, with every choice the run made and no model, except for the reasoned line.
- `merge.pins.yaml`: the lock file. For each relation the script uses, it records the integrity of the form it
  was offered, its method and the sets it came in, plus the digest of every form the server declared.
- `merge.derivation.yaml`: for each line of the script, which step of the run it came from, and which rule.

The heart of `merge.kts`:

```kotlin
act("github:repos-get", "owner" to owner, "repo" to repo, inferred = "get the repository $owner/$repo", fromIntent = "In ${owner}/${repo}") {
    act("github:open-issue", intent = """Open an issue titled "Merge watch", saying "Close this issue to stop the watch."""", fields = {
        "title" to "Merge watch"
        "body" to "Close this issue to stop the watch."
    }) {
        keepAs("watch")
    }
}
watch(until = { at("watch") { "state" eq "closed" } }) {
    act("github:pulls-list", "owner" to owner, "repo" to repo, "labels" to label, inferred = "list pull requests of ${owner}/${repo} filtered by label ${label}", fromIntent = "a pull request labelled ${label} changes") {
        whenever {
            has { "github:labels" has label }
            leaveWhere("leave it, and say so once.") { "github:labels" has hold }
            once("github:merge", giveUp = 3.minutes, otherwise = { why ->
                act(intent = "tell its author in one friendly sentence why it wasn't merged ... ${why ?: "no reason given"}")
            }) {
                act("github:merge", "merge_method" to "squash", intent = "merge it by squashing.") {}
            }
        }
    }
}
```

Every act carries words beside its relation: the goal's own, or the model's with the goal line they serve.

List a script's inputs without running it:

```sh
artie run merge.kts --inputs-of
```
```
owner: a question, "Whose repository?"
repo: optional, default "heart-github-test"
label: optional, default "dependencies"
hold: optional, default "hold"
```

### 4. Run the kept script, at the level of care you choose

```sh
artie run merge.kts owner=JogoShugh --reasoner claude                           # lenient, the default
artie run merge.kts owner=JogoShugh --reasoner claude --guard strict
artie run merge.kts owner=JogoShugh --reasoner claude --guard look-then-leap    # --look used, the default
artie run merge.kts owner=JogoShugh --reasoner claude --guard look-then-leap --look everything
artie run merge.kts owner=JogoShugh --reasoner claude --guard off-the-rails
```

`--reasoner claude` is there only for the reasoned line, the note to an author. Without it, that step is
refused, and the run says where the model key goes.

See [Guards](#guards) for what each level checks.

### 5. Bring it down to bare calls

A bare script calls the service directly, at fixed addresses, with no gateway and no checks. It is generated
from a goal run kept with `--exchanges`, and the gateway's record of the calls it made to GitHub
(`GATEWAY_RECORD`):

```sh
artie generate runs/live-6.yaml --bare runs/live-6.upstream.yaml --out merge.bare.kts
artie run merge.bare.kts owner=JogoShugh --reasoner claude
```

Bare is fast, and only as safe as the service's own rules. On the scratch repository, bare merged a pull request
one commit behind its base, which GitHub calls clean. Every guard level through the gateway held it back: the
gateway's own rule withholds merge while a branch is behind.

## `artie run`

```
artie run <script> [name=value …] [options]
```

`<script>` is a goal (`.goal.md`), a kept script (`.kts`) or a bare script (`.bare.kts`), in the current folder.

### Inputs

| Option | What it does |
|---|---|
| `name=value` | Gives the input `name`. Repeat it for each one. |
| `--inputs FILE` | Reads inputs from a JSON or YAML file. Values given on the line win. |
| `--interactive` | Asks a person for each input the script still needs, and for each `ask(…)` in it. Without it, a missing input stops the run before its first request. |
| `--inputs-of` | Lists the script's inputs, and how many questions it asks inline, without running it. |
| `--plan-only` | For a goal: reads, checks and prints it, with nothing sent. |

### Where and how it reads

| Option | What it does |
|---|---|
| `--entry URL` | Starts at another entry point than the one the script or goal names: the same script against another server. |
| `--rung L0\|L1\|L2` | Which rung of HEART the client asks for. L0 gets forms and schemas inline; L1 and L2 get them by link, shared and cached. A kept script reads at the rung its lock file records. |
| `--reasoner claude` | Allows the steps that need a model: every step of a goal, and a script's reasoned lines. |
| `--remember FILE` | For a watch: keeps what it did in FILE, so that run again, it doesn't do it again (a comment already made, a "left" already said). |

### Guards

How carefully a kept script holds to its lock file, from most to least careful:

| `--guard` | What it checks |
|---|---|
| `strict` | Everything `lenient` checks, plus the whole set of forms each act came in. Anything not recorded is refused. For audited work. |
| `lenient` | The default. Before each act: that it's offered, that the body fits the form's schema, and that the form is the one pinned. Reads again after acting. A changed form it uses is refused; a change elsewhere is not. |
| `look-then-leap` | One look at the server's manifest of forms at the start. If nothing it compares has changed, the rest of the run is off the rails; otherwise lenient. It says which, in the run's notes. |
| `off-the-rails` | No pins, no schema check, no reading again; recorded addresses are fetched directly. It still sends the ETag of the last answer, a 412 stops the run, and the server still enforces its own rules. Needs no lock file. |

| Option | What it does |
|---|---|
| `--look used\|everything` | What `look-then-leap` compares. `used`, the default: each form the lock file pins, against the integrity the server lists for it. `everything`: the digest of every form the server declares, against the one the lock file kept. |
| `--validators last\|none` | What each act carries in `If-Match`. `last`, the default: the ETag of the last answer for that resource. `none`: no validator, a blind write, which a HEART server refuses unless its development switch allows it. |
| `--pins FILE` | Holds to another lock file than `NAME.pins.yaml` beside the script. |
| `--config FILE` | Reads another config than `NAME.config.yaml` beside the script. |
| `--no-repin` | Leaves the lock file as it is, whatever the config says. |

**The config file**, `NAME.config.yaml` beside the script, set by a person and never written by ARTIE. A flag on
the command line wins over it.

```yaml
guard: lenient          # strict, lenient, look-then-leap or off-the-rails
look: used              # used or everything
on-change: refuse       # refuse, or values: go past a changed form when it keeps its method and what is sent fits it
repin: never            # never, propose (say what would change), or auto (write it into the lock file)
validators: last        # last or none
```

### What the run writes

| Option | What it does |
|---|---|
| `--trace FILE` | Writes the trace to FILE instead of `runs/NAME-<time>.trace.yaml`, where every run writes one. It holds every step: what was asked, the values sent and where each came from, the answer, the guard's check, and the run's inputs. A goal run's trace also keeps the script its choices amount to. YAML. |
| `--exchanges` | Keeps each request and answer in the trace, with its `traceparent`, so the gateway's record of its upstream calls can be joined to the step. |
| `--report DIR` | Builds an HTML report of the run in DIR, from the trace (needs the report builder installed beside `artie`). |
| `--quiet` | Prints only the last line. |
| `--output json` | Prints the outcome as one JSON object. |

By default, `artie run` prints one line per step and a last line saying how the run ended, such as
`finished, 252 steps`.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Finished, or listed (`--inputs-of`, `--plan-only`) |
| 1 | A check or an expectation in the script was not met |
| 2 | Stopped before the first request: bad arguments, a missing input, no lock file for `--guard`, a rung other than the lock file's |
| 3 | Refused by the server or by the guard |
| 4 | The client failed: the network, TLS, a server error |
| 5 | The script has an error |

## `artie generate`

```
artie generate <capture.yaml> --out <script.kts> [--script <source>] [--keep-reason] [--intents] [--inputs a,b] [--report <dir>]
artie generate <capture.yaml> --bare <upstream.yaml> --out <script.bare.kts>
```

`<capture.yaml>` is a run's trace, from `artie run --trace`.

| Option | What it does |
|---|---|
| `--out FILE` | The script to write. Beside it, `NAME.pins.yaml` and `NAME.derivation.yaml`. |
| `--script FILE` | The goal or script the run ran. With a goal, the generated script follows its lines and keeps its words as each act's `intent`. |
| `--keep-reason` | Keeps each reasoned step as a reasoned step, instead of writing down the choice the model made. |
| `--intents` | Writes the run in words only: its shape as code, each act and follow as `act(intent = "…")` or `act(inferred = "…")`, for the reasoner to choose on every run. A watch's acts too; what `once` waits for stays named. |
| `--inputs a,b` | Makes the values the run sent under these fields into inputs, defaulting to what was sent. |
| `--report DIR` | Builds a derivation report in DIR: the source and the generated script side by side, each derivation numbered on both (needs `--script`). |
| `--bare FILE` | Writes a bare script instead, from the gateway's upstream record FILE: each act the call it came down to, at a fixed address. A run that watched becomes a bare watch with the same words. Needs a capture kept with `--exchanges`. |

A generated script prints what to review: `wrote merge.kts: 0 values to review` means every value came from an
input or an earlier answer. A value the model chose, with nothing to read it from, is marked `review:` in the
script.

## Further reading

- `spec/PLAN-ARTIE-SCRIPTS.md`: the scripting language and `artie run`
- `spec/PLAN-SCRIPT-GENERATOR.md`: `artie generate` and lock files
- `spec/PLAN-GOAL-MARKDOWN.md`: goals in plain English
- `spec/ideas/replay-guards-lenient-strict.md` and `spec/ideas/off-the-rails.md`: the guards
- `spec/ideas/bare-runs.md`: bare scripts
- `docs/reports/`: the reports on these runs, on GitHub Pages
