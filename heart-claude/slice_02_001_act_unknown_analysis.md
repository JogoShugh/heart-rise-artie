# Slice 2.1 — Act: rejection of unknown affordances

## What this tests

After enter, the manifest contains known affordances. The client attempts to act on a rel that was never in the manifest. The expectation is an error raised before the transport is ever called. This is not a server rejection — no HTTP, no 404. The client enforces its own contract: you may only act on what the server gave you.

This is one of the core H.E.A.R.T. structural guarantees made testable.

## Feature shape

Background only needs the enter response. No act response is enqueued — the transport must never be reached. One scenario is sufficient; testing multiple unknown rel names adds no value since the behavior is identical regardless of which unknown rel is tried.

```gherkin
Feature: Act — rejection of unknown affordances

  Background:
    Given the service returns the following on enter:
      """yaml
      status: initial
      _forms:
        transition:
          href: https://example.com/resource
          method: POST
          hash: abc123
          _schema:
            type: object
            properties:
              value:
                type: string
            required: [value]
      """
    And the Content-Type declares the H.E.A.R.T. dual profile
    When the client enters the service

  Scenario: Acting on an affordance absent from the manifest raises an error
    When the client attempts to act on "nonexistent" with:
      """yaml
      value: hello
      """
    Then the client raises an error
```

## New step patterns

- `When the client attempts to act on {string} with:` — distinct from the existing `acts on` step. Wraps client.act() in a try/catch, stores the caught throwable on ClientContext. Does not rethrow.
- `Then the client raises an error` — asserts the stored throwable is non-null.

## Changes required

ClientContext: add `var lastError: Throwable? = null`

ActSteps: add the two new step definitions above. No new file needed — same territory as existing act steps.

## What is already in place

ArtieClient.act() already does:

    val form = current?.affordance(rel) ?: error("No affordance '$rel' in current manifest")

The behavior exists. This feature locks it in as a specified contract, not an implementation accident.
