Feature: A run's capture made a bare script: the calls the gateway made, sent straight to the service
  spec/ideas/bare-runs.md. A run recorded with its exchanges names its
  trace in each request's traceparent; the gateway's upstream record
  (GATEWAY_RECORD) keeps each call it made to the service under the
  same traceparent. `artie generate --bare` reads both and writes the
  run as the calls it came down to: every write the service took, in
  order, and only the reads whose answers a later call sends a value
  from. Each value is an input, a value read from an earlier answer by
  pointer, or as recorded; a call another run made is no part of it.
  Where the run waited (an until step), the next write is tried again
  until the service takes it.

  Background:
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs:
          owner:
            value: acme
            given: given
            question: Whose repository?
          stamp:
            value: '0101'
            given: given
      ---
      mode: script
      say: enter {entry}
      params:
        entry: https://localhost:8392/
      outcome: done
      exchanges:
        - request:
            method: GET
            url: https://localhost:8392/
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000001-01
          response:
            status: 200
      ---
      mode: script
      say: act {relation}, title {title}
      params:
        relation: open-issue
        title: Crash (0101)
      outcome: new resource
      exchanges:
        - request:
            method: POST
            url: https://localhost:8392/repos/acme/rockets/issues/actions/open-issue
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
          response:
            status: 201
      ---
      mode: script
      say: until
      params: {}
      outcome: held
      exchanges:
        - request:
            method: GET
            url: https://localhost:8392/repos/acme/rockets/pulls/9
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000003-01
          response:
            status: 200
      ---
      mode: script
      say: act {relation}
      params:
        relation: merge
      outcome: done
      exchanges:
        - request:
            method: PUT
            url: https://localhost:8392/repos/acme/rockets/pulls/9/actions/merge
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000004-01
          response:
            status: 200
      """
    And the file "upstream.yaml" is
      """
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000001-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets
      response:
        status: 200
        body:
          id: 1422841381
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/issues
        body:
          title: Crash (0101)
      response:
        status: 201
        body:
          number: 452
          id: 5987690660
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/branches/main
      response:
        status: 200
        body:
          name: main
          commit:
            sha: d49b2b6ba4eda7477ae13e6ab152b065a1c3e9f0
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/issues/452
      response:
        status: 200
        body:
          number: 452
          state: open
      ---
      traceparent: 00-77777777777777777777777777777777-0000000000000009-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/issues
        body:
          title: Another run's issue
      response:
        status: 201
        body:
          number: 453
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/git/refs
        body:
          ref: refs/heads/fix-0101
          sha: d49b2b6ba4eda7477ae13e6ab152b065a1c3e9f0
      response:
        status: 201
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000004-01
      request:
        method: PUT
        url: https://api.github.com/repos/acme/rockets/pulls/9/merge
        body:
          commit_title: 'Fixes #452'
          duplicate_of: 5987690660
      response:
        status: 200
        body:
          merged: true
      """

  Scenario: The run's writes, and the one read a write needs, sent straight to GitHub
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --bare         |
      | upstream.yaml  |
      | --out          |
      | bare.kts       |
    Then artie exits 0
    And artie prints
      | line                                                                   |
      | wrote bare.kts: 4 calls of the run's 6 to the service, 3 writes and 1 read |
    And the file "bare.kts" reads
      """
      // Made by artie generate --bare from run.trace.yaml and upstream.yaml (spec/ideas/bare-runs.md): the calls the
      // run came down to, sent straight to the service, with no entry, no forms, no validators and no checks.
      val owner by input(question = "Whose repository?")
      val stamp by input()

      bare {
          val r2 = call("POST", "https://api.github.com/repos/${owner}/rockets/issues", mapOf("title" to "Crash (${stamp})"))
          val r3 = call("GET", "https://api.github.com/repos/${owner}/rockets/branches/main")
          call("POST", "https://api.github.com/repos/${owner}/rockets/git/refs", mapOf("ref" to "refs/heads/fix-${stamp}", "sha" to r3["commit"]["sha"].text))
          call("PUT", "https://api.github.com/repos/${owner}/rockets/pulls/9/merge", mapOf("commit_title" to "Fixes #${r2["number"].text}", "duplicate_of" to r2["id"]), retry = 30)
      }
      """

  Scenario: A capture with no exchanges names no trace to find the service's calls by
    Given the file "plain.trace.yaml" is
      """
      ---
      run:
        inputs: {}
      ---
      mode: script
      say: enter {entry}
      params:
        entry: https://localhost:8392/
      outcome: done
      """
    When artie is run with
      | argument         |
      | generate         |
      | plain.trace.yaml |
      | --bare           |
      | upstream.yaml    |
      | --out            |
      | bare.kts         |
    Then artie exits 2
    And artie's last line is: can't generate a bare script from plain.trace.yaml: it keeps no exchanges; record the run with --exchanges
