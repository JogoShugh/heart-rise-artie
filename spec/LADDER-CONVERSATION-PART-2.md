# Only One Step Guesses: A Conversation About L4 to L6

Part two of [Inline, Then Linked](LADDER-CONVERSATION.md). Josh and Dev take the rest of the ladder: L4, how errors and concurrency are told apart; L5, two dialects that must agree; and L6, reasoning, the one place inference enters a running system. Everything around it is deterministic, tested code. About fifteen minutes read aloud.

Samples are trimmed from the spec, the client's code and real runs, and the speakers say what each one shows.

---

## Not more caching

**Dev:** Last time we stopped at L3. I assumed L4 meant "even more caching."

**Josh:** Everybody does. But the ladder bends there. L0 to L2 are one progression, how much is inline and how much is linked. L3 is the caching property that falls out of it. L4, L5 and L6 are separate axes. An L0 server can do L4. An agent can run L6 against L0.

**Dev:** So they're more like badges than rungs.

**Josh:** Close enough. And the spec says L6 is never required. A HEART client with no model at all is fully conformant.

## L4: two different kinds of no

**Dev:** L4 is concurrency and errors. Start me with the obvious one. Two people water bed 2 at the same time.

**Josh:** Both read bed 2 at the same version. ARTIE holds the ETag, say `"seq-3.hsf.L2"`. Both send the water act with `If-Match` carrying that ETag. The first one wins, and the bed moves to the next version. The second one gets this.

```http
HTTP/1.1 412 Precondition Failed
```

**Dev:** In words: your precondition failed. The version you said you were acting on is gone.

**Josh:** And the rule for 412 is strict. Re-sync from scratch, and never resend the stale act. The spec says SHALL NOT.

**Dev:** Why not just retry? It's the same request.

**Josh:** Because the intent may mean something different now. "Water bed 2" was sensible when the bed was dry. Someone else just watered it. Maybe it's now ready to harvest and watering isn't even offered. A blind retry overwrites a decision you didn't see.

**Dev:** And 409?

**Josh:** 409 means "that was never legal here." The act isn't in the current manifest. The spec's example is the interesting one: the client holds a fresh ETag and still gets 409. Sowing into an occupied bed is illegal, however current your view is.

**Dev:** Freshness isn't legality.

**Josh:** Right. A client that retries a 412 overwrites someone. A client that treats a 409 as temporary loops forever. Two codes, two paths, never collapsed.

**Dev:** And 422?

**Josh:** Two kinds there too, told apart by the problem type. `invalid-input` means the body broke the schema. `rule-refused` means the body was fine, but a rule the schema can't express said no.

```json
{
  "type": ".../problems/rule-refused",
  "title": "A rule of the resource refuses the act",
  "status": 422,
  "detail": "… The bed would be waterlogged: at most 600 ml now."
}
```

**Dev:** In words: the schema allows up to 1,000 millilitres, but this bed, right now, can take only 600.

**Josh:** And there's 428 for an act sent without `If-Match` at all. ARTIE never does that, but the server has to say so if someone does.

**Dev:** So in ARTIE, each of those is what?

**Josh:** A type. `StaleAct` for 412, `IllegalTransition` for 409, and for 422, `InvalidPayload` or `RuleRefused`, told apart by the problem type. Each has its own scenarios. The client suite has 136 scenarios, and a good share of them are exactly these paths: stale, not offered, invalid, rule-refused, and integrity failures.

**Dev:** And you've attacked it.

**Josh:** Buster. It's a hostile server that mutates real responses: wrong hashes, missing headers, stale ETags, illegal acts. The last campaign was 125 mutations, and ARTIE failed none. That's the deterministic side being tested the way deterministic code should be.

## L5: two dialects, one truth

**Dev:** L5 we half covered last time. HAL-FORMS and HAL Schema Forms.

**Josh:** Right. What L5 adds is the conformance rule. Ask for bed 2 both ways. The bytes differ, the hashes differ, the cache entries differ. What must not differ is the legality decision: which acts are offered on bed 2 right now.

**Dev:** And how do you make sure they agree?

**Josh:** One source of truth. The library computes legality once, from the domain's declaration, and renders it twice. The spec requires that. A server where the two dialects disagree about what's legal is broken in a way no cache can hide.

**Dev:** So L5 is a test that the rendering didn't sneak in a rule.

**Josh:** That's a good way to put it. `Vary: Accept, Accept-Profile` on the state, so a cache never mixes them up.

## L6: the one step that guesses

**Dev:** Now the one I actually wanted to talk about. Reasoning.

**Josh:** The spec calls it "deliberately the thinnest rung in the ladder." And I think it's the most misunderstood part of the whole project.

**Dev:** People hear "AI client" and picture the model driving everything.

**Josh:** Making the HTTP calls, reading the docs, building URLs. None of that. The cycle is Sync, Reason, Act. Sync and Act are ARTIE's deterministic code. Reason is one function call.

```kotlin
fun interface Reasoner {
    fun reason(intent: String, state: JsonObject, offers: List<Offer>, links: List<LinkOffer>): Selection
}
```

**Dev:** In words: give the reasoner the user's intent, the resource's state, the offered acts and the offered links, and get back one selection.

**Josh:** One. And look at what goes in. An `Offer` is a name, a title, a description and a schema. A `LinkOffer` is a relation and a title. There's no href in either. The comment in the code says it plainly: "a reasoner that is never shown a URI cannot hallucinate one."

**Dev:** And what can come out?

**Josh:** Five things. Act, with an affordance name and fields. Follow, with a relation and a title. Describe, to ask for the longer description of an act. Explain, to ask why an act isn't offered. Or Clarify, to ask the user a question.

**Dev:** No field for a URL.

**Josh:** No URL, no method, no header, no precondition. The spec's phrase is that structural hallucination isn't reduced, it's made inexpressible. There's nowhere in the output to put a made-up address.

**Dev:** But the model could still name an act that isn't there.

**Josh:** It could. And then a deterministic check catches it before any request. The agent looks the name up in the manifest it just synced. Not there: rejected, with the reason "not in the current manifest." Nothing is sent. Same for fields: they're checked against the act's schema before sending. Two thousand millilitres is rejected locally, because the maximum is a thousand.

**Dev:** And that rejection is a test case.

**Josh:** A scenario outline with several cases, each one a selection that fails a check and sends nothing. And every rejection comes back as a typed decision, Rejected, with its reasons, so your code can count them. The event log shows no request went out. A bad choice is a measurable event, not a bad request on someone's server.

## How narrow the choice is

**Dev:** Here's what I think people miss. How many choices does the model actually have?

**Josh:** On bed 2, while it's planted and dry? One act, water. A couple of links. That's it.

**Dev:** So the model isn't designing an API call. It's picking from a menu of three.

**Josh:** And the menu is the server's, for this resource, at this moment. That's the narrow band. The server already decided what's legal. The model only decides which of the legal things matches what the user meant, and with what values.

**Dev:** Which is the part that actually needs judgement. "About 500 ml" to the integer 500.

**Josh:** Or "the tomatoes look thirsty" to the water act. Or "harvest it" on a dry bed to: that's not offered, here's the bed's reason, from Explain.

**Dev:** And when it's ambiguous?

**Josh:** Clarify. The model asks the user rather than guessing, and ARTIE turns that into a decision called Asked. Nothing is sent. There's a scenario for that too. And one more: the model may answer in words instead of calling the tool. That's always treated as a question for the user, never as an act.

**Dev:** Why that rule?

**Josh:** We found it running a real model. The current models refuse a forced tool call, so `ClaudeReasoner` sends `tool_choice: auto`. Which means the model may just talk. So "talk" was given a safe meaning: it's a question, not a command.

## What the prompt looks like

**Dev:** How is ClaudeReasoner built? I'd expect a big prompt.

**Josh:** Small, and arranged for the prompt cache. The tool definition and the system prompt never change, so they're a cached prefix. Everything that changes, the state, the offers, the links, the intent, is the last user message.

**Dev:** So a manifest change only invalidates the tail.

**Josh:** Right. And the tool's input schema is generic on purpose. If we put this turn's act names into it, the cached prefix would change every time the manifest did. There's a scenario that checks the manifest stays last, behind the fixed prefix.

**Dev:** Has it run against a real model, or only scripted?

**Josh:** Both. Most scenarios use a scripted reasoner, so they're deterministic and fast. A separate task, `llmTest`, runs the real model. On 10 October it asked a real model, against the live garden, to "please water bed 2 with 250 ml." It chose `garden:water-cell` with 250. ARTIE's checks passed it, the server accepted it, and the bed's own history ended with that watering.

**Dev:** One run.

**Josh:** One scripted live run, and it's kept out of the normal live suite on purpose. Real models cost money and aren't repeatable. The deterministic suite is the safety net. The live model run shows the seam works.

## Where inference lives

**Dev:** Let me say the whole thing back. Everything in ARTIE is ordinary code, with ordinary tests, except one function.

**Josh:** Yes. Sync is code: content negotiation, integrity checks, caching, revalidation. Act is code: the schema check, template expansion, `If-Match`, the refusal types. The checks between Reason and Act are code. Only the selection is inference.

**Dev:** And that one function is optional.

**Josh:** Completely. You can write a reasoner from plain rules. Or skip it and call act and follow yourself. The spec says a deterministic workflow engine following relation names is fully conformant and never reaches L6.

**Dev:** And heart-mcp?

**Josh:** Same seam, moved outward. The host's model is the Reason step. heart-mcp shows it exactly ARTIE's Offer and LinkOffer shapes, and checks its choice the same way before ARTIE sends anything. The model is different; the boundary isn't.

**Dev:** So if someone asks "where does the AI run in production?"

**Josh:** In one place. Choosing one of a few legal moves, which the server offered, for this resource, right now. And whatever it chooses is checked by code before it touches the wire.

## What's open

**Dev:** What don't you know yet?

**Josh:** How a model does on a big manifest. Our resources offer a handful of acts at a time. A resource with forty acts is a different test. We haven't run one.

**Dev:** And the real-model coverage is thin.

**Josh:** Two `@llm` scenarios and the heart-mcp runs. The deterministic paths are well covered. The model's judgement is not, and judging judgement is a different kind of test. I'd rather say that than pretend a passing run proves it.

**Dev:** One sentence, then.

**Josh:** The server says what's legal, the code makes sure that's all that can happen, and the model only picks which legal thing you meant.

---

## Where it is

- `spec/SPEC.md` §6.2 (error codes), §6.8 (Sync, Reason, Act), §6.10.5 to §6.10.7 (L4, L5, L6).
- `client/src/main/kotlin/heart/Reasoning.kt`: `Offer`, `LinkOffer`, `Selection`, `Reasoner`.
- `client/src/main/kotlin/heart/ArtieAgent.kt`: the checks and the seven decisions.
- `client/src/main/kotlin/heart/ClaudeReasoner.kt`: the prompt-cache layout and `tool_choice: auto`.
- `client/src/main/kotlin/heart/Refusals.kt`: the refusal types.
- `client/src/test/resources/features/reason-l6.feature` and `conditional-acts.feature`.
- `hacklog.md`, 10 October 2026: the first real-model run.
