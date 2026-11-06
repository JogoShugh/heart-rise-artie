# Manual review plan: October 8 to 11, 2026

This plan is for one purpose: so you can look at what was built over these
four days with your own eyes, understand it, and decide whether to trust it.
No new code is written while you follow it. Passing tests are not the
evidence here. You are.

It takes about six working days, at two to three hours a day. Each day says
what to do, what you should see, and what should worry you. Keep a notebook
open the whole time and write down every place where what you see does not
match what you expected. Those notes are the real output of this plan.

## The size of it

From October 8 to October 11 there were 80 commits on `oct-branch`. Together
they added about 22,800 lines and removed about 6,400. That is far more than
one person can review line by line, so this plan does not try to. It works
from the outside in: first what the system does when you poke it, then what
the tests claim, then whether those claims are true, and only then the code.

The work falls into six storylines:

1. **The spec, October 8 and 9.** HAL-FORMS as the easy entry for clients,
   the service index as the bookmark, JSON Home, the order in which an act
   is refused (428, then 412, then 409, then 422), and strong If-Match.
2. **The garden server and the ARTIE client, October 10 morning.** Refusals
   as RFC 9457 problem details, forms named by relation, explaining why an
   act is absent, the W3C traceparent header, and ARTIE's event log.
3. **Buster, the chaos tester.** A filter that mutates the server's answers,
   and campaigns that check ARTIE notices every mutation.
4. **Any protocol.** A protocol-neutral core, with HTTP and CoAP as two
   bindings, and the first run with a real Claude model as the reasoner.
5. **The HEART library, October 10 evening.** The garden server's insides
   pulled out into a reusable library in nine steps: heart-core,
   heart-spring, heart-coap, heart-eventsourcing, and the heart-tck
   conformance suite every server runs against itself.
6. **Domains on the library, October 10 night and October 11.** Youth
   basketball, support tickets, acts on the bookmark instead of a create
   operation, feature files that test only their own domain, the explorer
   client, and refactor mode in the TDD gate.

## Day 1: the map, with no code

The goal today is to be able to say each storyline back in your own words.

1. Read the root `README.md`.
2. Read the first screen of `spec/SPEC.md`, its status line, not the whole
   thing.
3. Read the hack log, `hacklog.md`, from the October 8 entries to the end.
   This is the closest thing to a diary of why each decision was made.
4. Read `heart/README.md` and `spec/ARCHITECTURE.md`.
5. Run `git log --since=2026-10-08 --oneline` and read every subject line.
   Each one is meant to say what changed in plain words.

For each of the six storylines, write one or two sentences in your
notebook, in your words. Wherever you cannot, mark it. That is where you dig
later. Also write down anything in the hack log you do not remember agreeing
to.

What should worry you: a decision in the hack log you did not make, or a
storyline you cannot explain after reading about it.

## Day 2: watch one server with your own eyes

Use the support tickets server. It is the newest and the smallest, and it
sits on top of everything else, so seeing it work exercises the whole stack.
Every command below was run on October 11 and gave the answers described.

Start it, in one terminal:

```
cd ~/repos/artie
docker compose -f server-spring/docker-compose.yml up -d postgres
cd domains/tickets
./gradlew bootRun
```

Wait for "Started TicketsApplicationKt". Then, in a second terminal, put the
seed data in. That is ticket 1, new, and ticket 2, waiting on the customer:

```
cd ~/repos/artie/domains/tickets && ./reset-db.sh
```

Now walk it with curl. Read every header and every body, not just the
status codes.

**The bookmark.** `curl -i localhost:8282/`

You should see the service index in HAL-FORMS, with three forms: find
tickets by subject, get a ticket by number, and open a ticket. The open form
lists customer, description, priority and subject, with the four priorities
as options. Note the ETag; it starts with `index-`.

**A ticket.** `curl -i localhost:8282/tickets/1 -H 'Accept: application/hal+json'`

You should see the ticket's state (subject "Cannot log in", status NEW, no
agent), an ETag of `"seq-1.hsf.L0"`, and a `_forms` object holding exactly
assign, comment, and change priority. There is also an `hr:unavailable`
link: that resource says why the other acts are absent. Follow it with curl
and read the reasons.

**The same ticket, linked rather than inlined.** Add
`-H 'Prefer: forms=link, schema=link'`. The body now has no forms, only an
`hr:forms` link with an integrity hash, and the response says
`Preference-Applied: forms=link, schema=link`.

**Every refusal, in order.** Send these, and read each problem body:

```
J='Content-Type: application/json'; H='Accept: application/hal+json'
# No If-Match: 428, precondition-required
curl -i -X POST localhost:8282/tickets/1/actions/assign -H "$H" -H "$J" -d '{"agent":"priya"}'
# A stale If-Match: 412, stale
curl -i -X POST localhost:8282/tickets/1/actions/assign -H "$H" -H "$J" -H 'If-Match: "seq-9.hsf.L0"' -d '{"agent":"priya"}'
# An act not offered while NEW: 409, not-offered, naming the relation
curl -i -X POST localhost:8282/tickets/1/actions/resolve -H "$H" -H "$J" -H 'If-Match: "seq-1.hsf.L0"' -d '{"resolution":"x"}'
# An agent who does not exist: 422, invalid-input, at /agent
curl -i -X POST localhost:8282/tickets/1/actions/assign -H "$H" -H "$J" -H 'If-Match: "seq-1.hsf.L0"' -d '{"agent":"bob"}'
```

**An act that works.** The same assign with `"priya"` and If-Match
`"seq-1.hsf.L0"` answers 200, status ASSIGNED, agent priya, ETag
`"seq-2.hsf.L0"`, and `Content-Location: /tickets/1`. Send it again with
`"seq-2.hsf.L0"`: 422 at `/agent`, because she already has it. That is a
ticket rule, not a schema rule.

**A new ticket, with no create operation.** Get the index's ETag in the
HAL Schema Forms dialect, then send the open-ticket act with it:

```
curl -s -D - -o /dev/null localhost:8282/ -H "$H" | grep -i etag
curl -i -X POST localhost:8282/actions/open-ticket -H "$H" -H "$J" -H 'If-Match: <that etag>' \
  -d '{"subject":"Printer on fire","customer":"Kim","description":"Smoke.","priority":"urgent"}'
```

You should get 201, `Location: /tickets/3`, and the new ticket.

**The past.** `curl localhost:8282/tickets/2/history -H "$H"` lists ticket
2's four events in order: opened, assigned to marco, an internal comment,
and a question to the customer.

**The database.** Look at what is really stored:

```
docker compose -f ~/repos/artie/server-spring/docker-compose.yml exec postgres \
  psql -U artie -d tickets -c "SELECT stream_id, sequence_no, event_type, payload FROM heart_event ORDER BY 1, 2"
```

Every act you sent that succeeded is one row. Every refused act left no
row. Check that with your own eyes; it is the most important property of
the whole design.

When done, stop the server with Ctrl-C and run `./reset-db.sh` again.

What should worry you: any act refused in a different order than 428, 412,
409, 422; any refused act that left a row; a form offered that the ticket's
status should not allow.

## Day 3: read the tests as promises, then break the code

The goal today is to know what the tests actually say, and to prove for
yourself that they catch real mistakes.

**Read the ticket rules.** Open
`domains/tickets/src/test/resources/features/tickets.feature`. It has nine
scenarios, and each one is a sentence in plain English, such as "A ticket is
not assigned again to the agent who has it". For each one, say out loud
what it promises. Compare it with what you saw on Day 2.

**Check that the steps really check.** Open
`domains/tickets/src/test/kotlin/com/artie/tickets/bdd/TicketSteps.kt`.
Pick three scenarios. For each "Then" line, find its step and confirm it
asserts the exact thing the sentence says, not something weaker. A step
that says "the game offers exactly" must compare the whole set, not check
that one item is present.

**Read the conformance suite.** Open
`heart/heart-tck/src/main/resources/heart/tck/conformance.feature`. These
are the rules every HEART server must keep: the layers, 304 revalidation,
every refusal, both dialects, the bookmark, the profile, and acts on the
bookmark. Each domain runs them against itself in its `RunHeartTckTest`.

**Break the code on purpose.** This is the best way to trust tests you did
not write. Make each change below by hand, run the tests, and confirm a
test fails for the right reason. Then undo it with `git checkout <file>`.
Do not commit any of these.

1. In `domains/tickets/src/main/kotlin/com/artie/tickets/declaration/Tickets.kt`
   at line 34, remove `"comment"` from the NEW list. Run
   `./gradlew test` in `domains/tickets`. Expect a scenario about NEW
   tickets to fail.
2. In `domains/tickets/src/main/kotlin/com/artie/tickets/domain/TicketService.kt`,
   change `requireOtherAgent` so it never refuses. Expect "A ticket is not
   assigned again to the agent who has it" to fail.
3. In `heart/heart-core`, find where the act checks run in order, and swap
   the 409 check with the 412 check. Run `./gradlew test` in `heart`.
   Expect the conformance and heart-spring scenarios about refusal order to
   fail.

Write down each change and whether a test caught it. If a change you
believe is a real bug passes every test, that is the most valuable finding
of the week.

The test reports are HTML, in each module's `build/reports/tests/test/index.html`,
and the Cucumber results are listed there by scenario name.

What should worry you: a scenario sentence that promises more than its step
checks, or a deliberate bug no test catches.

## Day 4: the library, one request at a time

The goal today is to follow one request through the code, start to finish,
and see that nothing is hidden.

Read `heart/ARCHITECTURE.md` again, then follow a POST to
`/tickets/1/actions/assign`:

1. heart-spring's `HeartResourceHandler` receives it.
2. heart-core runs the checks, in order: is there an If-Match (428), does it
   match the current ETag (412), is the act offered in this state (409), and
   does the body satisfy the act's schema (422).
3. Only then does the domain's `perform` run: `TicketActs`, then
   `TicketService.record`.
4. heart-eventsourcing's `EventStore` appends the event at the next number,
   and the new state is folded from the events.
5. heart-core renders the new state at the client's rung and dialect.

Use your editor's "go to definition" to walk it. At each step, write down
which file and function you are in. If any step does something you did not
expect, write it down.

Then look at what a domain has to write, against what the library gives it.
The whole tickets domain is about 600 lines in 12 files. Read all of them;
it is short enough. Ask yourself whether each file holds only ticket rules.

Finally, run the other two servers the same way as Day 2, briefly:

- The garden: `server-spring`, port 8080. See its README.
- Basketball: `domains/basketball`, port 8181. See its README.

What should worry you: any place where a domain re-implements something the
library should do, or where the library knows about a specific domain.

## Day 5: the client, the chaos tester, and the gate

**ARTIE.** ARTIE is the client that drives a HEART server only by following
its links and forms. Run its live scenarios against the garden:

```
cd ~/repos/artie && scripts/live-client-check.sh
```

It starts the garden if needed, seeds it, and runs the client's `@live`
scenarios. Then read ARTIE's event log at
`server-spring/build/artie-events.jsonl`. Each line is something ARTIE
decided and why. Read twenty lines and check they make sense against the
scenario names.

**Buster.** Buster mutates the server's answers, to prove ARTIE notices
when something is wrong. Run a campaign on the tickets server:

```
cd ~/repos/artie && scripts/buster-campaign.sh tickets
```

It takes several minutes. At the end it prints how many mutations it made,
how many ARTIE caught, and any holes. The full report is
`domains/tickets/build/campaign-report.json`, and the round logs are next
to it. Pick three mutations from the report, and for each one, find in the
round log how ARTIE noticed it.

**The TDD gate.** Every change Claude makes is logged in
`.claude/tdd-events.log`: feature file written, production code written,
test run passed or failed. Open it and read the October 11 entries. You
should see, for each feature, a feature write, then a failing run, then
production writes, then a passing run. Then run
`git log --grep Refactor-Mode` to see the three refactor commits, each with
the reason you typed.

What should worry you: an ARTIE event that does not explain its decision, a
Buster hole, or a production commit whose events are not in that order.

## Day 6: decide

Read your notebook. For each storyline, decide one of three things:

- **Trusted.** You saw it work, you know what its tests promise, and you
  broke it and the tests caught it.
- **Trusted with doubts.** Write the doubt down as a question.
- **Not trusted.** Write down what would change your mind, or whether it
  should be removed.

Only after this should new work (A7, the renamed-vocabulary overlay) start,
and only once every "not trusted" item has an answer.

## Things noticed while preparing this plan

These came up while the Day 2 commands were being checked. None has been
fixed. They are here for you to judge.

1. **A domain refusal carries the schema's title.** When a ticket is
   assigned again to the same agent, the 422 says "The body does not match
   the form's schema", although the body did match: it was a ticket rule
   that refused it. The violation at `/agent` says the real reason.
2. **The server logs no requests.** Its log shows startup and nothing else,
   so you cannot watch traffic there. To see traffic, use `curl -i`, ARTIE's
   event log, or Buster's log.
3. **The index's HAL-FORMS templates have no `default` key.** A resource's
   first template is keyed `default`, as the HAL-FORMS spec suggests, but
   the index names all its templates. Decide whether the index should
   follow the same rule.
4. **The heart README's "Past" section predates `EventSourcing.past`.** It
   still shows building `Past` by hand, which works, but no longer matches
   what the three servers do.

## Asking Claude while you review

Ask Claude to explain anything, to find where something lives, or to run a
command and show its output. Do not ask it to change code during this
review, unless a finding needs a fix and you decide so. Then it is a
separate piece of work, with its own feature first.
