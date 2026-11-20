# Inline, Then Linked: A Conversation About the Ladder

Josh and Dev talk about HEART's two dialects and its rendering ladder, L0 to L3, as if it were a website: HAL-FORMS as the on-ramp that keeps everything inline, and HAL Schema Forms, which moves the forms and then the schemas out into their own linked, cacheable resources, the way a site moves its CSS and scripts out of the HTML. Then the integrity that makes sharing safe. About fifteen minutes read aloud.

The HEART samples are real responses from the garden server, captured on 13 October 2026 and trimmed with "…". The speakers say what each one shows.

---

## Everything inline

**Dev:** You know "Save page as, single file"? You get one HTML file with everything inside. The styles are inlined, the scripts are inlined, and even the images are pasted in as long strings of base64.

**Josh:** I used to email those around. One file, opens anywhere, nothing missing.

**Dev:** And nothing shared. Save ten pages from the same site and you've got ten copies of the same stylesheet.

**Josh:** That's L0 in HEART. One response, everything inside. And the garden can give it to you in two dialects. Here's bed 2 in the first one, HAL-FORMS.

```json
{
  "id": 2, "status": "PLANTED", "cropName": "tomato", "moistureLevel": 40,
  "_links": { "self": { "href": "/beds/2" } },
  "_templates": {
    "waterCell": {
      "title": "Water the bed",
      "method": "POST",
      "target": "/beds/2/actions/water-cell",
      "properties": [
        { "name": "milliliters", "prompt": "Water per cell", "required": true, "type": "number", "min": 1, "max": 1000 }
      ]
    }
  }
}
```

**Dev:** In words: bed 2 is planted with tomatoes, moisture 40. Under "templates" there's one form, "waterCell", titled "Water the bed". Post it to the bed's water-cell address. It has one property, millilitres, required, between 1 and 1000.

**Josh:** 417 bytes. Now the same bed, same moment, in the second dialect, HAL Schema Forms.

```json
{
  "id": 2, "status": "PLANTED", "cropName": "tomato", "moistureLevel": 40,
  "_links": { "self": { "href": "/beds/2" }, "hr:unavailable": { …, "integrity": "sha256-…" }, … },
  "_forms": {
    "garden:water-cell": {
      "title": "Water the bed",
      "description": "Pours water into every cell of a planted bed. Enough water makes it ready to harvest.",
      "_links": { "target": { "href": "/beds/2/actions/water-cell" } },
      "method": "POST",
      "schema": { "properties": { "milliliters": { "type": "integer", "minimum": 1, "maximum": 1000, … } }, … }
    }
  }
}
```

**Dev:** In words: the same state, the same one act. But the act is named by a link relation, "garden colon water-cell", it has a description of what watering does, and the fields are a full JSON Schema. Plus a link explaining what's not offered, with a hash next to it.

**Josh:** 1,089 bytes. Same legal act, same state, different vocabulary.

**Dev:** And the important part holds in both: the water act is there because watering is legal now.

**Josh:** Right. When the bed is ready, water disappears and harvest shows up, in either dialect. Absence means something even at L0.

## Why HAL-FORMS is the on-ramp

**Dev:** So why two? I know HAL-FORMS. Spring HATEOAS does it out of the box.

**Josh:** That's exactly why it's here. A Spring team can turn on HAL-FORMS and get a usable L0 nearly for free. The spec calls HAL-FORMS the on-ramp dialect.

**Dev:** On-ramp sounds like "toy."

**Josh:** It isn't. At L0, HAL-FORMS covers the service's front door, search and lookup, every act, conditional requests, and the same legal acts as the other dialect. A client could live there.

**Dev:** You said "nearly" for free.

**Josh:** We found the catch. Spring HATEOAS builds the HAL-FORMS fields by reflecting on each act's input class. In Kotlin, every `val` came out as read-only. And an optional text field came out as a number range up to 2,147,483,647.

**Dev:** So the forms said "you can't fill in these fields."

**Josh:** For basketball and tickets, yes. Only the garden dodged it, because its input classes happened to use `var`. So now our library renders HAL-FORMS itself, from each act's schema, and Spring HATEOAS is gone from the library and the garden. There's even a test that forbids it in garden code.

**Dev:** So the dialect stayed, and the free generator went.

**Josh:** Right. The point of HAL-FORMS is that other people's tools speak it. Ours just has to speak it correctly.

## Where HAL-FORMS stops

**Dev:** And it stays at L0?

**Josh:** Always. I tried it this morning. I asked the HAL-FORMS bed for L1, then L2, with the `Prefer` header. Both times I got the same 417 bytes back, and no `Preference-Applied` header.

**Dev:** So it didn't error. It just said "here's L0."

**Josh:** Which is what the spec requires. A preference it can't honour degrades to L0, it doesn't fail.

**Dev:** Why can't it climb, though?

**Josh:** Two things in HAL-FORMS itself. First, a template's target can't be a URI template. It's a fixed address, and if you leave it out, it means "the address of this form document." So every bed would need its own form document, and nothing could be shared between beds.

**Dev:** And sharing is the whole point of the upper rungs.

**Josh:** Second, the properties are always inline. There's no way to say "the schema is over there." So there's no separate schema to cache.

**Dev:** You could extend HAL-FORMS.

**Josh:** We thought about it. But then we'd be hand-building HAL-FORMS bytes nobody else's tools produce, and we'd lose the one reason HAL-FORMS is here. So the rule is simple. If you need caching, switch dialects. Same acts, same state.

**Dev:** How hard is the switch?

**Josh:** Two request headers. `Accept: application/hal+json` and an `Accept-Profile` naming HAL Schema Forms. ARTIE reads the profile on each response, so it knows which dialect it got, and it never asks a HAL-FORMS response to go above L0.

## The external stylesheet

**Dev:** Okay, now the climb. First thing any web developer does is pull the CSS out into its own file. The browser fetches it once and keeps it.

**Josh:** That's L1, and from here on it's HAL Schema Forms only. The client adds `Prefer: forms=link`.

```json
{
  "id": 2, "status": "PLANTED", "cropName": "tomato", "moistureLevel": 40,
  "_links": {
    "self": { "href": "/beds/2" },
    "hr:forms": {
      "href": "/affordance-sets/a092fe364864d570",
      "integrity": "sha256-oJL+Nkhk1XBM9rClMS+N1O9Tn+2s2AOJDXaP+FNB3/c="
    }
  }
}
```

**Dev:** In words: the same state, but instead of the forms there's a link called "forms", pointing at an affordance set. And next to it, a hash starting "sha256".

**Josh:** Hold that hash. 485 bytes now instead of 1,089, and the server confirms with `Preference-Applied: forms=link`.

## Splitting the assets

**Dev:** Next step on the web: split the fonts, the scripts, the icons. Each its own file, shared across every page.

**Josh:** That's L2: `Prefer: forms=link, schema=link`. Now the affordance set doesn't carry the schema either.

```json
{
  "_forms": {
    "garden:water-cell": {
      "title": "Water the bed",
      "_links": { "target": { "href": "/beds/{id}/actions/water-cell", "templated": true } },
      "hr:templatePointers": { "id": "/id" },
      "method": "POST",
      "schema": {
        "$ref": "/schemas/7f232c04c52671dd",
        "hr:integrity": "sha256-fyMsBMUmcd0HdvI4xkfZO3dvViJqO0HdGynlKasuMdQ="
      }
    }
  }
}
```

**Dev:** In words: the water act, its title and method, and the schema is now just a reference with its own hash. And the target is "beds slash, id in braces," a template.

**Josh:** The exact thing HAL-FORMS can't do. The pointer says "take the id from the bed's own state." So this affordance set says nothing about bed 2 in particular.

**Dev:** A stylesheet that doesn't mention any one page.

**Josh:** Right. A cold start is three hops: the bed, the set, the schema. Once. After that, checking bed 2 is one conditional request. I ran it this morning: `304 Not Modified`, and nothing else.

## Fingerprinted filenames

**Dev:** Build tools rename files with a hash in the name, `app.3f9a1c.css`, and serve them as good for a year.

**Josh:** Same here. The affordance set comes with:

```http
Cache-Control: public, max-age=31536000, immutable
```

**Dev:** In words: anyone may cache it, for a year, and it will never change.

**Josh:** Its name ends in sixteen hex characters from the SHA-256 of its body, canonical JSON per RFC 8785. Same content, same name. So the L1 set and the L2 set for the same bed have different names, because their bytes differ. An earlier version of the spec gave them one name, and the ladder showed why that can't work: a cache could hand an L1 client the L2 bytes.

**Dev:** And the bed itself isn't fingerprinted.

**Josh:** It can't be. It changes when someone waters it. So the bed is `no-cache` with an ETag: always check. Things that never change get a year. The thing that changes gets checked every time. Same as the web: the HTML is checked, the hashed assets are cached forever.

## The shared cache

**Dev:** Now a CDN. A thousand pages, one stylesheet, fetched from the origin once.

**Josh:** That's L3. When I captured these, bed 2 and bed 4 were both planted tomato beds, moisture 40 and 0. Ask each for its forms at L2, and both point to the same affordance set, `aab67c2bd106ace1`.

**Dev:** Because of the template.

**Josh:** Yes. Bed 1, empty, has a different set, since an empty bed offers different acts. Bed 3, ready to harvest, has a third. The number of affordance sets is the number of states, not the number of beds. The spec's live test runs two clients through one shared cache, and the origin is contacted exactly once.

**Dev:** And in HAL-FORMS, a million beds is a million copies.

**Josh:** Each one inline, each one re-sent. Correct, but never shared.

## Integrity

**Dev:** Okay, the hash. Why does the link carry one?

**Josh:** Web version first. Subresource Integrity.

**Dev:** The `integrity` attribute on a script tag. The page carries the script's hash, and the browser refuses the script if it doesn't match.

**Josh:** The page vouches for what it links to. In 2024 the domain behind polyfill.io, a script loaded by a huge number of sites, changed hands, and Sansec reported in June that it was injecting malware. Reports put it at over 100,000 sites.

**Dev:** And those sites never changed a line.

**Josh:** The file under the same URL changed. And polyfill.io tailored its response to each browser, so sites couldn't have pinned one hash anyway.

**Dev:** And HAL Schema Forms avoids that how?

**Josh:** Layer 2 and 3 bodies carry no instance data, so they're the same bytes for everyone and can be pinned. And every link to them carries the hash: `integrity` on the forms link, `hr:integrity` on the schema reference, same syntax as the web's.

**Dev:** And ARTIE checks it.

**Josh:** Before parsing, on the bytes exactly as they arrive. A mismatch is refused and never cached. I checked by hand with openssl this morning: the L2 set hashed to exactly the value in bed 2's link, and the schema to the value in the set.

**Dev:** A chain. The bed vouches for the set, the set for the schema.

**Josh:** And the bed is revalidated every visit. So each layer vouches for the next.

**Dev:** What about HAL-FORMS?

**Josh:** At L0 there's nothing separate to vouch for. Everything is in the one response, and that response is revalidated with its ETag every time. Integrity becomes a question only once you start sharing.

**Dev:** And the `Repr-Digest` header I saw?

**Josh:** Sent, and checked when present, but optional on purpose. A response can't vouch for itself: whoever swaps the body can swap the header. The vouching comes from the link you already trust. Same reason the short sixteen-character names are safe: nothing trusts a name, only the full digest in the link. And a link with no integrity value is refused before any request, unless you've set ARTIE to talk to an older server.

## What it costs, and what's open

**Dev:** What does all this cost?

**Josh:** Two dialects to keep in step, for one. Our library renders both from the same declaration, so the acts can't drift apart. L1 and L2 clients don't share affordance-set cache entries, though they share schemas. And on CoAP, the ETag is at most eight bytes, so it's derived from a hash of the HTTP one. Integrity still rides in the links, so it works on any protocol.

**Dev:** And HAL-FORMS over CoAP?

**Josh:** Not offered. A CoAP client asking for HAL-FORMS gets "not acceptable."

**Dev:** And above L3?

**Josh:** L4 to L6 are other axes: the error model, the second dialect itself, and the optional reasoning step. Another conversation.

**Dev:** One sentence, then.

**Josh:** HAL-FORMS gets you onto the ramp with the tools you already have, and HAL Schema Forms is how you stop sending the same form a million times, with every representation vouching for what it links to.

---

## Where it is

- `spec/SPEC.md` §6.1 (the two dialects), §6.4 (`Prefer`), §6.5 (content addressing and integrity), §6.6 (caching) and §6.10 (the ladder).
- `spec/HAL-SCHEMA-FORMS-HR.md`, Rules 2 and 3 (integrity).
- `client/src/main/kotlin/heart/Dialect.kt`, `Rung.kt`, `Integrity.kt` and `CachingTransport.kt`.
- `hacklog.md`: why Spring HATEOAS was removed (commit `f4f342f`).

## Sources

- HAL-FORMS: <https://rwcbook.github.io/hal-forms/>
- Spring HATEOAS, HAL-FORMS support: <https://docs.spring.io/spring-hateoas/docs/current/reference/html/#mediatypes.hal-forms>
- Subresource Integrity, W3C: <https://www.w3.org/TR/SRI/>
- RFC 8785, JSON Canonicalization Scheme: <https://www.rfc-editor.org/rfc/rfc8785>
- RFC 9530, Digest Fields (`Repr-Digest`): <https://www.rfc-editor.org/rfc/rfc9530>
- polyfill.io, BleepingComputer, June 2024: <https://www.bleepingcomputer.com/news/security/polyfillio-javascript-supply-chain-attack-impacts-over-100k-sites/>
- polyfill.io, SiliconANGLE, June 2024: <https://siliconangle.com/2024/06/26/supply-chain-attack-compromises-100000-websites-via-polyfill-io-domain-takeover/>
