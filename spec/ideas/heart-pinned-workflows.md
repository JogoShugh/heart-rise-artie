# Idea: workflows a provider publishes, pinned to its forms

**Status:** idea only. Raised by the author on 2026-10-29, after comparing HEART with Kin Lane's "We Did Not Skip
Hypermedia, We Moved It Out Of The Response" (API Evangelist, 2026-10-22). No code, no spec text.

## The gap

Lane's spec stack has one thing HEART lacks: a workflow the provider writes down ahead of time (Arazzo), so an agent
sees the whole path before its first call. A HEART client finds the way one response at a time. It gets the whole
path only after a run of its own: the run is recorded, and `artie generate` writes the kept script from it. That
workflow is the consumer's, made after the fact; no provider publishes one.

## The idea

A provider could publish a workflow as a HEART resource: the steps a common job takes, each named by the relation of
the form it uses, and each pinned to that form's integrity, as a kept script's lock file pins it. A client would then:

- read the whole path before acting, as Arazzo allows;
- check each step against the live form at the moment it acts, as a kept script does, so a workflow that drifted
  from the API is caught, not followed;
- still be refused by the server for any step the state doesn't allow, with the reason.

It would be a kept script written by the provider rather than learned by the client, and the form pins would keep
it from becoming "a second copy of your API that drifts", Lane's own warning.

## Questions

- Should the format be Arazzo itself, with a HEART profile adding relations and integrities, rather than a new one?
- Where does it live: linked from the service's entry point, or from each resource the job starts at?
- Can `artie generate` write one from a recorded run, for a provider to review and publish?
