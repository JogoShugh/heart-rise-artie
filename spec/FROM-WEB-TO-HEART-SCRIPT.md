# From the Web to HEART: a walk-through for the road

*A spoken version of [FROM-WEB-TO-HEART.html](FROM-WEB-TO-HEART.html), written to be
listened to, for example through a text-to-speech reader while driving. It has no code,
symbols or tables. About 12 minutes read aloud. Written 12 October 2026, against
H.E.A.R.T. 0.5.*

---

This is a story in three parts. First, how the web learned to let a browser use any website without knowing that site in advance. Second, how the APIs most of us build lost that ability. Third, how HEART gives it back, so an AI agent can use a service the way a person uses a website. After that, we take one plain data API and change it, one step at a time, until it speaks HEART.

All the examples use the same thing: a garden bed. You can sow a seed in an empty bed. You can water a bed once something is planted. And you can harvest it when it's ready. What you're allowed to do depends on the state the bed is in right now. Keep that sentence in mind, because it's the whole story.

## Part one: the landscape

It starts between 1989 and 1995, with the web. Tim Berners-Lee put three things together. Addresses, called URIs, to name things. A protocol, HTTP, to move them around. And a format, HTML, to show them. The key part is that HTML carries two things at once: the information, and the controls. The controls are the links you can follow and the forms you can fill in.

So a browser knows HTML and nothing else. It knows nothing about your bank or your favourite shop. The site tells the browser, in every single response, what can be done next. And when the site changes, nobody has to reinstall their browser.

In the year 2000, Roy Fielding wrote his dissertation and gave this architecture a name: REST. One of its rules has an awkward name, "hypermedia as the engine of application state". The idea is simple. The client moves from one step to the next only by choosing among the links and forms the server just sent. That rule is what lets clients and servers change separately without breaking each other.

Then, through the 2000s and 2010s, something got lost. Programs started talking to servers using JSON over HTTP, and people called that REST too. But plain JSON has no links and no forms. So the controls moved out of the response and into documentation. The docs told you which address to build, which method to use, what to send, and which rules applied. Swagger, and later OpenAPI, made that documentation readable by machines. But the result was the same: every client wrote the addresses and the rules straight into its own code.

Fielding objected, in 2008, with a blog post titled "REST APIs must be hypertext-driven". The same year, Leonard Richardson described a maturity model for APIs, with hypermedia on the top level. Most APIs stopped short of it.

From 2011 on, a few formats tried to bring the controls back into JSON. HAL, by Mike Kelly, added links. Siren and JSON Hyper-Schema came along too. Mike Amundsen's HAL-FORMS added forms. And HAL Schema Forms added forms whose fields are described with JSON Schema. They all worked, but they stayed niche. A developer was writing each client by hand from the docs anyway, so the links didn't seem to buy much.

Then came AI agents, from about 2023. Agents call APIs through tool definitions. That can be function calling, an OpenAPI document pasted into the prompt, tool search, or the Model Context Protocol, MCP, from 2024. Every one of these hands the agent a catalogue: a list of what the system can do in principle. None of them tells the agent what's allowed for this particular bed right now. So the agent tries to water bed one, and only finds out from the error that nothing is planted.

That brings us to HEART, in 2026. The name stands for Hypermedia Enforced Agentic Reliable Transactions. HEART puts the controls back in the response, and narrows them to one resource in its current state. The agent gets the bed, plus exactly the acts that bed allows right now. If an act isn't offered, it isn't allowed. The absence is the enforcement.

The reference client is called ARTIE. An agent built on ARTIE can work a garden, a basketball game or a ticket desk, with no code written for any of them.

Here's a simple way to hold it in your head. ARTIE is the browser. HAL Schema Forms, with HEART's profile, is the HTML. A HAL link is a hyperlink. A form in the response is a web form, and its fields are described by JSON Schema. The person deciding what to click becomes the user's goal, or a language model choosing among the acts on offer. The home page you bookmarked becomes the service's single starting address. And that old browser message, "this page has expired", when you resubmit an old form? In HEART that's a 412: you tried to act on a state that has since changed.

## Part two: evolving one API

Now let's take an ordinary data API and add one idea at a time.

**Step zero: the starting point.** You ask for bed one and get back plain data. Its id is one, its name is "bed-1", its status is empty, and its moisture is zero. That's all. Everything else lives in a document the developer read once. The document says: to sow, post to this address with a seed type and a depth from one to ten centimetres, but only if the bed is empty. To water, post to that address with up to a thousand millilitres, but only if something is planted. To harvest, post to a third address, but only when it's ready.

Here's what goes wrong. The client builds the addresses itself, and copies the rules into its own code. When the server changes a rule or an address, the client breaks, or quietly does the wrong thing. An agent given those docs will try acts the bed doesn't allow, and learn the rules by being refused. And two clients acting at the same moment can overwrite each other's work.

**Step one: say it's HAL, and link instead of documenting.** The response now declares its format as HAL, and it carries a links section with a "self" link pointing at the bed's own address. The format tells the client how to read the response, the way "text slash HTML" tells a browser. Addresses now come from the server, so the client stops building them. But HAL alone can only say "go there". It can't say "send this".

**Step two: put the acts in the response, as forms.** This is the big one. The response gains a forms section. On the empty bed, there's exactly one form: sow a seed. That form says where to send it, which method to use, and what the body must look like: a seed type that's required, and an optional depth between one and ten.

What matters most is what's missing. Bed one is empty, so watering and harvesting simply aren't in the response. The client can't even try them. Bed two, which is planted, would offer watering instead. The rule about status has moved out of the client's code and into the server's response. At this point, the API behaves like a website again.

**Step three: give each act a name a stranger can understand.** Three things are added. First, a profile link in the headers, saying "this response follows HEART's profile of HAL Schema Forms", so the client knows which rules it can rely on. Second, the form's name becomes a link relation, written in a short form called a CURIE, like "garden colon sow-seed". That expands to an address that describes the act in a small vocabulary document called an ALPS profile. The client learns all of this from the response itself, during the conversation, not beforehand. Third, every form gets a title and a description, and every field gets a title, a description and an example. Those words are written for whoever is choosing: a person, or a language model.

**Step four: act only on the state you saw.** When the client reads the bed, the server sends a version tag, called an ETag. When the client acts, it sends that tag back in a header called If-Match. If the bed changed in between, the server refuses.

Every refusal comes back in a standard error format, called problem details, with its own type. The server checks them in a fixed order:

- **428:** you didn't send a version at all.
- **412:** your version is stale; the bed changed since you looked.
- **409:** the act isn't offered in the bed's current state. For example: "harvest is not offered now; nothing is growing yet."
- **422, invalid input:** the body breaks the form's rules, like a depth of fifty when the maximum is ten.
- **422, rule refused:** the body is valid, but a rule of the bed itself says no. For example: "the bed would be waterlogged; at most six hundred millilitres now."

Why this matters: two clients can no longer overwrite each other. On a 412, the client reads the bed again and decides fresh. It never resends the old act. And because each code means exactly one thing, a client can handle refusals without reading the text.

**Step five: start from one bookmark.** The service's starting address offers three forms. Search for beds by name. Go to a bed by its number. Or build a new bed. So a client only needs to know one address. Notice that a new bed doesn't come from some generic "create" button on a list. It comes from a real act, "build a bed", with its own form and its own rules. The same starting address can also describe itself in a format called JSON Home, which lists the formats and options the server supports.

**Step six: split the response into layers, so the forms can be cached.** Until now, every response repeats every form and every schema. That's correct, but wasteful, because many beds are in the same state and offer exactly the same forms.

So a client can ask, with a header called Prefer, for the forms and the schemas to be sent as separate resources. Now the bed's response is small: just its data, plus one link to its set of forms. That link carries a fingerprint, a hash of the set's contents.

The set of forms is shared by every bed in the same state, and can be cached for a year. Because one set serves many beds, its target address has a blank in it for the bed's number. A small pointer in the form says where to fill that blank from: the id field of the bed that linked here. Each form then points to its schema, again with a fingerprint, and a schema can be shared by every form that uses it.

Why it works: the bed itself is never treated as fresh, because it can change at any moment. The forms and schemas are named by their contents, so they never change at all. The client checks each one against the fingerprint in the link that led to it, so even a year-old cached copy can be trusted. And checking an unchanged bed again costs one small request, answered "not modified".

These choices form a ladder, and a server can climb it one rung at a time:

- **Level zero:** everything in one response. That gives correct behaviour, but it's an on-ramp, not full conformance.
- **Level one:** the forms move out to a cached set.
- **Level two:** the schemas move out too. This is the reference rung, and the minimum to claim conformance.
- **Level three:** proving the sharing works. Two beds, two clients, one cache, one fetch.
- **Level four:** concurrency and refusals, as in step four.
- **Level five:** the same acts also served in HAL-FORMS, as an easy on-ramp.
- **Level six:** optional. A language model chooses the act. A HEART client never needs a model.

**Step seven, optional: explain what's missing.** The forms set can link to a short list of the acts that are absent, and why. For an empty bed: you can't water because nothing is planted, so sow a seed first. And you can't harvest because nothing is growing yet. When someone asks "why can't I water bed one?", the client answers in the server's own words. It only fetches this list when asked, and never acts on it.

## Part three: the agent's loop

A HEART client runs three steps, over and over: sync, reason, act.

**Sync.** Read the resource, check its profile, follow and verify the layers, and hold on to the bed, its current forms and its version tag.

**Reason.** Pick one of the offered acts and fill in its fields. Plain code can do this, or a language model can. The model sees only the forms, with their titles, descriptions and field rules. Its entire output is a choice, like "water the bed with two hundred and fifty millilitres", or "follow the link to bed two", or "ask the user a question". It has no way to write an address, a method or a header.

**Act.** Ordinary code fills in the address, sets the method and the version tag, checks the body against the schema, and sends it. The answer is the bed's new state with its new forms, so the loop starts again. If the act is refused, the client syncs and reasons again.

Picture it as a short conversation. The agent opens the bookmark and sees three choices: search, go to a bed, or build one. It searches for bed two and gets a link. It opens bed two and receives the state, only the legal forms, and the version tag. The user says, "water bed two with two hundred and fifty millilitres." The agent picks the water form, fills in the amount, and sends it with the version. The server answers with the new state and the new choices, or with a clear refusal.

## Part four: the checklist

To turn a data API into HEART:

1. Serve HAL, and declare HEART's profile in a header.
2. Give every resource a link to itself, and link instead of documenting addresses.
3. Put the acts in the response as forms, with an address, a method and a schema, and only the acts that are legal right now.
4. Name each form with a link relation described in a profile, with a title and a description, and describe each field.
5. Send a version tag on every resource, and require it back on every act.
6. Refuse in a standard format, checked in order: 428, then 412, then 409, then 422.
7. Offer one bookmark that reaches everything, including building new things.
8. When asked, split out the forms and schemas into shared, cached, fingerprinted layers, and say which layers you applied.
9. Optionally, explain the acts that are missing.

## One closing note on status

HEART is a working draft, version 0.5. Levels zero to two run live against the reference garden server and the ARTIE client. Level six has been tested with a scripted reasoner, and once with a real model. The companion protocol for system-to-system exchange, called RISE, isn't built yet.

And the one sentence to take away: what you're allowed to do depends on the state of the thing right now, so the thing itself should tell you.
