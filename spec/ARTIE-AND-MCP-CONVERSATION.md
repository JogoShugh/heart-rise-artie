# ARTIE, Bare or Wrapped: A Conversation

Josh and Dev talk about two ways to use ARTIE: directly, as a library in your own code, or wrapped in heart-mcp so any MCP host can drive it. What each does better, what the wrapper costs, and how the two work together. About fifteen minutes read aloud.

Code samples are small, and the speakers say what each one shows. Anything that is an idea rather than something built, they say so.

---

## Why keep the bare one?

**Dev:** So I watched Claude Code water bed 2 through heart-mcp. It looked at the garden, asked it for bed 2, watered it with 250 millilitres, then tried 700 and got told no, in the bed's own words. No code from me at all.

**Josh:** That's the demo I like best.

**Dev:** Then here's my honest question. If a model in a chat window can do that, why does anyone write code against ARTIE directly anymore?

**Josh:** Fair. Let's start with the stack, because I think the answer is in it. At the bottom is a HEART service, the garden. Above that is ARTIE, our client library. It does the HTTP, the caching, the schema checks, the If-Match.

**Dev:** And heart-mcp sits on top of ARTIE.

**Josh:** Right. heart-mcp is a small program that speaks MCP over standard input and output. Above it is the host, Claude Code say, and the host runs the model.

**Dev:** So heart-mcp isn't a second client. It's ARTIE with an MCP face on it.

**Josh:** That's the key point. Every request still goes through ARTIE. So the guarantees are the same either way: the schema is checked before sending, the act carries If-Match, and a 412 means read again, never resend.

**Dev:** Then the question isn't "which client is safer." It's "who's choosing, and where does the code run."

**Josh:** I think that's exactly it.

## What bare ARTIE does better

**Dev:** Let me try the first strength. Bare ARTIE runs inside my process. No child process, no JSON going over a pipe, no model in the loop unless I put one there.

**Josh:** And that last part matters most to me. You can drive ARTIE with no model at all. For a flow you already know, you just call it.

```kotlin
client.enter(gardenBookmark)
client.act("garden:get-bed", buildJsonObject { put("id", 2) })
client.act("garden:water-cell", buildJsonObject { put("milliliters", 250) })
```

**Dev:** In words: enter the garden from its bookmark, take the act "get-bed" with id 2, then take the act "water-cell" with 250 millilitres. Three calls, no model.

**Josh:** And still no addresses in your code. You name an act by its name, or a link by its relation and title. ARTIE finds the address in the representation.

**Dev:** So it's still late binding. Just without a model doing the choosing.

**Josh:** Right. Second strength: when you do want a model, you choose how. ARTIE has a Reasoner interface. One function: you get the intent, the state, the offered acts and the links, and you return one selection.

**Dev:** And there's a Claude one built in.

**Josh:** ClaudeReasoner, yes. But you could write a reasoner from plain rules, or a different model, or a test double. ARTIE doesn't care.

**Dev:** What can a selection be?

**Josh:** Act, follow, describe, explain, or clarify. Clarify is the interesting one. The reasoner can say "I need to ask the user something" instead of guessing.

**Dev:** And ARTIE turns that into what?

**Josh:** A decision called Asked, with the question. Nothing is sent. That's one of seven kinds of decision: selected, acted, followed, explained, asked, rejected, refused.

**Dev:** Which my code can switch on. That's the third strength, I think. Bare ARTIE gives me typed results, so I can test them, branch on them, and run them in CI.

**Josh:** Yes. And there's a decide call that runs Sync and Reason and the checks, and stops before sending anything. A dry run.

**Dev:** Useful for "what would the model do here?" without touching the bed.

**Josh:** Fourth: cost and speed. A known flow in bare ARTIE costs no tokens. A model through MCP costs tokens on every step, because each result goes back into its context.

**Dev:** And fifth, scale. One process can hold as many ARTIE clients as I want. One per user, one per job.

**Josh:** Whereas heart-mcp today is one process per host session, one user, with its state in memory.

## What the wrapper gives you

**Dev:** Okay, the other side. The biggest one is obvious: no code. Any MCP host can drive any HEART service. Claude Code, Claude Desktop, someone's own agent.

**Josh:** In any language. The host doesn't need to be on the JVM. It just needs to speak MCP.

**Dev:** Second, a person can watch. In a chat, I see every look and every act, and I can say "no, not that bed."

**Josh:** That's a real difference. Bare ARTIE with a reasoner makes one choice per intent. The host model in MCP holds the whole conversation, and the person is right there.

**Dev:** Third, exploring. If I've never seen a service, I can just ask the model to look around. It reads the titles and descriptions and tells me what it can do.

**Josh:** With heart_describe it gets the service's longer description of an act, and with heart_explain it gets the reason an act isn't offered right now. Both come from the service, in its own words.

**Dev:** Fourth is the safety boundary. The six tools never take a URL.

**Josh:** Here's what the model calls when it acts.

```json
{ "act": "garden:water-cell", "fields": { "milliliters": 250 } }
```

**Dev:** In words: the act's name and one field, millilitres, 250. No address, no method, no header.

**Josh:** So the model can't make one up. And if it names an act that isn't offered, heart-mcp says "rejected before sending" and nothing goes out.

**Dev:** Same with fields that break the schema. Two thousand millilitres is over the maximum of a thousand, so it's stopped before the wire.

**Josh:** And fifth, packaging. It ships as a Docker image. One command adds it to Claude Code.

## What the wrapper costs

**Dev:** Now the honest part. What's weak about heart-mcp today?

**Josh:** It's stdio only. No HTTP transport, no auth. So it's one local user per process.

**Dev:** And the state lives in memory. Close the host, and the session is gone.

**Josh:** Right. Tokens and latency on every step, like we said. And the automated tests, twelve scenarios, cover only the garden. Basketball and tickets worked, but only in Claude Code runs we watched, not in a test suite.

**Dev:** And one subtle thing. Bare ArtieAgent syncs at the start of each intent. heart-mcp acts on the copy it already holds.

**Josh:** On purpose. The model acts on what it actually saw. If the bed changed in between, the server says 412 and ARTIE reads it fresh. The model then sees the new state and decides again.

**Dev:** So the trade is the model never acts on something it didn't see, at the price of an occasional stale.

**Josh:** Yes. I'd take that trade.

## How they work together

**Dev:** So far it sounds like a choice. Bare for code, wrapped for chat. But you said they pair.

**Josh:** They share more than people expect. The shape the model sees in heart-mcp is ARTIE's own. An offered act there is ARTIE's Offer: name, title, description, schema. A link is ARTIE's LinkOffer: relation and title. Neither has an address, by design.

**Dev:** So a reasoner and an MCP host see the world the same way.

**Josh:** Exactly. Which gives you the first pairing. Explore with MCP, then write the flow down in bare ARTIE.

**Dev:** Walk me through that.

**Josh:** You point Claude Code at a service you've never seen. Ask it to find a game and score a basket, say. It looks, follows, describes, acts. Now you know the path: which link titles, which act names, which fields.

**Dev:** And I turn that path into three or four ARTIE calls in my code, with no model.

**Josh:** Right. The model did the discovery once. Your code runs the known path a thousand times for free.

**Dev:** Second pairing. The hot path in bare ARTIE, the long tail through MCP.

**Josh:** The common flows are code. The rare ones, the "the customer wants something odd" ones, go to a model with a person watching.

**Dev:** Third. When my bare code hits something it can't handle, a refusal it doesn't recognise, or a reasoner that returns Asked, it hands off.

**Josh:** That one's an idea, not built. But the pieces are there. Asked carries the question. Explain gives the server's reason. A person opens an MCP host, picks up from the same bookmark, and finishes the job.

**Dev:** Fourth, heart-mcp is a library too, not just the Docker image.

**Josh:** Yes. If you're building your own agent on the JVM, you can register the six tools on your own MCP server in a few lines. Your agent gets the same boundary without our process.

**Dev:** And fifth, one audit trail.

**Josh:** Both sides are ArtieClient underneath, so both write the same event log, with OpenTelemetry fields, one JSON line per decision. Set ARTIE_EVENTS and you get one file whether a person in chat or your code did the work.

**Dev:** And Buster reads that log.

**Josh:** It can. We haven't run a Buster campaign against an MCP-driven session yet, so I won't claim it.

## What's still open

**Dev:** What don't you know yet?

**Josh:** Whether the hand-off feels natural to a real person. We haven't built it. And whether exploring with a model and then writing code is faster than reading the docs, on a service bigger than ours.

**Dev:** And the HTTP transport. Until heart-mcp has it, with auth, it's one person on one machine.

**Josh:** Right. And our own ArtieAgent has a small gap we already know of. It offers the hr:unavailable link as if it were a choice. That's on the list.

**Dev:** So, one sentence. Bare or wrapped?

**Josh:** Both. Bare ARTIE when you know the path and want it cheap, tested and fast. Wrapped when you don't know the path yet, or a person should be in the loop. And either way, the same client makes the same checks before anything touches the wire.

---

## Where it is in the code

- `client/src/main/kotlin/heart/HeartRiseClient.kt`: enter, act, follow, describe, explain.
- `client/src/main/kotlin/heart/Reasoning.kt`: Offer, LinkOffer, Selection and the Reasoner interface.
- `client/src/main/kotlin/heart/ArtieAgent.kt`: decide, handle and the seven decisions.
- `client/src/main/kotlin/heart/ClaudeReasoner.kt`: the Reason step on Claude.
- `client/src/main/kotlin/heart/Events.kt`: the event log sinks, `ARTIE_EVENTS` and `ARTIE_EVENTS_URL`.
- `mcp/heart-mcp/src/main/kotlin/heart/mcp/HeartMcp.kt`: the six tools.
- `mcp/README.md`, `mcp/docs/HOW-IT-WORKS.md` and `mcp/PROOF.md`.
