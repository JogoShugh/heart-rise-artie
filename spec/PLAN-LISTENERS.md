# Plan: listeners, not one loop

Status: chosen 2026-10-24. Slices 0, 1, 2, 3 and 8 done; see "As built" at the end.

## The problem

A watch today is one loop on one thread (`Watching.kt`, `StepWatch.kt`). Each look reads the watched list, visits
every member that changed, runs what is due, then sleeps (`run.pause`, 15 seconds by default). A `once` wait does
not block: it is left for the next look. But everything shares that one loop:

- Every listener shares one cadence.
- Anything slow in a look holds up every other listener: a model call, a slow server, a retry, a gateway 500.
- The loop reads one kind of source, a poll. It can't also take a webhook, an event stream, or a mail inbox.

Some listeners must react quickly. One example: a comment by veracode-bot on a pull request saying "failed" should
label the pull request, email its author, and ask CI to run again. It must not wait behind another listener's
handler.

## The ideas

- **Events are hints, never truth.** That rule stays. A hint says "this resource may have changed". The listener
  reads the resource again and acts only on what it offers now, with its validator.
- **Sources are separate from listeners.** A source produces hints: a poll, a webhook, an event stream, a mail
  inbox. A listener matches hints and runs its handler. N listeners on one pull request share one poll of it.
- **Each listener runs on its own.** A slow handler holds up only itself.
- **Decide once, replay.** That stays (spec/PLAN-WATCH-GOALS.md). A listener's match and acts are decided by the
  model the first time they are needed, then replayed with no model, so a thousand events cost no model calls.

## The runtime: Kotlin coroutines

- **Structured concurrency.** A watch is one `coroutineScope`, and each source and each listener is a child of it.
  Until cancels the scope, which ends every child cleanly. A `SupervisorJob` keeps a failed listener from ending its
  siblings; the failure is noted and the listener starts again.
- **A bus of hints.** A `SharedFlow<Hint>`. Each hint carries an address, a kind (a label, a comment, a state, a
  check), an actor (a bot or a person), an id and a time.
- **A listener collects from the bus.** `bus.filter(match).collect { handle(it) }`, with a buffer of its own. A full
  buffer folds hints for the same address into one, since one "read it again" is enough.
- **Give up is a timeout.** `withTimeoutOrNull(giveUp)` around waiting for a hint that matches. Otherwise runs on
  the timeout. There is no sleeping loop, and thousands of waits are thousands of suspended coroutines, not threads.
- **One handler at a time per resource.** A `Mutex` per address, so two handlers never race on one pull request,
  while different resources run in parallel.
- **Limits.** A `Semaphore` per host caps calls, for GitHub's rate limit. Model calls have a cap of their own.
- **Deciding is serialised.** Two listeners may need the same step decided at the same moment. One lock per step
  makes the second wait for the first's decision, then replay it.

## Non-blocking IO

- **First, virtual threads.** A dispatcher over Java 21 virtual threads
  (`Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()`). Today's blocking navigator, HTTP client
  and model calls run unchanged, one virtual thread per listener. Most of the gain, with little change.
- **Then suspend on the hot paths.** `HttpClient.sendAsync(...).await()` in the navigator and the poller, so
  `act`, `follow` and `read` become `suspend`. Model calls likewise.
- **Time.** `run.clock` and `run.pause` become a coroutine test scheduler in the features (virtual time), so a
  10-minute give-up runs in milliseconds.

## Sources

- **Poll.** One poller per watched resource, each at its own cadence (a comment thread every 5 seconds, the pull
  request list every minute), with `If-None-Match`. On GitHub a 304 doesn't count against the rate limit. This
  needs the gateway to give list resources an ETag, a known gap.
- **Webhook.** A small HTTP server in the run receives a POST, checks its HMAC signature, and puts a hint on the
  bus. GitHub can't reach a laptop, so this comes through a tunnel or the gateway.
- **The gateway as the hub.** The gateway polls once, or takes the webhook, and serves an event stream (SSE) per
  resource or relation. Every HEART client subscribes the same way: events from any server, made uniform by HEART.
- **Others.** A mail inbox (IMAP IDLE) and a timer are sources too.

## Acts beyond GitHub

- A handler acts on any resource: a HEART server, by what it offers; a plain HTTP server, through a gateway made
  from its OpenAPI description, as GitHub is; email, through a gateway that offers a mailbox resource with a send
  act.
- **Idempotency.** Each act carries a key, the hint's id and the listener, kept in the journal, so a webhook
  delivered twice doesn't act twice. This generalises today's `finished` set.

## The goal words

```
- Whenever a comment by veracode-bot on a pull request labelled {label} says "failed" `as c`
  - Label {c.pr} "security-hold".
  - Email {owner} "Veracode failed on #{c.pr.number}".
  - Ask CI to run again for {c.pr.head}.
- Whenever someone removes "security-hold" from a pull request `as pr`
  - Once it can be merged, merge it. Or give up after 1 hour. Otherwise, say why.
```

- Each top-level Whenever is its own listener, and they run at the same time.
- New words in a match: `by <actor>` (a bot, a person, or a team) and `says "<text>"`.
- `manners: alongside-others` holds inside every handler.

## The journal, and resuming

- An append-only journal: each hint taken, each act done, each wait opened with its deadline.
- On a restart, open waits resume with the time they have left, and hints already handled are skipped.
- It replaces the full trace for long watches, and the `--remember` memory.

## Slices

Each slice starts with its feature, seen red at test level, then made green.

0. **The Otherwise chain** (found by the live run g11, 2026-10-24). A step under a Whenever is decided as a chain
   of acts, not one act: "say why, unless it is said already" is a read, a check and a write. The planner's acts
   are kept in order until it says done, an act that opens is followed by the acts inside it, and a check in the
   chain leaves the acts after it undone, for this resource, when it holds. The whole chain is replayed. A chain
   cut short by its check is decided further at the next resource that needs it. No act the planner makes is
   dropped without being kept or refused.
1. **Listeners on their own coroutines.** Each top-level Whenever runs as its own coroutine on a virtual-thread
   dispatcher, with a mutex per resource. The feature: a slow handler on one listener doesn't delay another.
2. **The bus and a polling source**, with ETags; list ETags in the gateway.
3. **Give up as a timeout**, on virtual time; Otherwise on the timeout.
4. **The words `by` and `says`**, and listening to a comment thread.
5. **The journal and resuming**, with idempotency keys.
6. **The gateway as hub:** an event stream per resource, and webhooks taken in with HMAC.
7. **Acts on other servers:** a mail gateway, and a plain server through its OpenAPI gateway.
8. **`artie generate`** writes a decided run as a model-free script with listeners (spec/PLAN-WATCH-GOALS.md,
   slice 4c).
9. **Live.** The Dependabot simulation and a stand-in veracode-bot commenting on the scratch repository. N
   listeners, and the time each takes to react, against today's single loop.

## Risks

- **Order.** Hints may come out of order. A handler reads the resource as it is now, so order doesn't matter.
- **Rate limits.** Listeners share sources; none polls on its own.
- **Deciding at once.** Two listeners deciding the same step: one lock per step.

## As built

- **Threads, not coroutines (slice 1).** Each listener and each poll is a Java 21 virtual thread
  (`Listening.kt`). Today's blocking navigator runs unchanged; that was most of the gain the coroutine plan promised.
- **Hints from what a look read (slice 2, `Hints.kt`).** A listener names no source. The watch polls each resource
  its last look read (the list, each member, the start), one poll per address however many listeners read it, at the
  quickest pace any of them asks. A listener is hinted only when a resource it read now shows something else than it
  last saw there, and looks again then: a list that stays the same is never looked at again, however often it is
  polled. Pages of search results carry an ETag in every binding, so each poll of an unchanged list is a 304.
- **Time (`Time.kt`).** The watch's threads wait on the run's `time`: the real clock, or, in the features, a
  virtual time that moves only once every thread of the watch waits, so a one-hour give-up takes no real time and
  no thread finds more time passed than the others let pass.
- **Give up as a timeout (slice 3).** A `once` that waits keeps when it gives up (`Watching.ends`); its listener
  wakes then, whether or not anything it read changed, and its Otherwise runs at that time, not at a later look.
