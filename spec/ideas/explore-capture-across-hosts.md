# Explore, capture, replay, across hosts

An idea, written 2026-10-18 from a conversation with the author. It builds on
`captured-scripts.md` (explore once with a model, replay forever without one) and on plan
steps 4 and 5 in `spec/PLAN-ARTIE-SCRIPTS.md` (trace capture, then script synthesis). It adds
three things those leave open:

- how a captured script gets its shape, nested or linear;
- what exploring over many steps needs;
- what changes when one task crosses several hosts.

Nothing is decided and nothing is built.

## The author's thinking

A script written by hand in advance may not know how the resources behave: which acts make
a new resource, where a step returns to, what comes back. A script captured from a run can
see all of that as it happens, and can write it into the nested form. So the author of a
script may choose:

- to write it by hand, nested, up front;
- to write it by hand, linear;
- not to write it at all: run in an explore-and-capture mode, where a reasoner does the task.
  The runner logs every step. Only after the whole run has finished does it write the
  `.kts` script, which later runs use without inference, with the parameters it deduced
  supplied up front.

## 1. What a capture can deduce

### Its shape

Every trace step already records the representation it was chosen from (`held`) and the
address it ended at. From that order of addresses:

- **A step whose run later comes back** to the resource it started from is a block: visit,
  then return. In heart-script, `act(...) { ... }`.
- **A step the run never comes back from** is a move: `act(...)` with no block.
- **A new resource** (201 and a Location) that the run then works on is a block on that act.

So the nesting is not something the author has to design up front. It falls out of the
order of addresses the run visited.

### Where each value came from

This matters most. Every value a step sends is tagged at capture time by its source:

| Source of the value | In the captured script |
|---|---|
| The person's words ("basil", "issue 10") | An input. This run's value becomes its default. |
| The server's state (an id, a Location, a field read earlier) | Read again at run time, never written in. |
| A constant offered by the form (a default, a single choice) | A literal. |
| The model's judgement ("about 2 cm" became `depthCm = 2`) | The author chooses: freeze it as a literal, make it an input, or keep a `reason()` step there (hybrid). |

`artie run --inputs-of` on the captured script then lists exactly the parameters a later
run can supply up front. That is the goal: runs without inference, with parameters deduced
from the exploration.

### Its expectations

Each step's outcome (done, new resource, refused with a reason) becomes that step's
`expect`, so a later run that turns out differently stops and says where.

### What it must not capture

Addresses and redirections. HEART follows Location, Content-Location and links fresh on
every run. A captured script holds relations, value sources and expectations, never URLs.
That is why a replay survives moved resources and changed titles (`captured-scripts.md`
covers guarding against changed forms).

## 2. What one run can't tell

A single trace is one example, and a program generalised from one example is under-determined:

- **Branches not taken.** A run that found the form offered can't say what to do when it is
  not offered.
- **Loops and lists look the same.** A run that watered three beds doesn't say whether the
  intent was "every unplanted bed" or "these three".
- **Exploration noise.** Dead ends, a refusal followed by a corrected retry, a re-read after
  a 412: none of these belong in the replay. Capture must remove them. It keeps a refusal
  only when the run meant it, as an `expect`.

Ways to close the gap, cheapest first:

1. **The reasoner states its scope with each choice.** Alongside the step, it says why in a
   fixed shape: "for each bed where cropName is absent". Capture turns that into a `members`
   loop with a condition, not into three literal steps.
2. **A person confirms** the slots, loops and judgement calls before the script is kept, as
   plan step 5 already requires.
3. **Several explorations merged.** Two runs that took different branches give the branch.

## 3. A loop that repairs itself

1. The captured script runs without a model.
2. A step comes back not offered, or a form's schema has changed, or its vocabulary is gone.
   The step stops with the server's reason.
3. The runner explores again with the reasoner, from that step only, using the originating
   words.
4. Capture writes a new version of the script, recording why the old one stopped.

Most runs cost nothing. A run pays for inference only where the service changed.

## 4. Exploring over many steps

Today `reason()` decides one step per call (decided 2026-10-17). Exploring is a loop around
that, and the loop needs:

- **A goal test.** The reasoner says the task is done, and a `check` written from the
  originating words confirms it, or a person does.
- **A budget.** A maximum number of steps, a time limit and a cost limit, each reported
  when reached.
- **Safety for acts.** Reads are safe; acts are real. Exploring live GitHub posts real
  comments. The choices:
  - explore against a scratch copy (the scratch repository, a fake service, the garden);
  - ask a person before every act;
  - allow acts only on relations named up front, and reads everywhere.
- **No cycling.** It stops if it reads the same address twice with nothing changing between
  the two reads.

HEART makes this safer than exploring an ordinary API. Acts are explicit and conditional
(`If-Match`). An act not offered is refused before it is sent. `hr:unavailable` tells the
explorer why an act is missing, so it doesn't probe by trying.

## 5. Across hosts

One task may need several services: sign in to GitHub for a few requests, then Jenkins for
several, then Splunk for more. Each sits behind its own HEART gateway, with its own bookmark
and its own credentials.

### When one host links to another

For example, a GitHub check run's `details_url` points at a Jenkins build. Crossing hosts is
then just following a link, as the web always has. ARTIE already:

- keeps credentials per origin (`~/.artie/credentials`);
- picks the binding by the URI scheme;
- follows absolute links to another host.

One addition makes these links HEART all the way: a connector rule that rewrites a known
outside URL (a Jenkins build URL in GitHub's answer) into the Jenkins gateway's resource for
that build.

### When the hosts aren't linked

For example: take a pull request's head commit, find the Jenkins build for it, then search
Splunk over that build's time window. Nothing links the three. Only values move between them:

- the script holds several bookmarks, and values read from one host's state fill another
  host's forms;
- capture tags such a value as "read from GitHub's state at step 3", so the replay reads it
  again rather than writing it in.

### What changes once work crosses hosts

- **No shared transaction.** GitHub may accept a comment while Jenkins refuses a job. Every
  act needs either an idempotency key or a compensating step written next to it. The trace
  shows partial success openly, host by host.
- **One entry point, kept.** A small workspace resource of the author's own, linking to each
  host's bookmark, keeps the single-bookmark principle: the hosts become links, not
  configuration.
- **Long-running work looks the same everywhere.** A Jenkins queue item and a Splunk search
  job have the same shape: an act that makes a new resource, then `until` it finishes. The
  uniform interface pays off here: one way of handling refusals and one way of waiting,
  across three vendors.

### What exists and what's missing

| Need | State |
|---|---|
| Credentials per origin | Exists |
| Binding by URI scheme | Exists |
| Following an absolute link to another host | Exists |
| Several bookmarks in one script, values passed between them | Missing |
| Trace tag for which host a value came from | Missing |
| Connector rewriting of outside URLs | Missing |
| Policy for partial failure | Missing |

## 6. Suggested order

1. Capture on one host: plan steps 4 and 5 on the garden, then GitHub.
2. The Jenkins gateway (`jenkins-gateway.md`). GitHub plus Jenkins, linked through check
   runs, is the first task across hosts.
3. Splunk, the first host linked by values only.

## 7. The theory, in one paragraph

A hypermedia run is monadic: each next step depends on the last answer. Capture freezes one
run into a fixed program with slots, which is why the branches the run never took are lost,
and why tagging where each value came from is what keeps the frozen program honest. A host is
just one more interpreter of the same steps, so crossing hosts changes the credentials and
the failure policy, not the language.
