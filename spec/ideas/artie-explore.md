# Idea: artie explore, a person walking a service by hand, with or without a model to say it kindly

Status: idea, 2026-11-01. From "The True Test" (ARTIE against Spring HATEOAS's own HAL-FORMS example): to write
the script, the template and relation names were first read from the service with curl. ARTIE could have shown
them. Today `artie run --interactive` only answers a script's `input(question = …)` prompts; the ARTIE dashboard
(exhibits/openapi-vs-heart/artie-dashboard) walks a service by hand, but as an exhibit web page, in HAL Schema
Forms only.

## What

`artie explore <url> [--dialect hal-forms|hal-schema-forms] [--rung L0|L1|L2] [--trace FILE]`: a terminal
session on ARTIE's deterministic core. No reasoning, no script: a person enters at a starting point and goes on by
what each representation offers.

At each step it shows, from the representation just read, never from knowledge of the service:

| Part | Shown as |
|---|---|
| State | its members and values |
| Links | each relation, with its title when it has one |
| Embedded resources | each relation, with its title and state, followable |
| Forms | each form's name, method and target, and each field: type, required or not, limits, choices (resolved now, with their prompts) |
| Why absent | the reason the service gives for an act it doesn't offer now, when it gives one |

And it takes:

| Command | Does |
|---|---|
| `follow <relation> [title]` | follows a link or an embedded resource |
| `act <form> field=value …` | fills and submits a form; ARTIE checks the fields against the form's schema and choices first, and nothing is sent that they disallow |
| `act <form>` | asks for each field in turn: its type, whether it is required, its choices |
| `back` | returns to the resource before |
| `again` | reads the held resource again |
| `why <form>` | asks the service why the form is absent |
| `quit` | ends the session |

Every refusal is said as ARTIE says it in a run, and the session carries on from what the service answered.

## Trace to script

With `--trace`, the session writes the same trace a run does. `artie generate` can then make a script from it:
the walk a person did by hand becomes the script that does it again (spec/PLAN-SCRIPT-GENERATOR.md). This is how
"The True Test"'s script would have been written without curl.

## Sub-flag: friendly prompts from the reasoner (`--reasoner claude`, or `--friendly`)

The plain session shows boilerplate: field names, types, `required`, the raw choice values. With the sub-flag, the
reasoner turns each step's representation into words for a person: what the resource is, what can be done now
and why some things can't, and, when acting, one question per field in plain language ("What role should Frodo
have now?"), using the form's titles, descriptions, prompts and the ALPS profile where the service gives them.

The reasoner only words; it chooses nothing:

- What is shown, what may be done, and what is sent come from the deterministic core, as without the flag.
- The person's answers are still checked against the form before anything is sent; a model's wording never
  becomes a field value on its own.
- The boilerplate stays one keystroke away, so the person can always see what the service itself said.

### Measuring it

The same walk, with and without the sub-flag, is a measure of what the friendly words cost:

| Measure | From |
|---|---|
| Model calls per step | the trace (as goal runs record them, spec/PLAN-LOCAL-MODEL.md) |
| Tokens in and out per step, and per session | the model's usage, in the trace |
| Time per step | the trace |
| The same with a local model | `--model` and `--model-url`, as for goal runs |

Set beside "Reason on Rails" (goal, intent, kept and bare runs), it adds one rung: a model that neither plans nor
picks acts, but only speaks, with what that costs per step.

## First steps

1. `artie explore` with no model, Gherkin first in artie-cli, against the scripted service the CLI's features use.
2. `--trace`, and `artie generate` from an explore trace.
3. The friendly sub-flag, with a fake reasoner in the features and the token counts in the trace.
4. One measured session each way against the simulated services (heart-sims) and against Spring's example.
