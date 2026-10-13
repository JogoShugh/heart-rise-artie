# One Page, Then Many: A Conversation About the Ladder

Josh and Dev talk about HEART's rendering ladder, L0 to L3, as if it were a website: from one page with everything inlined, to linked files, to shared files on a cache, and then about the integrity that makes sharing safe. About fifteen minutes read aloud.

The HEART samples are real responses from the garden server, captured on 13 October 2026 and trimmed with "…". The speakers say what each one shows.

---

## One big page

**Dev:** You know "Save page as, single file"? You get one HTML file with everything inside. The styles are inlined, the scripts are inlined, and even the images are pasted in as long strings of base64.

**Josh:** I used to email those around. One file, opens anywhere, nothing missing.

**Dev:** And nothing shared. Save ten pages from the same site and you've got ten copies of the same stylesheet.

**Josh:** That's L0 in HEART. Ask the garden for bed 2 with no special headers, and you get one response with everything in it.

```json
{
  "id": 2, "status": "PLANTED", "cropName": "tomato", "moistureLevel": 40,
  "_links": { "self": { "href": "/beds/2" }, … },
  "_forms": {
    "garden:water-cell": {
      "title": "Water the bed",
      "_links": { "target": { "href": "/beds/2/actions/water-cell" } },
      "method": "POST",
      "schema": { "properties": { "milliliters": { "type": "integer", "minimum": 1, "maximum": 1000, … } }, … }
    }
  }
}
```

**Dev:** In words: bed 2 is planted with tomatoes, moisture 40. And inside the same response, the one act it offers right now, "Water the bed", with where to send it and the full schema: millilitres, a whole number from 1 to 1000.

**Josh:** 1,089 bytes. The state, the form and the schema, all inlined, like the single-file page.

**Dev:** And it works. A client with no cache and no clever headers still gets the truth.

**Josh:** It does. The important part holds even at L0: the water act is there because watering is legal now. When the bed is ready, the water act disappears and harvest shows up. Absence still means something.

**Dev:** So what's wrong with it?

**Josh:** Nothing is wrong. It's just expensive. Every visit re-sends the form and the schema, even though they haven't changed. The spec puts it in one line: "L0 buys correctness, L2 buys economy."

**Dev:** And it's honest about the label, I remember. A server that only does L0 doesn't get to claim it conforms.

**Josh:** Right. The spec calls it "H.E.A.R.T.-shaped." It's the on-ramp, not the destination.

## The external stylesheet

**Dev:** So the first thing any web developer does is pull the CSS out into its own file. The page says "my styles are over there," and the browser fetches that once and keeps it.

**Josh:** That's L1. The client sends one extra header, `Prefer: forms=link`. Same bed, now the response is much smaller.

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

**Dev:** In words: the same state, but instead of the forms themselves there's a link called "forms", pointing at an affordance set. And next to the link, a long hash starting "sha256".

**Josh:** Hold that hash. We'll come back to it. The point for now: 485 bytes instead of 1,089. And the server says "I did what you asked" with a header, `Preference-Applied: forms=link`.

**Dev:** What if the server ignores the header?

**Josh:** Then you get L0, which still works. That's why it's a `Prefer` and not a demand. It degrades, it doesn't break.

## Splitting the assets

**Dev:** Next step on the web: you don't just split out one stylesheet. You split the fonts, the scripts, the icons. Each is its own file, and the same font file serves every page on the site.

**Josh:** That's L2. The client asks `Prefer: forms=link, schema=link`. Now the affordance set doesn't carry the schema either. It points to it.

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

**Dev:** In words: the affordance set has the water act, its title and method. The schema is now just a reference to another address, with its own hash. And the target has changed. It's not "beds slash 2" anymore. It's "beds slash, id in braces," a template.

**Josh:** That's the subtle bit, and it's what makes the next rung possible. The template says "fill in the id from the bed's own state," and the pointer says where: the `id` field. So this affordance set says nothing about bed 2 in particular.

**Dev:** So it's like a stylesheet that doesn't mention any one page.

**Josh:** Exactly. And now there are three hops on a cold start: the bed, the affordance set, the schema. But only once.

**Dev:** And after that?

**Josh:** After that, checking bed 2 is one conditional request. The client sends the ETag it has, the server says `304 Not Modified`, and that's the whole conversation. I ran that this morning: 304, nothing else.

## Fingerprinted filenames

**Dev:** Here's a web trick that I think matters. Build tools rename your files with a hash in the name. `app.3f9a1c.css`. And the server says that file is good for a year.

**Josh:** Because if the contents change, the name changes. So the old name can be cached forever.

**Dev:** Cache busting by naming.

**Josh:** HEART does exactly that for the affordance sets and schemas. Here's the header the server sends on the affordance set:

```http
Cache-Control: public, max-age=31536000, immutable
```

**Dev:** In words: anyone may cache it, for a year, and it will never change.

**Josh:** And the name ends in sixteen hex characters taken from the SHA-256 of the body. The body is canonical JSON, keys sorted, no extra spaces, per RFC 8785. So the same content always gives the same name.

**Dev:** Which means the L1 set and the L2 set for the same bed have different names.

**Josh:** Yes, because their bytes differ: one has the schema inside, the other has a pointer. An earlier version of the spec gave them one name, and the ladder showed why that can't work: a cache could hand an L1 client the L2 bytes. Different bytes, different address. No exceptions.

**Dev:** And the bed itself? That's not fingerprinted.

**Josh:** No, and it can't be. The bed's state changes whenever someone waters it. So the bed is `Cache-Control: no-cache` with an ETag. Always check. The things that never change get a year. The thing that changes gets checked every time.

**Dev:** Same as the web. The HTML page is checked, the hashed assets are cached forever.

## The shared cache

**Dev:** Now picture a CDN. A thousand pages on a site, all using the same stylesheet. The CDN fetches it from the origin once and serves everyone.

**Josh:** That's L3. And here's the moment I like best. When I captured these, bed 2 and bed 4 were both planted tomato beds. Different beds, different moisture: 40 and 0. Ask each for its forms at L2…

**Dev:** And they point to the same affordance set.

**Josh:** Same address, `/affordance-sets/aab67c2bd106ace1`. Because the template made the set independent of which bed it's on. Two resources in the same state share one set, and a shared cache fetches it from the origin once.

**Dev:** And bed 1, which was empty?

**Josh:** A different set, because an empty bed offers different acts. Sowing, not watering. Bed 3, ready to harvest, had a third one.

**Dev:** So the number of affordance sets is the number of states, not the number of beds.

**Josh:** That's it. A million beds in five states is five sets. The spec has a live test for this: two clients, one shared cache, and the origin contacted exactly once.

## Integrity

**Dev:** Okay, the hash we held on to. Why does the link carry one?

**Josh:** Let's do the web version first. Do you know Subresource Integrity?

**Dev:** The `integrity` attribute on a script tag. You write the script's hash into your page, and the browser refuses to run the script if the file it downloads doesn't match.

**Josh:** Right. The page vouches for the files it links to. And here's why it matters. In 2024, the domain behind polyfill.io, a script loaded by a huge number of sites, changed hands. Security firm Sansec reported in June that it had started injecting malware. Reports put it at over 100,000 sites.

**Dev:** And those sites never changed a line. The file under the same URL changed.

**Josh:** That's the danger of trusting a URL. And there's a twist. polyfill.io tailored its response to each browser, so sites couldn't pin one hash even if they'd wanted to.

**Dev:** Which HEART avoids how?

**Josh:** Two ways. First, layer 2 and layer 3 bodies carry no instance data. The same bytes for everyone, so they can be pinned. Second, every link to them carries the hash. That `integrity` value on the forms link, and the `hr:integrity` on the schema reference. Same syntax as the web's: "sha256-" and the base64 digest.

**Dev:** And ARTIE checks it.

**Josh:** Before parsing. It hashes the bytes exactly as they arrive. If they don't match, it refuses them and doesn't cache them. I checked by hand this morning: I hashed the L2 affordance set with openssl, and it came out "sha256-qrZ8K9…", exactly the value in bed 2's link. Same for the schema.

**Dev:** So it's a chain. The bed vouches for the affordance set, and the affordance set vouches for the schema.

**Josh:** And the bed itself is checked every visit, with its ETag. So each layer vouches for the next, starting from the one you always revalidate.

**Dev:** What about the header that carries a digest? I saw `Repr-Digest` on the response.

**Josh:** The server sends it, and ARTIE checks it when it's there. But it's optional, on purpose. A header in the response can show the bytes arrived intact, but a response can't vouch for itself. If someone swapped the body, they could swap the header too. The vouching has to come from the link you already trust.

**Dev:** And those sixteen-character names? A hash cut to 64 bits, can't someone find a clash?

**Josh:** The spec's answer is that nothing trusts the name. It's only a name. The check is always against the full digest in the link. Serve the wrong body under a name, and it's refused, not used. And the server checks at start-up that no two of its own bodies share a name, and refuses to start if they do.

**Dev:** And a link with no integrity value at all?

**Josh:** Refused before any request is sent. Unless you've deliberately set ARTIE to talk to an older server that doesn't send them.

## What it costs, and what's open

**Dev:** What does all this cost?

**Josh:** A few things. L1 and L2 clients don't share affordance-set cache entries, because the bytes differ. They do share schemas. A deployment usually settles on one rung, so it's a small loss.

**Dev:** And HAL-FORMS?

**Josh:** Stops at L0. Its forms can't point to a shared schema, and its target can't be a template. So it's the on-ramp dialect. If you want caching, you move to HAL Schema Forms, with the same acts.

**Dev:** And over CoAP?

**Josh:** The ETag can be at most eight bytes there, so the CoAP validator is the first eight bytes of a hash of the HTTP one. Still opaque, still strong. The integrity values ride in the links, so they work the same on any protocol.

**Dev:** And above L3?

**Josh:** L4 to L6 aren't more caching. They're other axes: the error model, a second dialect, and the optional reasoning step. Another conversation.

**Dev:** One sentence, then. What's the ladder?

**Josh:** It's how the web learned to stop sending the same stylesheet a million times, applied to forms and schemas, with the page vouching for everything it links to.

---

## Where it is

- `spec/SPEC.md` §6.4 (`Prefer`), §6.5 (content addressing and integrity), §6.6 (caching) and §6.10 (the ladder).
- `spec/HAL-SCHEMA-FORMS-HR.md`, Rules 2 and 3 (integrity).
- `client/src/main/kotlin/heart/Rung.kt`, `Integrity.kt` and `CachingTransport.kt`.

## Sources

- Subresource Integrity, W3C: <https://www.w3.org/TR/SRI/>
- RFC 8785, JSON Canonicalization Scheme: <https://www.rfc-editor.org/rfc/rfc8785>
- RFC 9530, Digest Fields (`Repr-Digest`): <https://www.rfc-editor.org/rfc/rfc9530>
- polyfill.io, BleepingComputer, June 2024: <https://www.bleepingcomputer.com/news/security/polyfillio-javascript-supply-chain-attack-impacts-over-100k-sites/>
- polyfill.io, SiliconANGLE, June 2024: <https://siliconangle.com/2024/06/26/supply-chain-attack-compromises-100000-websites-via-polyfill-io-domain-takeover/>
