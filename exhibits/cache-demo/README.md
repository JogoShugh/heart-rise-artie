# ARTIE content-addressed cache demo

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
generate_fixtures.sh   -- builds fixtures/ with real sha256-based content addressing
server.py               -- stdlib-only static server with correct cache headers + /__stats
kotlin-client/           -- Ktor + coroutines client that walks all three levels
```

## Running it

**1. Generate the fixtures** (pure bash + `sha256sum`, no deps):

```bash
./generate_fixtures.sh
```

This creates `fixtures/resources/bed-1.json` .. `bed-12.json`, each pointing
at one of four affordance-sets (sizes 1, 2, 3, and 10), deliberately shared
across multiple beds. Check `fixtures/manifest.txt` to see exactly which
bed maps to which set and hash.

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

- I built and dry-ran `generate_fixtures.sh` and `server.py` in a sandbox
  and confirmed real sha256 hashing, correct sharing across beds, and
  correct 200/304/immutable header behavior via `curl`. I could not run
  the Kotlin client end-to-end here (no network egress in this
  environment, and no local JVM/Gradle), so give the Ktor `HttpCache`
  behavior a first real run and watch for surprises — in particular,
  confirm your Ktor version's `HttpCache` plugin honors `max-age` for
  skip-the-network behavior the way I've assumed (RFC 7234-compliant
  caches should, but worth confirming against whatever Ktor version
  Gradle resolves).
- `sha256sum | cut -c1-16` gives you 16 hex chars (64 bits) of the real
  hash for readable filenames — for anything beyond a demo, use the full
  digest to avoid collision risk at scale.
- The server's in-memory request log resets every time you restart it —
  restart the server between client runs if you want a clean count.
