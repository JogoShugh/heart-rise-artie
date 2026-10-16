# Dev Meets ARTIE

*A fictional conversation, after "Take HEART: Dynamically Guardrailing OpenAPI Services with HATEOAS"*

**Cast**

- **Me**: has been building HEART, the server side, and ARTIE, the generic client.
- **Dev**: has written HTML for twenty years. Six weeks into a move to backend work, and not enjoying it.

---

## I. The page that never broke

*A Thursday afternoon. Dev drops into the chair across from me with a laptop and the look of someone who has just read their fourth release-coordination thread of the day.*

**Dev:** Can I ask you something stupid?

**Me:** Those are usually the good ones.

**Dev:** How long have I been doing web work? Since tables for layout, since spacer GIFs. I've built thousands of pages, and in all that time I never once had to tell anyone's browser that I'd changed something.

**Me:** Go on.

**Dev:** Say I move the contact page from `/contact.html` to `/about/contact`. I change the link in the nav. Done. Nobody calls Mozilla or Google. Nobody's browser needs an update. Next time anyone loads the home page, the link points at the new place and they click it. They never knew the old address and they don't need to know the new one. The page *told* them.

**Me:** It did.

**Dev:** Forms are the same. Marketing wants a phone number on the sign-up form, so I add an `<input name="phone" required>`. That's the whole release. Every browser on earth renders the new field, refuses to submit without it, and posts it wherever the `action` attribute says. If the rule is "only digits, max fifteen," I write `pattern` and `maxlength` and the browser enforces it. I never shipped a single line of code to the client.

**Me:** Because the browser is a generic client. It knows nothing about your site, about contact pages or sign-ups. It knows how to render whatever arrives and follow the controls the page offers.

**Dev:** Right! And the best bit: if a user isn't allowed to do something, I simply *don't put the button there*. Logged out? No "Edit profile" link. Order shipped? No "Cancel order" button, just a line saying "This order has shipped and can no longer be cancelled." The page shows what you can do **now**.

**Me:** Information and controls, presented together.

**Dev:** That's just... how the Web works. I never thought about it. Then I moved to the backend team.

## II. The bewilderment

**Me:** What happened?

**Dev:** My first ticket. The orders service has an endpoint, `POST /orders/{id}/cancel`. Product wants a `reason` field on cancellations, required. In my world that's one `<input>` on the form. Twenty minutes, tops.

**Me:** And?

**Dev:** It's been three weeks. *Three weeks.* First I learned that the mobile app has the URL `/orders/{id}/cancel` **compiled into it**. And the iOS app has a different copy. And there's a "typed SDK" generated from our OpenAPI file, with a `CancelOrderRequest` class that has exactly the fields it had on the day someone ran the generator. If I add a required field, every client that doesn't send it gets a 422, and the iOS app was submitted to the App Store two weeks ago and there's no telling when people will update it.

**Me:** So you can't just add the field.

**Dev:** Apparently not! So someone says "version it." Make `/v2/orders/{id}/cancel`. Keep v1 alive. But v1 cancellations won't have a reason, so analytics needs to handle nulls, so their schema changes, so their client library needs regenerating, and *they* have a release train that leaves every other Tuesday...

**Me:** Oh no.

**Dev:** Then there's a spreadsheet. A literal spreadsheet called "Release Matrix Q4," with columns for orders-service, the SDK, web, iOS, Android, the analytics consumer and the partner integration, and rows that say which versions are compatible with which. There's a meeting about the spreadsheet. The meeting has its own Slack channel.

**Me:** *(laughing, but not unkindly)* The lockstep deploy.

**Dev:** Is that what it's called? Because it's how it feels. Everything chained together, marching in step. Nobody can move unless everybody moves. A *field*. I wanted to add *one field*.

**Me:** It gets worse, doesn't it?

**Dev:** It gets so much worse. The rules! On the Web, if you can't cancel a shipped order, the page leaves the button out. Here the mobile client has an `if (order.status == "PENDING" || order.status == "CONFIRMED")` to decide whether to show Cancel. The server has *its own* copy of that rule, because it has to refuse. Last month the server added a `PAYMENT_HOLD` status where cancellation is allowed too. The app doesn't know about it, so it hides the button from people who are allowed to press it. Yesterday someone proposed a "feature-flag service" so the server could tell clients which buttons to show.

**Me:** A feature-flag service. To tell the client which buttons to show.

**Dev:** **Yes!** And I sat in that meeting thinking: *we invented that in 1995.* It's called *sending the form*. Why are we compiling the URL, the fields, the validation and the business rules into a binary, shipping it to a hundred thousand phones, and then building a whole separate system to tell those binaries what we already knew when we sent the response?

*Dev slumps.*

**Dev:** I honestly thought I was missing something. Everyone treats it as normal. "Oh, that's a breaking change, we'll need to coordinate." *Coordinate.* Like moving a link is a diplomatic incident.

## III. You're not missing anything

**Me:** Dev, I need you to hear this: you're not missing anything. You're the one in that meeting who *isn't* confused. You're seeing it with fresh eyes.

**Dev:** That's very kind, but...

**Me:** No, I mean it literally. In 2000 Roy Fielding wrote the dissertation that named REST. One of its constraints is *hypermedia as the engine of application state*, HATEOAS. It means exactly what you've described: the client moves from state to state by choosing among the controls each response carries. Links and forms.

**Dev:** So REST APIs *are* supposed to send the forms?

**Me:** They were. Most "REST APIs" quietly dropped that part. By 2008 it annoyed Fielding enough that he wrote a post called *REST APIs must be hypertext-driven.* He defined hypertext as, and I'm quoting, "the simultaneous presentation of information and controls such that the information becomes the affordance through which the user **(or automaton)** obtains choices and selects actions."

**Dev:** "Or automaton."

**Me:** He wasn't only talking about people. In the comments he even says a client needs only "the meaning of those instructions and some idea of what it wants to do next, whether that purpose be user-driven, configuration-driven, or some sort of **AI-driven**." That's 2008. Eighteen years before everyone started pointing AI agents at APIs.

**Dev:** So how did we end up with the Release Matrix spreadsheet?

**Me:** Your browser story has four frames. Picture a page about one garden bed.

1. **Information.** Some values, read-only. You can look, that's all.
2. **A link.** Now you can go somewhere, and the page gave you the address. You didn't build it.
3. **A form.** Now you can do something. It says where it goes, what it needs, and the limits.
4. **Forms that follow the state.** The bed froze overnight. Water is gone, Thaw is offered, and the page says why.

**Dev:** That's every good page I ever built. Frame four is the shipped-order thing.

**Me:** A typical JSON API sends frame one and stops. Here's bed 2:

```json
{
  "id": 2,
  "label": "bed-2",
  "status": "PLANTED",
  "cropName": "tomato",
  "growthPoints": 80,
  "moistureLevel": 40
}
```

**Dev:** ...that's it? No links? No forms?

**Me:** None. So the client has to *already know* that watering means `POST /beds/2/actions/water-cell`, that the body needs `milliliters`, that it has to be between 1 and 1000, and that a frozen bed can't be watered. Where does it learn all that?

**Dev:** *(slowly)* From the docs. Written for people. And then a developer types it into the client. Compiles it.

**Me:** And when the client gets a rule wrong?

**Dev:** It finds out by failing. 409 Conflict, "the bed is frozen."

**Me:** That's the whole disease. Every address, field, limit and rule is copied from the server into every client, *ahead of time*, and frozen into a build. Two copies of the truth. When the server's copy changes, the frozen ones are wrong. Your lockstep deploys, version matrices and synchronized upgrades are all ways of keeping those copies in agreement by force.

**Dev:** And the feature-flag service is...

**Me:** ...a third copy, to patch the second one. Yes.

*Dev stares at the ceiling for a long moment.*

**Dev:** Okay. So how do we stop? Because "go back to HTML" isn't the answer. A phone app isn't a browser. A batch job isn't a browser.

**Me:** No. But each of them could be *a* browser. A browser for JSON.

## IV. Putting the forms back: HEART

**Me:** That's what HEART is. *Hypermedia Enforced Agentic Reliable Transactions.* It's not a new idea. It's your idea, the Web's idea, written down carefully enough for programs. Let's rebuild bed 2 the way you'd rebuild a page, one rung at a time.

**Me:** Rung one: **it names itself and its vocabulary.**

```json
"_links": {
  "self": { "href": "/beds/2" },
  "curies": [
    { "name": "garden", "href": "/profiles/garden#{rel}", "templated": true },
    { "name": "hr", "href": "https://w3id.org/heart-rise/rels/{rel}", "templated": true }
  ]
}
```

**Dev:** A self link is the page's own URL. And the curies?

**Me:** Shorthand for vocabularies. `garden:` words are defined in the garden's profile, `hr:` words in HEART's. It's like a page that says what it's *about*.

**Me:** Rung two: **forms. What you can do now, and where.**

```json
"_forms": {
  "garden:water-cell": {
    "title": "Water the bed",
    "description": "Pours water into every cell of a planted bed. Enough water makes it ready to harvest.",
    "_links": { "target": { "href": "/beds/2/actions/water-cell" } },
    "method": "POST",
    "contentType": "application/json"
  }
}
```

**Dev:** *(sitting up)* That's a `<form>`! `action` is the target, plus `method`, and the title is the button label.

**Me:** Exactly. Keyed by its *meaning*, `garden:water-cell`, not by its URL. The title and description are there for people *and* for language models.

**Me:** Rung three: **what the act needs, embedded.** A JSON Schema, inline:

```json
"schema": {
  "type": "object",
  "title": "Water Cell",
  "required": ["milliliters"],
  "properties": {
    "milliliters": {
      "type": "integer",
      "title": "Water per cell",
      "description": "Millilitres of water poured into each cell",
      "minimum": 1,
      "maximum": 1000,
      "examples": [250]
    }
  }
}
```

**Dev:** `required`, `min`, `max`, a placeholder. Those are my `<input>` attributes!

**Me:** They're your `<input>` attributes. Now, your `reason` field on cancellation: where would it go?

**Dev:** In the schema. Add it to `properties` and to `required`. And any client that *renders the schema* would just... show a new box.

**Me:** Same day. No regeneration, no v2.

**Dev:** Oh my god.

**Me:** Rung four: **why the other acts are absent.**

```json
"hr:unavailable": {
  "href": "/unavailable/3257a9132e2fdd0b",
  "type": "application/json",
  "integrity": "sha256-MlepEy4v3Qt2z8jIohUJxoffZ2SOwNKCgJP7hMT/XV8="
}
```

which resolves to:

```json
{
  "unavailable": [
    { "title": "Sow a seed", "reason": "The bed is already planted." },
    { "title": "Harvest the crop", "reason": "The crop is not ready yet; it needs more water to grow." },
    { "title": "Thaw the bed", "reason": "The bed is not frozen." }
  ]
}
```

**Dev:** "This order has shipped and can no longer be cancelled." The red line under the missing button.

**Me:** In the server's own words. Now the frost hits overnight. Same bed, same URL, nothing changed on the client:

```json
"status": "FROZEN",
"_forms": {
  "garden:thaw": {
    "title": "Thaw the bed",
    "description": "Covers a frozen bed with fleece. The bed becomes planted again.",
    "_links": { "target": { "href": "/beds/2/actions/thaw" } },
    "method": "POST"
  }
}
```

and the unavailable list now says *"The bed is frozen; watering now would split the roots. Thaw it first."*

**Dev:** So the client doesn't have an `if (status == FROZEN)` anywhere.

**Me:** Nowhere. The rule exists **once**, on the server, which is the only place that can actually enforce it. The client's job is to show whatever forms arrived. Your `PAYMENT_HOLD` bug can't happen: the server adds the status, includes Cancel in the forms for it, and every client shows the button.

**Dev:** What about two people pressing at once? On the Web I'd sometimes get double submits.

**Me:** Rung five, the headers:

```http
HTTP/1.1 200 OK
Content-Type: application/hal+json
ETag: "seq-3.hsf.L0.da0109b4d9380b72"
Cache-Control: no-cache
Link: <https://w3id.org/heart-rise>; rel="profile"
```

An act has to send that ETag back as `If-Match`. If somebody else's write landed first, you get a **412**, not a silent overwrite. Leave `If-Match` off and you get a **428**: precondition required. Fill in the form wrong, **422**. Press a form that isn't offered any more, **409**. Each one tells you what happened instead of quietly doing something you didn't mean.

## V. ARTIE: one client for everything

**Dev:** Okay, the server side makes sense. But somebody still has to *write* the client that reads all this.

**Me:** Once. That's ARTIE: *Autonomous Restful Traversal and Interactive Engine.* A generic API client, the same way the browser is a generic web client. It doesn't know about beds, frost, orders or cancellations. It knows four things:

1. Start from a bookmark. `GET /`, the only address it's ever told.
2. Show the state it receives.
3. Show the forms it receives, and build each form's inputs from its schema.
4. When one is chosen, fill the schema, send it to the form's target with the form's method, and include `If-Match` with the ETag it saw.

Then repeat with whatever comes back.

**Dev:** That's... a browser's main loop.

**Me:** It is a browser's main loop. *Choose among what's offered. Fill the schema. Never guess.* The spec sets ARTIE a test: run against two unrelated domains, a garden and a sports scoreboard, from a bookmark, with **no** domain-specific configuration. Their only shared trait is that both speak HEART.

**Dev:** Same binary, two completely different apps.

**Me:** Same binary. Now the bit you'll enjoy. We ran an experiment. Monday, every client works. Then on "Wednesday" we change the API, one release at a time, and see which clients survive *without being touched since Monday*. Each one tries to water bed 2.

| Change | Generated from the OpenAPI description | ARTIE (native HEART) |
|---|---|---|
| Move the URL | **404** | watered |
| Rename a field | watered | watered |
| Rename the act | watered | watered |
| Retitle it | watered | watered |
| Tighter limit (max now below 800 ml) | **422 at 800 ml** | watered |
| New required input | **422** | watered |

**Dev:** *(pointing)* "Move the URL." That's my contact page! The generated client had the URL baked in, so it got a 404. ARTIE followed the form's target, the way a browser follows a link.

**Me:** "New required input" is your `reason` field. The generated client sends the old body and gets a 422. ARTIE reads the new schema, sees the new required field, and fills it in. "Tighter limit" is the same story: ARTIE reads the new `maximum` rather than remembering the old one.

**Dev:** So none of the release matrix is needed. You don't coordinate the client's upgrade, because *there is no client upgrade.*

**Me:** That's the point. The server is free to evolve. The client's only promise is to read what it's sent. No lockstep, no synchronized upgrades, nothing statically compiled that can go stale.

**Dev:** I need to be suspicious for a second. Nothing is free.

**Me:** Good, stay suspicious. We ran one more column: a **scripted** client, ARTIE driven by code that names the act `garden:water-cell` and its field by hand. That's hard-coding at a higher level.

| Change | Scripted, on top of ARTIE |
|---|---|
| Move the URL | watered |
| Rename a field | watered |
| **Rename the act** | **not found** |
| Retitle it | watered |
| Tighter limit | watered |
| **New required input** | **422** |

**Dev:** So if my code *says* "water-cell," and the server renames it "irrigate," I'm broken again.

**Me:** Yes. Hypermedia doesn't make coupling disappear. It moves it to the one place it belongs: **meaning.** You're no longer coupled to URLs, HTTP methods, field layouts, limits or state rules. You're coupled to the vocabulary, "water-cell means pour water into the bed," and that vocabulary is published in a profile, versioned on its own, and changes far less often than paths do. Browsers live the same way. Your form fields' `name` attributes are a contract too.

**Dev:** And if I don't hard-code names at all? If a person or a model is choosing from the titles and descriptions?

**Me:** Then you get the ARTIE column: it survived all six. That's where agents come in. An AI agent is the perfect HEART client. Give it the forms allowed *now*, with their titles, descriptions and schemas, and it chooses among them. It can't call Water on a frozen bed because Water isn't on the menu, and it can read *why*. Fielding called them "guidance for automated agents." Today we'd say **guardrails**.

**Dev:** Instead of handing the model a forty-page OpenAPI file and hoping it remembers which rules apply.

**Me:** And hoping it doesn't learn them by failing, in production.

## VI. "But my backend is already OpenAPI"

**Dev:** Here's my real problem, though. The orders service isn't HEART. It's a pile of OpenAPI endpoints, and nobody's rewriting it this quarter.

**Me:** Think about what an OpenAPI description actually is. Imagine a shop putting every inventory action on one page: eight blank forms, each asking for a plant ID, each with a red sentence saying when it would work.

**Dev:** *(wincing)* I've seen that page. That's the admin panel nobody's allowed to touch.

**Me:** It's accurate! Every form works if you fill it in correctly, for a plant in the right state. But it asks for what it already knows, makes you apply the rules yourself, and teaches by failure. That's an OpenAPI description. Every operation is a form card, every `{plantId}` is a blank ID field, and every "Only allowed when the plant is in quarantine, after 14 days" is a red sentence. It lists everything the service can ever do, in every state, for everyone. What it *can't* say is what *this* plant allows *now*.

**Dev:** So it's the documentation, not the page.

**Me:** Right. So we put a **gateway** in front. OpenAPI in, HEART out. It reads the description at start and builds resource types. For each answer it needs one more thing: a signal of what's allowed now. There are three ways to get one:

1. **Add one `Link` header** pointing at a HEART affordance set. Best option: shared, cached forms, and reasons for what's missing.
2. **Change nothing; say what you already say.** Lots of real APIs already send this, each in their own way: Google Drive's `capabilities` (`canTrash`, `canDelete`), Box's permissions, PayPal's links, OData's advertised actions. The gateway reads them.
3. **Nothing at all; write the rules down.** A table in the gateway: "when status is LOCKED, only unlock, because…" It's still early binding, but it lives in *one* place instead of in every client. Last resort.

**Dev:** Number three is the `if` statement again.

**Me:** It is, but it's *one* `if` statement, next to the service, instead of seven copies in seven clients on seven release trains. And when the service learns to send a real signal, you delete the table and no client notices.

**Dev:** How's it configured?

**Me:** A connector, a folder per API. Most need one small file:

```yaml
# connectors/drive/file.reader.yaml
allowed:
  booleans: /capabilities
  map: { canTrash: trashFile, canDelete: deleteFile }
hide: [/capabilities]
```

It fails loudly. A connector that names an operation the description doesn't have stops the gateway at startup and names the file. An answer nothing recognizes gets refused. **The gateway never guesses.**

**Dev:** And when ARTIE presses a button through the gateway?

**Me:** The gateway checks what a HEART server would check: is the form offered now, is the body valid, is the ETag current? If not, it refuses with 409, 422, 412 or 428 before anything is sent upstream. If so, it re-reads the resource to confirm nothing's changed, calls the real OpenAPI operation, and turns the plain JSON answer back into HEART with the new forms.

**Dev:** How do you know it actually works?

**Me:** We gave it the hardest test we could think of. We took a real HEART server, the garden, and flattened it into a plain OpenAPI twin with no controls at all. Then we asked the gateway to rebuild HEART from that alone.

**Dev:** You translated a novel into French and back to check the translator.

**Me:** *(grinning)* That's exactly how the write-up puts it. And it's exactly the reason: the native garden is the answer key. Anything the gateway dropped would show up as a different result. Through the gateway, every result matched native HEART, change by change. The frost test was 7 out of 7 buttons right. HEART's conformance kit passed 23 of 23 through the gateway, and the gateway's own tests passed 26 scenarios against a little door service that's never heard of HEART.

**Dev:** And the honest limits?

**Me:** They're real. If the upstream service doesn't support `If-Match`, the gateway re-reads before acting, but a write landing in that gap can't be caught. Reasons for missing acts only come from the `Link` signal or from tables. Right now the gateway calls the service as itself, so per-caller credentials are planned, not built. And it's our own services and one run of a small suite: evidence of behaviour, not a benchmark.

## VII. The ticket, again

*Dev pulls the laptop closer and opens the cancellation ticket.*

**Dev:** Let me replay my three weeks with this.

**Dev:** One: the orders service adds `reason` to the cancel schema. Required, max 500 characters.

**Me:** Through the gateway, it shows up in the form's schema on the next response.

**Dev:** Two: ARTIE, or any HEART client, renders the new box, because it renders whatever schema it's given. No regeneration, no `CancelOrderRequest` class, no v2.

**Me:** Correct.

**Dev:** Three: the old iOS app, the one stuck in review...

**Me:** ...would be fine if it were a HEART client. That's the migration: you stop shipping clients that *know* the API and ship clients that *read* it. After that, an app store review is just a UI change.

**Dev:** Four: `PAYMENT_HOLD`. The server offers Cancel in that state, so the button appears. When it doesn't offer it, the client shows the reason. No feature-flag service.

**Me:** You can cancel that meeting.

**Dev:** Five: the Release Matrix spreadsheet...

**Me:** ...gets much shorter. The rows that remain are about *meaning*, like "we renamed `cancel` to `void`." That's a vocabulary change, and you handle it the way the Web always has: keep the old relation alongside the new one for a while, publish the profile, and let hard-coded scripts migrate when they're ready. Everything else, the paths, fields, limits and rules, just isn't a coordinated release any more.

*Dev closes the laptop.*

**Dev:** You know what's funny? I spent six weeks thinking backend people knew something I didn't. That all this ceremony was the price of doing "real" engineering.

**Me:** And?

**Dev:** And the browser solved it in 1995. We just stopped sending the forms.

**Me:** So let's send them again.

**Dev:** One last question. What about things that aren't ours, like GitHub?

**Me:** That's Part 2. GitHub publishes its full OpenAPI description, and a pull request is nothing *but* state: open or closed, draft or ready, mergeable or blocked. Put the gateway in front, and you can ask an agent "why can't I merge #482?" and get GitHub's actual reason: behind main, a required check missing, still a draft. And it's offered Merge only when GitHub would accept it.

**Dev:** A page about a pull request, with only the buttons that work right now.

**Me:** With a red line under the ones that don't, saying why.

**Dev:** *(standing up)* I'm going to go be annoying in the release meeting.

**Me:** Take HEART.

---

*Sources: "Take HEART: Dynamically Guardrailing OpenAPI Services with HATEOAS," Part 1 (16 October 2026); `SPEC.md` in this repository; Roy T. Fielding, "REST APIs must be hypertext-driven" (2008). Dev is fictional. The JSON excerpts are trimmed from the article's real garden responses, and the results tables are from its 16 October run.*
