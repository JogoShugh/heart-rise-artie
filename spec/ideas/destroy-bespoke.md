# Idea: destroy bespoke — a CTO's case against a model, or a vendor, in the middle of everything

Status: idea, 2026-11-06. A talk, in the words it would be spoken; nothing to build. Its evidence is the kit trial
(exhibits/kit-trial, docs/reports/kit-trial). Spoken, so no tables: the numbers are said. The vendor is never named.

## The talk

Let me be blunt about where this industry is going, and why we are not going with it.

Right now the pitch you hear everywhere is this: put a model in the middle of everything. Let an agent read the API
docs and figure out what to call. Let it figure it out again tomorrow, and the day after, and every time. And if you
want that at enterprise scale, a vendor will send a team of engineers to sit in your building and wire your systems
together inside their platform. Then they rent your own business logic back to you, by the seat, by the token, by the
year.

I've seen what that costs. I've watched a vendor try to take control of an integration layer that was ours. We said
no. We built eight services instead, and we kept the keys. So when someone tells me the future is bespoke AI, built
fresh for every customer and every run, I hear something very old with a new coat of paint. Bespoke is the most
expensive word in enterprise software. It always has been.

Here is what we built instead, and here is what it proved.

Every system gets a front door, one address you can start from and reach everything else by following links. Every
system speaks one format: the same kind of document, with the same kind of forms in it, saying what you can do right
now. And every system can show the same thing two ways. Ask with a browser and you get a screen with buttons. The
buttons you're allowed to press are live. The ones you aren't are greyed out, and they tell you why. Ask with a
program and you get the same thing as structured data, with the same forms and the same choices. Same address, same
rules, one source of truth. Nobody keeps a second copy of the rules in client code that drifts out of date.

Then we let a model loose on it exactly once. We gave Claude a goal in plain English. Ship release one point one:
queue the build, deploy it to staging, record it on GitHub. It started at the front door, a single file that linked a
build service, a deploy service and GitHub, three systems that know nothing about each other. It followed links. It
read what each system offered. It filled in the forms. Fifty-two seconds, twenty-two model calls. Then we kept that
path as a script, pinned to exactly what each system offered at that moment. And we ran it again. Six seconds. Zero
model calls. Zero tokens.

We did that with four goals. Opening and closing issues. Triaging new issues by reading what they say. Closing out
issues the way their labels say they ended. And that release across three systems. Finding the way took eighty-five
model calls in total. Replaying all of it took three. Those three were on purpose. When an issue comes in, someone has
to read it and decide whether it's a bug or a question, and that judgement is the one place we want a model.
Everything else was the same steps, the same way, every time. Four out of five replays never touched a model at all.

Now watch what happens when something is wrong, because that is where you find out whether a system is serious.
Release one point two failed its build. So the deploy service simply never offered one point two as a choice. A person
looking at the screen couldn't pick it. The machine reading the form couldn't pick it. When we tried to replay the
release for one point two anyway, the client refused before sending a single byte: one point two is not one of the
choices. No model had to be clever. No guardrail prompt had to be written. The system said what was allowed, and that
was the end of it. While a deploy was running, the Deploy button greyed itself out and said a deployment is in
progress. Every change carried a check that refuses to overwrite a state it never saw. And if a system ever changes a
form underneath us, the replay stops at that exact step and says so. It doesn't guess. It doesn't improvise. It stops
the line.

That phrase isn't mine. It's Toyota's. For seventy years the best manufacturers on earth have run on a few ideas.
Standard work: you find the best way to do a thing once, you write it down, and you do it that way every time. Stop
the line: the moment something is abnormal, you halt. Mistake-proofing: you make the wrong action impossible, not
merely discouraged. Our saved script is standard work. Our replay that halts on a changed form is stopping the line. A
release that isn't offered is mistake-proofing. None of this is new. It's what businesses have always wanted from
their systems, and the AI hype skipped right over it.

Deming spent his life telling companies that quality means reducing variation. A model in the middle of every run
builds variation into every run. Ask it the same thing twice and you can get two different paths. And reliability
compounds. If every step is ninety-five percent reliable, which sounds great in a demo, a twenty-step process succeeds
about one time in three. At ninety-nine percent per step, a fifty-step process still fails about four times in ten.
You cannot run a business on that. You can run a business on a replay that does the same thing every time, or stops
loudly at the one step that changed.

Fred Brooks told us in 1986 to separate the hard part of software, the problem itself, from the self-inflicted part,
the friction we add. A custom client for every API is pure self-inflicted friction. David Parnas told us in 1972 that
a good design keeps a change in one place. With bespoke clients, every server change ripples into every client. In
our world, the server changes its form, and every client reads the new form, person and machine alike. Lientz and
Swanson showed around 1980, and study after study since has confirmed, that most of a system's lifetime cost is
maintenance, not building. Bespoke maximizes what you have to maintain. Then there's simple arithmetic. Wire ten
systems together pairwise and you can end up with forty-five integrations. Twenty systems, a hundred and ninety. Give
them one format and one generic client, and you have twenty systems that speak it and one client that reads them all.

And when Google's own engineers wrote about the hidden technical debt in machine learning systems in 2015, what did
they find? The model was the small part. The real cost was the glue around it. An agent that rediscovers your APIs on
every run is that glue, rewritten every time and billed to you every time.

Roy Fielding wrote down the rules of the web in 2000. The most important one says the server tells the client what it
can do next, through links and forms. Don't hard-code it in the client. That is the most followed network constraint
of all time. Every browser on the planet does it. And it is the least followed on the machine web, because programs
never had anyone to read those links the first time. Now they do. The model reads them once, like a person would.
After that, plain code follows the same links, at no cost, with no surprises.

So here's the line I'm drawing. We use models where judgement or exploration is actually needed: finding the way,
reading what a person wrote, repairing a step when a system changes. We don't pay a model to rediscover yesterday's
path. We don't let a vendor encode our business inside their platform and rent it back. We don't write a client per
API, a prompt per run, a scraper per screen, or glue per pair of systems.

And if you're already inside someone else's platform, you don't need a heroic migration. You put your own front door
in front of it, the way we put a gateway in front of GitHub, a system we don't control. You move one route at a time,
until the old thing is just a backend you call, behind your own controls.

I'll be straight with you about what's proven and what isn't. GitHub in that demo is real GitHub, reached through our
own gateway. The build and deploy services are simulations. A browser sign-in for those screens is still to be
built. But the pattern is real, the numbers are real, and every screenshot was taken live.

The future isn't bespoke. It isn't a model in the middle of everything, and it isn't a consultant in the middle of
everything. It's systems that say what they allow, in one language, to anyone who asks. A model finds the way once.
Then the business runs on rails.

## Before it is given

- The research named is from memory: check each source and date before quoting it (Brooks, "No Silver Bullet",
  1986; Parnas, "On the Criteria To Be Used in Decomposing Systems into Modules", 1972; Lientz and Swanson, Software
  Maintenance Management, 1980; Sculley et al., "Hidden Technical Debt in Machine Learning Systems", NeurIPS 2015;
  Fielding's dissertation, 2000).
- The trial's numbers are from exhibits/kit-trial/README.md; rerun them if the kit changes.
