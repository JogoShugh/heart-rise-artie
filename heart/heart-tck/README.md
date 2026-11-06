# heart-tck

The HEART conformance features (TCK: technology compatibility kit). Written
once, run by every server against itself. If they pass, the server is HEART
compliant over HTTP.

## Run it against your server

1. `testImplementation("com.artie.heart:heart-tck")`.
2. Register a `TckFixture` bean:

   ```kotlin
   object : TckFixture {
       override val collection = "beds"
       override val offered = TckAct("garden:water-cell", "water-cell",
           body = """{"milliliters": 1}""", invalidBody = """{"milliliters": 0}""")
       override val absent = TckAct("garden:sow-seed", "sow-seed",
           body = """{"seedType": "kale"}""", reason = "The bed is already planted.")
       override val searchTerm = "Bed 2"
       // Only if the bookmark offers an act for the type:
       // override val bookmarkAct = TckAct("home:install-lamp", "install-lamp", body = ..., invalidBody = ...)
       override suspend fun ready(): Long { /* reset data, prepare bed 2 */ return 2 }
   }
   ```

3. Run Cucumber on `classpath:heart/tck` with glue
   `com.artie.heart.tck` (plus your fixture's package), in a context with a
   `WebTestClient`, `TckWorld`, and your `HeartResource` beans. See
   `server-spring/.../tck/RunHeartTckTest.kt`.
4. If your server's bookmark offers no act, add the tag
   filter `not @bookmark-act` (Cucumber's `cucumber.filter.tags`).

## What the 18 scenarios check

L0 with inline schemas and concrete targets; L1 and L2 behind
integrity-checked `hr:forms` links with templated targets; 304; the absence
explanation and its reason; a successful act (`Content-Location`, new ETag);
428, 412, 409 and 422 as problems that change nothing; an act performed on
the state its ETag matched, refused as stale when another client's act
landed after the check (the domain's `perform` called directly, since the
gap cannot be timed over HTTP); the same targets in
HAL-FORMS and HAL Schema Forms; the index, search and JSON Home; every form
relation described in the ALPS profile. The four `@bookmark-act` scenarios: the
index offers the act as a form on its own `/actions/{path}`; it answers 201
with the new resource in `Location`, `Content-Location` and `self`, readable
at that location; and 428, 412 and 422 refuse it as problems that add no
resource.

## Who runs it

The lamp (`src/test`, a server that declares one type and writes no HTTP
code) passes all 18. The garden (`server-spring`) and basketball
(`domains/basketball`) have no act on the bookmark, and pass the other 14.
