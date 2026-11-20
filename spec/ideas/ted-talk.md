# Idea: the talk, eight minutes before the demo

Status: idea, 2026-11-07. A TED-style talk, about eight minutes (about 1,100 words), then eight minutes of demo. Its
frame is `where-heart-fits.md`; its board version is `heart-whiteboard.md`. The two quotes are left as markers: read
them word for word from the links. The time marks are approximate.

## The script

**[0:00]**

Today, every one of you used a piece of software that has never been told what a bank is.

It doesn't know what an airline is. It has never heard of your employer. It has no idea what a shopping cart is for.
And yet, today, you probably used it to move money, check in for a flight, and buy something you didn't need.

It's your web browser. And how it gets away with knowing nothing about any of that is what the next eight minutes are
about.

My name is Josh Gough. I've been writing software for close to thirty years. Tonight I want to talk about what may be
the most widely deployed networking constraint ever built into software. Every one of you depends on it. Most of you,
at a conference like this, make your living on it.

It has a terrible name. HATEOAS. H-A-T-E-O-A-S. Hypermedia As The Engine Of Application State. Nobody loves that
acronym. Stay with me anyway, because the idea behind it is beautiful.

**[1:00]**

A lot of us in backend engineering think Roy Fielding invented this. He didn't. He named it. His dissertation,
published in 2000, didn't propose a new Web. It described the one that already worked. That Web was nearly ten years
old and growing faster than anything before it, and Fielding asked why. Why could it scale like that? How could
millions of sites change every single day without breaking a single browser?

His answer, boiled down: the browser and the site don't share knowledge of each other. They share a media type.

**[2:00]**

Eight years later, Fielding was not happy. In 2008 he wrote a blog post that opens by saying he's getting frustrated.
Frustrated with people calling any HTTP-based interface a REST API. People were building APIs, calling them REST, and
building them as RPC.

RPC is remote procedure call: pretend the network is just a function call inside your own program. The caller knows the
name, the arguments and their order before it ever runs. It's all baked in at compile time.

And you might say: of course! You have to know what to send. And you're right. You do have to know something. But you
don't have to know it at compile time. The Web never did. Your browser doesn't ship knowing about your bank. It finds
out when it gets there.

**[3:00]**

What Tim Berners-Lee, Fielding and the others did was couple the client to media types. text/html. image/png.
text/css. Later, application/json. And HTML is the one that matters, because it gives the browser two things: links
and forms. Not bank things. Links and forms.

Fielding said it best himself, in the comments under that same post:

> [QUOTE 1: read verbatim. Fielding's comment of 20 October 2008, 9:22 am, beginning "Think of it in terms of the
> Web." https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven#comment-720]

And earlier in that same thread is the line I'd tattoo on every API team:

> [QUOTE 2: read verbatim. Fielding's comment of 20 October 2008, 7:44 am, beginning "When I say hypertext, I
> mean…" https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven#comment-718]

Information and controls. Hold on to that word: *controls*. The server doesn't just tell you what is. It tells you what
you can do about it, right now. And if it's not on offer, there's no button.

**[4:00]**

A few years later came Swagger. Today we call it OpenAPI. And let me be fair: it's a gift. Documentation, a contract,
autocomplete in your IDE, a generated client in an afternoon. Most of us have used one.

But notice the trade. A generated client learns the whole API before it runs. Every operation, every field, frozen into
code. It's a printed menu: every dish, every day, whether the kitchen has it or not. And when the kitchen changes, the
menu in your hand doesn't.

The Web made the opposite trade. The menu comes with the meal.

**[5:00]**

Here's why this matters more tonight than it did in 2008. We are building a new kind of client, the most capable
client ever: AI models that read, reason and plan. And what do we hand them? Mostly, a list of tools. The printed menu.
Every operation, up front, whether it's allowed right now or not. Then the model guesses, and the server says no after
the fact, if we're lucky.

We built the smartest clients in history and handed them the way of working the browser escaped decades ago.

So I made the other choice.

**[5:45]**

I built a reference client called ARTIE: Autonomous RESTful Traversal and Interactive Engine. ARTIE is coupled to no
domain and to no API. It's coupled to a media type. And I built that media type too. It's called HEART: Hypermedia
Enforced Agentic Reliable Transactions. Yes, another mouthful. Blame me. But hear the word. *Heart*. Because that's what
this is: the heart of web architecture. Information and controls, moving over one uniform interface. That's the Web's
heartbeat.

A HEART representation carries the state of a resource, and only the actions that are legal at this moment, each one a
form with a schema. If an action is missing, the representation says why. Every answer carries a version. If the world
moved since you last looked, your request is refused, not half-applied. And open that same representation in a plain
web browser, and the actions are buttons.

Lo and behold: a person and a model, using the same controls.

**[6:45]**

"But GitHub doesn't speak HEART." Right. So I put my own gateway in front of it, on my laptop. You may have little
control over the systems you depend on. You can still put your own controls in front of them.

And now the part that changes the economics. The model doesn't have to drive every time. Give it a goal in plain words.
It finds the way once. ARTIE keeps that walk as a script. The next time, the script runs with no model at all. Zero
tokens. You pay for inference once, then as rarely as you like.

That only works while the structure is stable. And when it isn't, when a field moves, a choice disappears, or a deploy
is already running, HEART doesn't let the script barrel on. It fails fast, at the exact step, with the server's own
reason. Where you allow it, the model comes back for just that one step and repairs it. With a person reviewing the
repair, or not: your call.

Tokens aren't the point. They're the evidence. The point is controls.

**[7:45]**

Let me show you. Watch for three things. The model finding its way once. The replay, with no model at all. And a
refusal: the system saying no, and saying why.

Lo and behold.

## The demo it hands to

The kit (`kit/README.md`): a goal walked once by the model, kept with `try.sh keep`, replayed with `try.sh replay` and no
model calls, and the release replay of 1.2 refused before the deploy. If the demo changes, the last section changes with
it.
