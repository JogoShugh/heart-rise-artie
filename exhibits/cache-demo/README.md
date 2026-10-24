# ARTIE content-addressed cache demo

> **Historical (marked 2026-10-09).** This shows the design as of early
> October 2026. Since then the forms link carries `integrity`, schemas are
> referenced with `$ref` and `hr:integrity`, and the client reads nothing
> from a URL (the hash in the path is no longer checked). Current evidence:
> `server-spring/journal.html`, the client and server Cucumber suites, and
> `scripts/live-client-check.sh`. How to bring it up to date is in
> `hacklog.md`, "Old exhibits".

Proves the three-level scheme end to end with a real HTTP client, real
ETags, and a real `Cache-Control: immutable` server — no mocking, no
timing-based guessing. The client's dedup claims are checked against the
server's own authoritative request log, not eyeballed.

```
resource (ETag, always revalidated)
  -> affordance-set (content-addressed URL, immutable)
       -> schema x N (content-addressed URL, immutable, fetched in parallel)
```

## Layout

```
server.py               -- stdlib-only static server with correct cache headers + /__stats
kotlin-client/           -- Ktor + coroutines client that walks all three levels;
                            also where GenerateFixtures.kt (the `generateFixtures`
                            Gradle task) builds fixtures/ with real sha256-over-
                            RFC-8785-canonical-JSON content addressing
```

## Running it

**1. Generate the fixtures**, from `kotlin-client/`:

```bash
cd kotlin-client
gradle generateFixtures    # or ./gradlew generateFixtures, if you have a wrapper
```

This creates `fixtures/resources/bed-1.json` .. `bed-12.json` at the repo
root (the task pins its working directory there regardless of where you run
`gradle` from), each pointing at one of four affordance-sets (sizes 1, 2,
3, and 10), deliberately shared across multiple beds. Check
`fixtures/manifest.txt` to see exactly which bed maps to which set and
hash.

Layer 2 (affordance-sets) and Layer 3 (schemas) hashes are real SHA-256,
truncated to 16 hex chars, computed over each body's RFC 8785 JSON
Canonicalization Scheme (JCS) form — recursively sorted keys, no
insignificant whitespace — via `com.apicatalog:titanium-jcs`, per
SPEC.md §6.5. That's what makes the address a genuine content address: the
same logical JSON always hashes the same regardless of how it was
constructed, and the bytes on disk are exactly the bytes that were hashed.

**2. Start the server** (pure Python 3 stdlib, no deps):

```bash
python3 server.py 8080
```

Leave this running in its own terminal — it prints a line for every
request it receives (`REQ #n METHOD path -> status`), which is your
ground truth for whatever the client claims.

**3. Run the Kotlin client**, in a second terminal:

```bash
cd kotlin-client
./gradlew run    # or: gradle run, if you don't want the wrapper
```

If port 8080 is taken, start the server on another port and point the
client at it:

```bash
ARTIE_BASE_URL=http://localhost:18080 gradle run
```

(No `gradlew` wrapper jar is checked in here — run `gradle wrapper` once
first if you want one, or just use your local `gradle` install directly.)

## What to look for

The client walks 12 bed syncs (with bed-1 synced twice, once at the start
and once again at the end) and fans out schema fetches in parallel within
each sync. At the end it fetches `/__stats` from the server and checks two
things against that log, not against its own assumptions:

1. **Every affordance-set and schema path was actually requested exactly
   once at the network level**, even though several beds reference the
   same set, and several sets reference the same schema (`sowSeed` is
   referenced by all four sets, for instance). If Ktor's `HttpCache` is
   doing its job with the `immutable, max-age=31536000` headers the server
   sends, the second, third, and later references are served from local
   cache and never touch the wire — so the server's own count stays at 1.

2. **`bed-1`'s resource path was requested twice**, since resource state
   uses `Cache-Control: no-cache` and is always revalidated — but the
   *second* request should come back as `304 Not Modified`, not a fresh
   `200`, because nothing about bed-1's state or affordance-set changed
   between the first and second sync.

Both checks print `PASS`/`FAIL` per path. A fully green run is the actual
proof that hash-in-URL content addressing collapses your custom hash
registry into ordinary HTTP caching — the client never had to be told
"you already have this," it just never asked twice.

## Notes / things worth verifying on your machine

- Verified end to end on 2026-10-03 (macOS, Docker Desktop, JDK 21):
  `docker build -f Dockerfile.origin` succeeds (the Gradle stage resolves
  `titanium-jcs` and `jakarta.json` and lands `fixtures/` where
  `server.py` expects it), and the Kotlin client run against that
  container reports ALL CHECKS PASSED: every content-addressed path
  requested exactly once, and bed-1 revalidated as `[200, 304]`. So the
  resolved Ktor `HttpCache` honors `max-age`/`immutable` as assumed.
- Every affordance-set/schema filename is the true first-16-hex-chars
  SHA-256 of its own file's bytes, and keys come out recursively sorted
  with no insignificant whitespace (JCS working as intended).
- Gradle 8.13/8.14 cannot run on JDK 25. Use JDK 17 or 21 for
  `generateFixtures` and `run`.
- SHA-256 truncated to 16 hex chars (64 bits) gives readable filenames for
  a demo; SPEC.md §6.5 explicitly allows this but recommends the full
  digest for production use, to keep full collision resistance at scale.
- `Jcs`/titanium-jcs's canonicalization is number-format-sensitive per
  RFC 8785 (ECMAScript-style number-to-string rules) — the schema/set
  builders in `GenerateFixtures.kt` intentionally use `Int` for whole
  numbers (`5`, not `5.0`) and `Double` only where the source data is
  actually fractional (`0.5`), to keep output matching what a human
  reading the original JSON Schema would expect.
- The server's in-memory request log resets every time you restart it —
  restart the server between client runs if you want a clean count.
