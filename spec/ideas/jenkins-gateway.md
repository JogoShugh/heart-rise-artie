# A HEART gateway for Jenkins

An idea, parked 2026-10-17. Written to be listened to: no code, no tables. Not started.

Two layers, kept apart. The gateway stays configuration only, as for GitHub: a connector
folder of YAML, plus a small OpenAPI description of the Jenkins slice, written by us
because Jenkins publishes none. The "scripts" below are heart-script clients, .kts files
run with artie run, like garden-bed.kts and github-comment.kts: they talk to the gateway
from outside and do the summarising. No scripting inside the gateway.

## What we want

You want to work Jenkins from scripts, not from its web pages. Four things matter most.
First, start a job, with or without parameters. Second, see how one job is doing: is it
running, and how did its last run go. Third, see one build: its result, how long it took,
when it ran. Fourth, and most important, a rollup for a whole folder: for every job in it,
the last run, the last success, the last failure, and how long the last run took. All of
it through the HEART gateway, so the same heart-script DSL that just drove GitHub and the
garden drives Jenkins too, knowing one address, the bookmark, and nothing else.

## What Jenkins gives us

Jenkins has a good remote API, and it is older and more uniform than most people think.
Almost every page in Jenkins has an API twin: add "api slash json" to the end of a page's
address and you get the same thing as JSON. A job, a folder, a build, the queue, the
whole server. Three features make it powerful.

The first is the tree parameter. You tell Jenkins exactly which fields you want, nested as
deep as you like, and it returns only those. So in one request you can ask a folder for
every job in it, and for each job, its last build, its last successful build, its last
failed build, and its last completed build, each with its number, result, duration and
timestamp. That one request is the rollup. Jenkins already keeps those "last this, last
that" pointers on every job, so nothing has to be searched or computed upstream.

The second is that Jenkins's JSON already carries addresses. Every job and every build in
an answer comes with its own URL. That is half of hypermedia already: Jenkins hands out
links, it just doesn't hand out forms, and it doesn't say what you may do now.

The third is how starting works. You post to a job's "build" address, or to
"build with parameters" when the job takes parameters. Jenkins answers "201 Created" and
names a new resource in the Location header. But that new resource is not the build. It
is a queue item: a ticket that says "this will run when an executor is free". Later, the
queue item gains a link to the actual build once it starts. This matters for the design,
and it happens to fit HEART very well.

On authentication: Jenkins takes a user name and a personal API token, sent as Basic
authentication. Since Jenkins 2.96, requests authenticated with an API token don't need
the anti-forgery "crumb" that browsers need. So the gateway can forward each caller's own
token, exactly as it forwards each caller's GitHub token today. Passwords would need the
crumb dance, so the gateway takes tokens only, and says so.

What Jenkins lacks is an OpenAPI description. GitHub publishes one, and our gateway reads
it to know GitHub's operations. Jenkins publishes none. So we write a small one ourselves,
covering only the slice we use: perhaps a dozen operations. The gateway then works the
same way it does for GitHub, unchanged. Writing that description is the first real task.

A note on Blue Ocean. You mentioned it. Blue Ocean had its own REST API with a nicer model
of pipelines and runs, but Jenkins has marked Blue Ocean and its REST plugins deprecated,
with only security fixes, and the handbook dates the deprecation to July 2026. We should
not build on it. The core API, plus the Pipeline Stage View plugin's small API for stages,
covers what we need.

## The resources

Like GitHub, the gateway presents a handful of kinds of resource, each with the acts its
state allows and a reason for each act it doesn't.

The bookmark. The one address a script knows. It offers a search for jobs by name, a way
to open a job or folder by its full name, and a link to the top-level folder, which in
Jenkins is the server itself.

A folder. Its state is its name and description, and its members are links to each job and
each sub-folder inside it, titled by name. And here is the rollup: each member's link
comes with that member's last-run facts embedded. Last result, last success, last failure,
last duration, whether it is running now, and whether it is disabled. That way a script
reading a folder sees the whole rollup in one read, with no request per job, which on a
folder of two hundred jobs is the difference between one round trip and two hundred.

A job. Its state says what kind of job it is, whether it can be built, whether it is
disabled, whether something is waiting in the queue for it, and what parameters it takes,
each with its type, its choices if it is a choice parameter, and its default. Its links
go to its last build, last successful build, last failed build, its build history, and
its parent folder. Its acts are "build", and "disable" or "enable".

A build. Its state is its number, its result, whether it is still running, when it started,
how long it took, and Jenkins's own estimate of how long it should take. Its links go to
its job, the builds before and after it, its console log, and, for pipelines, its stages.
Its one act is "stop", offered only while it runs. Once it has finished, the reason given
is simply "The build has finished."

A queue item. The new resource that starting a job brings into being. Its state says why
it is waiting, in Jenkins's own words, like "waiting for next available executor". Its act
is "cancel". And once the build has started, it carries a link to the build.

## The acts, and their reasons

Start. Offered by a job when Jenkins says the job is buildable. When it isn't, the gateway
says why: "The job is disabled", or "A multibranch project is built through its branches."
If the job takes parameters, the form carries exactly those parameters, with their types,
defaults and choices, so a script can't send a parameter the job doesn't have, or a choice
it doesn't offer. This is new for the gateway: the form's fields come from the job's own
state, not from a fixed schema. It is the same idea as our "choices from state" plan, and
the two should share the mechanism.

Stop. Offered by a running build. Cancel. Offered by a queue item that hasn't started.
Disable and enable. Offered by a job, each only when it makes sense, and only to callers
Jenkins lets configure the job.

Starting a job is a domain act the job offers, never a "create build" call. The client
holds the queue item it brought into being. From there, a script waits with "until": read
the queue item again until it links to a build, follow that link, then read the build
until it is no longer running. "until" is already in the DSL. So "start and wait for the
result" is a few lines of script, with no polling loop written by hand and no URL built.

## Freshness and the stale problem

This is the one design question with real teeth. HEART acts carry a validator: the client
says "I am acting on the version of this resource I read". If the resource has changed
since, the act is refused as stale. That is exactly right for a pull request. But a busy
job's representation changes every time any build of it finishes, because its "last
build" changes. If the job resource held both its settings and its recent activity, then
starting a job would often be refused as stale simply because some other build finished a
second earlier. That would be correct by the letter and useless in practice.

The fix is to split by rate of change. The job resource holds only what the "start" act
depends on: whether it is buildable, whether it is disabled, its parameters. Its recent
activity, last build, last success and so on, lives in a separate linked resource: the
job's activity, or its history. Then the job's version changes only when its settings
change, and "start" is refused as stale only when that is genuinely meaningful, for
example when someone changed its parameters after you read them. The folder's rollup
reads the activity side. This split is the most important modelling decision in the plan.

Jenkins doesn't send entity tags on its JSON answers, as far as we know. The gateway
already handles that: it makes its own versions from the representation it serves, and it
reads upstream again just before acting.

## Folders, nesting, and names

Jenkins folders nest to any depth, and a job's address is a chain: job slash team, job
slash service, job slash deploy, and so on. Our gateway today expects addresses with a
fixed number of variables, like owner, repo and number. So the gateway needs one new
ability: a variable that spans several path segments, the job's full name. The URI
template standard already has a form for that, so this is a small, well-defined change.

Multibranch pipelines and organization folders are folders whose members Jenkins makes
for you, one per branch or per repository. They fit the folder model as they are. Their
one special act is "scan", which tells Jenkins to look for new branches. That comes in a
later phase.

## The rollup, and where it lives

You asked for a rollup at the folder level. There are two layers to it.

The gateway's layer is facts. The folder representation lists every member with its
last-run facts. That is honest data straight from Jenkins, read in one upstream request
with the tree parameter, and nothing invented.

The script's layer is judgement and summary. A heart-script reads the folder and computes
whatever you want: how many jobs are green, red, unstable, running or never built; which
failed most recently; the slowest last run; the oldest last success. It prints a table, or
writes a trace for a run report. Keeping the summary in a script keeps the gateway simple,
and lets each team summarise the way it wants. A recursive rollup across nested folders is
the same script walking into each sub-folder with "members".

If a summary turns out to be universally wanted, like counts by status, the gateway can
add it to the folder's state later as plain computed facts.

## The scripts

Three scripts come first, in the scripts folder beside the GitHub and garden ones.

"Start and wait". Takes the job's full name and its parameters as inputs. Opens the job
from the bookmark, starts it, waits on the queue item until the build exists, follows it,
waits until it finishes, and checks the result. Its exit code says it all: zero for
success, a test failure if the build failed, a refusal if Jenkins wouldn't start it, with
Jenkins's reason.

"Job status". Takes a job's name. Reads the job and its activity, and prints the last run,
last success, last failure and last duration, plus whether it is running now.

"Folder rollup". Takes a folder's name. Reads the folder once and prints one line per job
with its last result, last success, last failure and last duration, then the totals. With
an option, it walks sub-folders too.

Each run writes a trace, and the run report we built today renders it, script and all.

## How we build it, in phases

Phase one, read only. Write the small OpenAPI description of the Jenkins slice. Write the
connector: bookmark, folder, job, job activity, build. Add the multi-segment name to the
gateway. Build a small fake Jenkins for the tests, the way we built a small GitHub. Then
run it read-only against a real Jenkins, a scratch one in Docker, seeded with a few
folders and jobs that pass, fail, and run long.

Phase two, starting. The start act, with parameters from the job's state, the queue item as
the new resource, and cancel. Then "start and wait" end to end, live.

Phase three, the rest of the acts: stop, disable, enable. Then the rollup script and the
job status script, with run reports.

Phase four, the extras: pipeline stages from the Stage View plugin, the console log as a
linked resource read in pieces, multibranch scanning.

Every phase feature first, red then green, as always. Writes only ever to the scratch
Jenkins.

## Questions for you

Which Jenkins is the real target: open-source Jenkins, or CloudBees? And which version,
roughly? Which plugins does it have: Folders, Pipeline, Stage View, multibranch? How do
people sign in: local accounts with API tokens, or single sign-on? Single sign-on usually
still allows personal API tokens, but it is worth checking. How deep are your folders, and
how many jobs does the biggest one hold? That decides whether the rollup must page. And
the rollup itself: are last run, last success, last failure and last duration the whole
list, or do you also want things like the failure streak, or the build health score
Jenkins shows as weather icons?

That is the plan. The short version: Jenkins already hands out links, so the gateway adds
the forms, the reasons, and one honest split between a job's settings and its activity.
Starting a job brings a queue item into being, scripts wait on it with "until", and the
rollup is one upstream read, summarised by a script.
