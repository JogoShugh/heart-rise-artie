# Bind It Late: A Conversation About the Web

Josh and Dev talk about how the Web was built, why Roy Fielding's rules work, and the idea that holds them together: late binding. About fifteen minutes read aloud.

Every code sample is small, and the speakers say what it shows, so you can follow by ear. Quotes from Fielding and Alan Kay are word for word. Anything else is our own reading, and we say so.

---

## Why nobody reinstalls the browser

**Dev:** Here's what I keep coming back to. My bank redesigned its whole site last month. New menus, a new transfer screen. And I didn't install anything.

**Josh:** Nobody did. Compare that to the bank's mobile app. Change one screen there and you ship a new build, wait for review, and nag people to update.

**Dev:** So the browser knows nothing about banks. It knows HTML, it knows HTTP, and it follows what the server sends.

**Josh:** Right. Which is a strange thing to say about software. Most programs know exactly what they're calling before they run. The browser decides at the last moment.

**Dev:** That's late binding.

**Josh:** That's the word I wanted to get to. And it's the thread through this whole conversation.

## Late binding, in plain terms

**Dev:** Let me try to define it, and you correct me. Binding is when a name gets tied to the thing it means. Early binding happens before the program runs: the compiler or linker fixes it. Late binding waits until the moment of use.

**Josh:** That's it. In C, a function call is usually fixed by the linker. The address is baked in. In Smalltalk, you send a message to an object, and the object decides at that moment what the message means.

**Dev:** Objective-C kept that idea. Every method call goes through a runtime lookup. You can even add methods to a class while the program runs.

**Josh:** And Erlang lets you load new code into a running system without stopping it. Phone switches stayed up while they were upgraded.

**Dev:** JavaScript too. You look up a property on an object, and what you get depends on what's there right now, not what was there when you wrote the code.

**Josh:** Alan Kay put it strongest. In a 2003 email he wrote that object-oriented programming, to him, means "only messaging, local retention and protection and hiding of state-process, and extreme late-binding of all things."

**Dev:** "Of all things." That's a big claim.

**Josh:** It is. The payoff is that parts can change without everyone else changing with them. The cost is that you find some mistakes later, at run time instead of at compile time.

**Dev:** And the Web picked that trade on purpose.

**Josh:** That's my reading, yes. Fielding uses the words "late binding" only once in the REST chapter of his dissertation. But once you see the idea, it's in every constraint.

## The early Web, and its deliberate choices

**Dev:** Give me the short history. I know Tim Berners-Lee wrote a proposal at CERN in 1989.

**Josh:** Right. Three pieces came out of that: the URL to name things, HTTP to fetch them, and HTML to describe them. The first HTTP had one method, GET.

**Dev:** And links were one-way. You could link to anyone without asking them.

**Josh:** Which meant links could break. Hypertext researchers before him thought that was unacceptable. Berners-Lee accepted broken links so the thing could grow without any central control.

**Dev:** That's a late-binding choice too, isn't it? A link doesn't promise what's at the other end. You find out when you follow it.

**Josh:** Good point. Then Mosaic came in 1993, and the Web exploded. HTTP was a mess of extensions. Fielding was one of the authors of HTTP/1.0 in 1996, and the lead editor of HTTP/1.1. He also co-wrote the URI spec.

**Dev:** So he wasn't theorizing from outside. He was fixing the real protocol while millions of people used it.

**Josh:** And he needed a way to judge proposed changes. Does this extension help the Web or hurt it? The dissertation in 2000 is that yardstick. He called it REST.

## The constraints, one at a time

**Dev:** Let me take the first one. Client-server. Separate the user interface from the data storage.

**Josh:** So each side can evolve on its own. The browser and the server are built by different people, on different schedules.

**Dev:** Second, stateless. He says "each request from client to server must contain all of the information necessary to understand the request."

**Josh:** The server doesn't remember where you are in your conversation with it. Your position lives with the client.

**Dev:** Which is why any server in a cluster can answer any request.

**Josh:** Yes. The binding between a request and a machine happens late, request by request. Third is cache. A response says whether it can be reused.

**Dev:** So a copy near me can answer instead of the origin. Fourth is the big one, the uniform interface.

**Josh:** He calls it "the central feature that distinguishes the REST architectural style from other network-based styles." And he breaks it into four parts.

**Dev:** I'll read them: "identification of resources; manipulation of resources through representations; self-descriptive messages; and, hypermedia as the engine of application state."

**Josh:** Take the first one, identification. Here's where his one use of "late binding" sits. He defines a resource as "a conceptual mapping to a set of entities," not the entity itself. And he says that definition "allows late binding of the reference to a representation, enabling content negotiation to take place based on characteristics of the request."

**Dev:** So the address names the idea, say "today's weather in Paris", and what bytes you get is decided when you ask. HTML for a browser, JSON for a program.

**Josh:** And he adds that it lets an author "reference the concept rather than some singular representation of that concept, thus removing the need to change all existing links whenever the representation changes."

**Dev:** Second part, manipulation through representations. I don't touch the server's database. I send a representation of what I want.

**Josh:** Third, self-descriptive messages. Each message says what it is. The media type is the label.

**Dev:** Content-Type: application/json, that kind of thing.

**Josh:** Though plain JSON says very little. We'll come back to that. Fourth part, hypermedia as the engine of application state.

**Dev:** The response tells you what you can do next, through links and forms. You don't have a list of endpoints in your head. You have the representation in front of you.

**Josh:** And that's late binding of the whole workflow. What's possible is decided by the server, at the moment you look.

**Dev:** Then layered system. A client can't tell if it's talking to the origin or a proxy, a cache or a gateway.

**Josh:** Which only works because the messages are self-descriptive. A proxy can read them without knowing your app.

**Dev:** And last, code on demand. The one that's optional.

**Josh:** He writes that it "simplifies clients by reducing the number of features required to be pre-implemented. Allowing features to be downloaded after deployment improves system extensibility."

**Dev:** "Downloaded after deployment." That's late binding of behaviour itself. It's JavaScript in the browser.

**Josh:** And he's honest about the cost. It "reduces visibility, and thus is only an optional constraint within REST."

## How the industry drifted

**Dev:** So if this all works so well, why do most APIs ignore it?

**Josh:** I don't think anyone was foolish. Programmers came to the network with remote procedure calls in mind. Call a function, get a result. SOAP formalised that. Then JSON APIs simplified it, and OpenAPI described them.

**Dev:** And all of that is early binding. You read the docs or generate a client, and the endpoints and fields are baked in at build time.

**Josh:** Which is very productive, until the server changes. Then every client breaks or needs a new release.

**Dev:** The mobile app problem again.

**Josh:** Exactly. In 2008 Fielding got tired of seeing these called REST. He wrote a short post, "REST APIs must be hypertext-driven." The famous line: "if the engine of application state (and hence the API) is not being driven by hypertext, then it cannot be RESTful and cannot be a REST API. Period."

## The six rules, heard as late binding

**Dev:** The post has six rules. I've always read them as a list of don'ts. Let's read them as late binding instead.

**Josh:** First: "A REST API should not be dependent on any single communication protocol." The protocol is bound late. HTTP today, something else tomorrow, as long as the meaning maps over.

**Dev:** Second: "A REST API should not contain any changes to the communication protocols aside from filling-out or fixing the details." So you don't invent private extensions that every client would have to know in advance.

**Josh:** Third, and I think it's the most important: "A REST API should spend almost all of its descriptive effort in defining the media type(s) used for representing resources."

**Dev:** Because the media type is the one thing the client does bind early. It's the contract. Everything else is learned at run time.

**Josh:** Fourth: "A REST API must not define fixed resource names or hierarchies (an obvious coupling of client and server)."

**Dev:** So no "the orders live at slash orders slash ID" baked into clients. The server hands you the address.

**Josh:** Fifth: "A REST API should never have 'typed' resources that are significant to the client." The client shouldn't think "this is an Order object, so it has these methods." It should read what the representation offers.

**Dev:** And sixth: "A REST API should be entered with no prior knowledge beyond the initial URI (bookmark) and set of standardized media types."

**Josh:** A bookmark and the media types. That's all you bind early. Everything else, late.

## What the media types look like

**Dev:** Show me. What does a representation look like that a program can follow?

**Josh:** Here's a garden bed in HAL, a common JSON hypermedia format. It's simplified from our garden server.

```json
{
  "bed": 2,
  "moisture": "dry",
  "_links": {
    "self": { "href": "/beds/2" },
    "garden:water-cell": { "href": "/beds/2/water", "title": "Water this bed" }
  }
}
```

**Dev:** So in words: bed 2 is dry, here's its own address, and there's one link named "water-cell" with a title, "Water this bed."

**Josh:** And notice what's not there. No harvest link. You can't harvest a dry bed, so the server doesn't offer it.

**Dev:** The client doesn't need a rule saying "only harvest when ready." The absence of the link is the rule.

**Josh:** Fielding said it in a comment under his post: "the information becomes the affordance through which the user (or automaton) obtains choices and selects actions."

**Dev:** "Or automaton." He was thinking about programs, not just people.

**Josh:** Now, a link says where. It doesn't say what to send. HTML has forms for that. For JSON, one option is to pair the act with a JSON Schema.

```json
{
  "name": "garden:water-cell",
  "title": "Water this bed",
  "schema": {
    "type": "object",
    "properties": { "milliliters": { "type": "integer", "minimum": 1, "maximum": 1000 } },
    "required": ["milliliters"]
  }
}
```

**Dev:** In words: the act "water this bed" takes one field, millilitres, a whole number from 1 to 1000. That's a form for machines.

**Josh:** And when the server says no, it should say why in a standard way. That's problem details, RFC 9457.

```json
{
  "type": ".../problems/rule-refused",
  "title": "A rule of the resource refuses the act",
  "status": 422,
  "detail": "… The bed would be waterlogged: at most 600 ml now."
}
```

**Dev:** So: refused by a rule, status 422, and the bed's own reason, which a person or a program can read. The schema allowed 1000, but the bed's state allowed only 600 right now.

**Josh:** The limit was bound late. The schema says what's possible in general. The state says what's possible this minute.

**Dev:** And the last piece is the vocabulary. Who says what "water-cell" means?

**Josh:** A profile, like ALPS. It's a published list of the words a service uses and what they mean. The client binds to those words, not to addresses.

## Where HEART lands

**Dev:** So where does HEART fit? It sounds like you're repackaging what the Web already did.

**Josh:** Partly, yes, and I'm fine with that. HEART is the browser's deal, written down for machine clients. Every response carries its state, its links and its offered acts with schemas. A client starts from a bookmark and binds to everything else when it looks.

**Dev:** And ARTIE is the client.

**Josh:** ARTIE is our client library. It reads the representation, checks the schema before sending, and sends the act with If-Match, so it acts only on the version it saw. If someone else changed the bed first, the server says 412 and ARTIE reads again rather than retrying blindly.

**Dev:** Here's the bit I find interesting. Fielding's rules always had one weak spot. A browser can follow links because a human is reading the screen and choosing. A program had to be told in advance which link to pick.

**Josh:** Which pushed people back to early binding.

**Dev:** But a language model can read "Water this bed" and decide. It's a reader that binds at run time.

**Josh:** That's the bet. In heart-mcp, the model never sees an address. It gets six fixed tools, like the browser's buttons: look, act, follow, describe, explain, back. What changes is what comes back. The model chooses an act by name, and our code builds the request.

**Dev:** So the model is the late binder, and ordinary code keeps it honest.

**Josh:** I'd put it that way.

## What's still open

**Dev:** What don't you know yet?

**Josh:** Plenty. We've run it against three small services we wrote ourselves. Nobody outside has built a HEART service yet. And late binding has its old cost: you find some mistakes at run time.

**Dev:** And the media types are new. Fielding's third rule says to spend the effort there, and that's exactly where we're youngest.

**Josh:** Agreed. I'd rather say that plainly than pretend it's finished.

**Dev:** So, one sentence. Why does any of this matter?

**Josh:** Because binding late is what let the Web change for thirty years without anyone reinstalling their browser. We'd like programs, and now models, to get the same deal.

---

## Sources

- Roy T. Fielding, *Architectural Styles and the Design of Network-based Software Architectures*, chapter 5, 2000: <https://ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm>
- Roy T. Fielding, "REST APIs must be hypertext-driven," 20 October 2008, with its comments: <https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven>
- Alan Kay, email to Stefan Ram, 23 July 2003: <https://purl.archive.org/stefan_ram/pub/doc_kay_oop_en>
- RFC 9457, Problem Details for HTTP APIs: <https://www.rfc-editor.org/rfc/rfc9457>
