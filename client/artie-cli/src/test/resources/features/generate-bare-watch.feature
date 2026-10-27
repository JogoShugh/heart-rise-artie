Feature: A watch run's capture made a bare watch: the kept script's words, over calls to fixed addresses
  spec/ideas/bare-runs.md, "Rails". A goal run that watched keeps its
  watch as the script its decisions amount to. `artie generate --bare`
  brings that script down to the service's own calls, learned from the
  gateway's upstream record under the run's trace: each act becomes the
  call it made, each member the read the gateway made of it, each address
  a template filled from the inputs, the list's item, the member read, or
  the resource the run kept by name. A member the gateway read again
  and found unchanged (304) is read from its last full answer. The
  words stay: watch until,
  whenever, has, leaveWhere, once with give up and otherwise. Facts the
  gateway read only to say why an act was absent are not read: where the
  kept script waits for a form, the bare one tries the call, and the
  service's refusal is the only gate. A reasoned Otherwise asks the
  reasoner to write the words it sends.

  Background:
    Given the file "watch.trace.yaml" is
      """
      ---
      run:
        inputs:
          owner:
            value: acme
            given: given
            question: Whose repository?
          repo:
            value: rockets
            given: default
            default: rockets
            question: Which repository?
          label:
            value: deps
            given: default
            default: deps
            question: Which label marks a bump?
          hold:
            value: hold
            given: default
            default: hold
            question: Which label means someone objects?
        source: |-
          act(intent = "get repository $owner/$repo")
          act(intent = "Open an issue titled \"Merge watch\"")
        marks:
          '2':
            as: watch
        watch:
          from: 3
          script: |-
            watch(until = { at("watch") { "state" eq "closed" } }) {
                act("github:pulls-list", "owner" to owner, "repo" to repo, "labels" to label) {
                    whenever {
                        has { "github:labels" has label }
                        leaveWhere("leave it, and say so once.") { "github:labels" has hold }
                        once("github:merge", giveUp = 3.minutes, otherwise = { why ->
                            act(intent = "tell its author why it wasn't merged. The server says why: ${why ?: "no reason given"}")
                        }) {
                            act("github:merge", "merge_method" to "squash") {}
                        }
                    }
                }
            }
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
      mode: intent
      say: Open an issue titled "Merge watch"
      line: 2
      params:
        relation: github:open-issue
      outcome: new resource
      exchanges:
        - request:
            method: POST
            url: https://localhost:8392/repos/acme/rockets/actions/open-issue
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
          response:
            status: 201
      ---
      mode: script
      say: act {relation}, owner {owner}, repo {repo}, labels {labels}
      params:
        relation: github:pulls-list
      outcome: done
      exchanges:
        - request:
            method: GET
            url: https://localhost:8392/repos/acme/rockets/pulls?labels=deps
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000003-01
          response:
            status: 200
      ---
      mode: script
      say: follow {relation}, titled {title}
      params:
        relation: item
      outcome: done
      exchanges:
        - request:
            method: GET
            url: https://localhost:8392/repos/acme/rockets/pulls/9
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000004-01
          response:
            status: 200
      ---
      mode: script
      say: merge it by squashing
      params:
        relation: github:merge
      outcome: done
      exchanges:
        - request:
            method: PUT
            url: https://localhost:8392/repos/acme/rockets/pulls/9/actions/merge
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000005-01
          response:
            status: 200
      ---
      mode: script
      say: follow {relation}, titled {title}
      params:
        relation: item
      outcome: done
      exchanges:
        - request:
            method: GET
            url: https://localhost:8392/repos/acme/rockets/pulls/8
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000006-01
          response:
            status: 200
      ---
      mode: intent
      say: 'tell its author why it wasn''t merged. The server says why: A draft can''t be merged.'
      params:
        relation: github:comment
      outcome: new resource
      exchanges:
        - request:
            method: POST
            url: https://localhost:8392/repos/acme/rockets/pulls/8/actions/comment
            headers:
              traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000007-01
          response:
            status: 201
      """
    And the file "upstream.yaml" is
      """
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/issues
        body:
          title: Merge watch
          body: Close this issue to stop the watch.
      response:
        status: 201
        body:
          number: 452
          url: https://api.github.com/repos/acme/rockets/issues/452
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000003-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets
      response:
        status: 200
        body:
          id: 1422841381
          permissions:
            push: true
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000003-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/issues?labels=deps
      response:
        status: 200
        body:
          - number: 9
            url: https://api.github.com/repos/acme/rockets/issues/9
          - number: 8
            url: https://api.github.com/repos/acme/rockets/issues/8
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000003-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/pulls/9
      response:
        status: 200
        body:
          number: 9
          labels:
            - name: deps
          head:
            sha: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000004-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/pulls/9
      response:
        status: 304
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000005-01
      request:
        method: PUT
        url: https://api.github.com/repos/acme/rockets/pulls/9/merge
        body:
          merge_method: squash
          sha: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
      response:
        status: 200
        body:
          merged: true
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000006-01
      request:
        method: GET
        url: https://api.github.com/repos/acme/rockets/pulls/8
      response:
        status: 200
        body:
          number: 8
          draft: true
          labels:
            - name: deps
          head:
            sha: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000007-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/issues/8/comments
        body:
          body: Hi @acme, it's still a draft; mark it ready for review and it can be merged.
      response:
        status: 201
        body:
          id: 77
      """

  Scenario: The watch's words over the service's own calls, each address a template
    When artie is run with
      | argument         |
      | generate         |
      | watch.trace.yaml |
      | --bare           |
      | upstream.yaml    |
      | --out            |
      | watch.bare.kts   |
    Then artie exits 0
    And artie prints
      | line                                                                         |
      | wrote watch.bare.kts: the watch's words over 6 calls to the service, at fixed addresses |
    And the file "watch.bare.kts" reads
      """
      // Made by artie generate --bare from watch.trace.yaml and upstream.yaml (spec/ideas/bare-runs.md, "Rails"):
      // the kept script's watch, its words kept, each act brought down to the call it made to the service, at a
      // fixed address. No entry, no forms, no validators, no reasons for absence: where the kept script waits for a
      // form, this tries the call, and the service's refusal is the only gate.
      import kotlin.time.Duration.Companion.minutes

      val owner by input(question = "Whose repository?")
      val repo by input(default = "rockets", question = "Which repository?")
      val label by input(default = "deps", question = "Which label marks a bump?")
      val hold by input(default = "hold", question = "Which label means someone objects?")

      bare {
          val watch = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", mapOf("title" to "Merge watch", "body" to "Close this issue to stop the watch."))
          watch(until = { call("GET", "https://api.github.com/repos/${owner}/${repo}/issues/${watch["number"].text}")["state"].text == "closed" }) {
              whenever(call("GET", "https://api.github.com/repos/${owner}/${repo}/issues?labels=${label}")) { item ->
                  val member = call("GET", "https://api.github.com/repos/${owner}/${repo}/pulls/${item["number"].text}")
                  has(member["labels"] has label)
                  leaveWhere("leave it, and say so once.", member["labels"] has hold)
                  once("PUT", "https://api.github.com/repos/${owner}/${repo}/pulls/${member["number"].text}/merge", mapOf("merge_method" to "squash", "sha" to member["head"]["sha"].text), giveUp = 3.minutes, otherwise = { why ->
                      call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${member["number"].text}/comments", mapOf("body" to write("tell its author why it wasn't merged. The server says why: ${why ?: "no reason given"}")))
                  })
              }
          }
      }
      """

  Scenario: An act the run never made is said so, not guessed
    Given the file "upstream.yaml" is
      """
      ---
      traceparent: 00-0189d6a9b7a605907ec3a4d57bc079e5-0000000000000002-01
      request:
        method: POST
        url: https://api.github.com/repos/acme/rockets/issues
        body:
          title: Merge watch
      response:
        status: 201
        body:
          number: 452
          url: https://api.github.com/repos/acme/rockets/issues/452
      """
    When artie is run with
      | argument         |
      | generate         |
      | watch.trace.yaml |
      | --bare           |
      | upstream.yaml    |
      | --out            |
      | watch.bare.kts   |
    Then artie exits 2
    And artie's last line is: can't bring the watch down to calls: the run made no call for "github:pulls-list"
