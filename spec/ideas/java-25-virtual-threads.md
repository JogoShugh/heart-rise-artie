# Idea: Java 25, http4k 6, virtual threads proved

**Status:** idea only, 2026-10-17. Not decided or built; the author is open
to Java 25 and beyond ("this is MY project").

## Why

The http4k binding, the gateway, the CoAP edge, ARTIE and the MCP server
block a thread per request: http4k handlers are synchronous and call the
suspend core with `runBlocking`; the gateway calls GitHub with http4k's
`JavaHttpClient`; ARTIE uses `HttpClient.send`. That is the right model on
virtual threads, where a blocked thread parks and frees its carrier, so no
one needs coroutines in http4k. Two things keep it from being proven:

- heart is on http4k 5.47 and Java 21. Whether `Jetty(port)`, as the gateway
  starts it, runs handlers on virtual threads has not been checked.
- Java 21 pins a virtual thread's carrier inside `synchronized`; JEP 491
  (Java 24) removed that.

## What it would take

- Every build to Java 25 (LTS, installed: `25.3.4+1.r25-graal`), with
  Kotlin's `jvmToolchain(25)`; first confirm Kotlin 2.3 and http4k 6
  support 25 as a target.
- heart to http4k 6, built around virtual threads.
- A test that proves it: inside a gateway handler,
  `Thread.currentThread().isVirtual` is true; else configure Jetty's thread
  pool for virtual threads explicitly.
- ARTIE and the script DSL stay blocking: scripts are sequential, and
  concurrency, where wanted, runs on virtual threads.
