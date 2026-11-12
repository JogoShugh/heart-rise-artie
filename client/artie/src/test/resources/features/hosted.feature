Feature: ARTIE hosted in a service
  A service makes its calls to other parties through ARTIE, run in its own process. ARTIE never serves:
  the host gives it the credentials it calls with and the key it signs with, runs a kept script, and
  gets the run back. Each act ARTIE sends for a host is signed with the host's key (RFC 9421 over
  RFC 9530's Content-Digest), so the party it calls can tell which service asked. Several hosts can
  run ARTIE in one process at once, each run its own.

  Background:
    Given the service answers GET "http://localhost:8301/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: /beds/2/actions/water-cell}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              milliliters: {type: integer}
              _links: {type: object}
      """
    And the service answers POST "http://localhost:8301/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: WATERED
      _links:
        self: {href: /beds/2}
      _forms: {}
      """

  Scenario: A host runs a kept script with ARTIE and gets the run back
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - act: waterCell
          fields: {milliliters: 100}
      """
    Then the host "desk" has its run back, ended, with steps:
      | step          | outcome |
      | enter         | done    |
      | act waterCell | done    |

  Scenario: ARTIE calls with the credentials the host gives
    Given the host "desk" gives ARTIE the token "desk-token" for "http://localhost:8301"
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - act: waterCell
          fields: {milliliters: 100}
      """
    Then every request on the wire had header "Authorization" set to "Bearer desk-token"

  Scenario Outline: A host may let ARTIE send its credentials over plain HTTP to addresses in a mask
    ARTIE never sends a credential over plain HTTP beyond the machine, unless its host names the
    addresses it may: a network written as a mask (CIDR), such as a home network. Only an address
    written as numbers is in a mask; a name never is, whatever it resolves to.
    Given the service answers GET "<origin>/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      """
    And the host "desk" gives ARTIE the token "desk-token" for "<origin>"
    And the host "desk" lets ARTIE send credentials over plain HTTP to "192.168.0.0/16"
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: <origin>/beds/2
      steps: []
      """
    Then what reached the wire for "<origin>/beds/2" was "<sent>"

    Examples:
      | origin                   | sent                         |
      | http://192.168.1.18:8301 | GET, with Bearer desk-token  |
      | http://10.0.0.5:8301     | nothing                      |
      | http://garden.local:8301 | nothing                      |

  Scenario: A hosted run keeps each step's exchanges for its report, credentials never in them
    Given the host "desk" gives ARTIE the token "desk-token" for "http://localhost:8301"
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - act: waterCell
          fields: {milliliters: 100}
      """
    Then the run of the host "desk" kept these exchanges:
      | step          | method | url                                             | status | Authorization |
      | enter         | GET    | http://localhost:8301/beds/2                    | 200    | <token>       |
      | act waterCell | POST   | http://localhost:8301/beds/2/actions/water-cell | 200    | <token>       |

  Scenario: Each act ARTIE sends for a host is signed with the host's key
    Given the host "desk" gives ARTIE its signing key "desk-2026-11"
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - act: waterCell
          fields: {milliliters: 100}
      """
    Then the requests on the wire were signed:
      | method | url                                          | signed by    |
      | GET    | http://localhost:8301/beds/2                    |              |
      | POST   | http://localhost:8301/beds/2/actions/water-cell | desk-2026-11 |
    And the signature on the POST covers, in order:
      | component      |
      | @method        |
      | @target-uri    |
      | content-digest |
      | if-match       |

  Scenario: ARTIE asks with a reply its host keeps, the envelope in the act's body
    Given the host "desk" keeps replies, opening "http://localhost:8160/replies/r1" with its keys at "http://localhost:8160/keys"
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - ask: waterCell
          fields: {milliliters: 100}
          reply: watering
      """
    Then the host "desk" has its run back, ended
    And the POST to "http://localhost:8301/beds/2/actions/water-cell" carried the body:
      """json
      {"milliliters":100,"_links":{"hr:reply-to":{"href":"http://localhost:8160/replies/r1"},"hr:keys":{"href":"http://localhost:8160/keys"}}}
      """
    And the host "desk" was told the reply "http://localhost:8160/replies/r1" was asked at "http://localhost:8301/beds/2"

  Scenario: ARTIE runs the kept script a link hands over, with inputs
    Given the service answers GET "http://localhost:8301/garden" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "garden-1"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      name: The garden
      _links:
        self: {href: /garden}
        care:script: {href: /garden/care.heart.yaml}
      """
    And the service answers GET "http://localhost:8301/garden/care.heart.yaml" with:
      """
      200 OK
      Content-Type: application/yaml

      heart: 1
      kind: kept
      inputs: {bed: {}}
      start: ${bed}
      steps:
        - scenario: planted
          steps:
            - check: [{field: status, is: PLANTED}]
      """
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/garden
      steps:
        - run-script: care:script
          inputs: {bed: "http://localhost:8301/beds/2"}
          as: care
        - note: "care passed: ${care.passed}"
      """
    Then the host "desk" has its run back, ended
    And the run of the host "desk" noted "care passed: true"

  Scenario: An act as of a version read before is refused as stale, and ARTIE sends it once
    ARTIE acts again after a 412 when the form is still offered once the server has caught up. An
    act as of a version kept by name is meant to meet the 412: ARTIE sends it as written, once.
    Given the service answers POST "http://localhost:8301/beds/2/actions/water-cell" with:
      """
      412 Precondition Failed
      Content-Type: application/problem+json

      type: https://w3id.org/heart-rise/problems/stale
      title: The resource changed since it was read
      status: 412
      """
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - keep-as: before
        - act: waterCell
          fields: {milliliters: 100}
        - sync: true
        - act: waterCell
          fields: {milliliters: 100}
          as-of: before
          refused-as: stale
      """
    Then the host "desk" has its run back, ended
    And the acts on the wire carried:
      | method | url                                                | If-Match       |
      | POST   | http://localhost:8301/beds/2/actions/water-cell    | "seq-1.hsf.L0" |
      | POST   | http://localhost:8301/beds/2/actions/water-cell    | "seq-1.hsf.L0" |

  Scenario: A host may give ARTIE a reasoner, for the one run a goal is captured from
    Given the host "desk" gives ARTIE a reasoner that will choose "waterCell" with {"milliliters": 100}
    When the host "desk" runs this kept script with ARTIE:
      """yaml
      start: http://localhost:8301/beds/2
      steps:
        - intent: water the bed
      """
    Then the host "desk" has its run back, ended
    And the acts on the wire carried:
      | method | url                                             | If-Match       |
      | POST   | http://localhost:8301/beds/2/actions/water-cell | "seq-1.hsf.L0" |

  Scenario: Two hosts in one process each keep their own run
    Given the host "watch" reads its entry only once the host "desk" has its run back
    When the hosts run, at the same time, each with ARTIE:
      | host  | script                                                                     |
      | watch | {start: "http://localhost:8301/beds/2", steps: [{note: "watch was here"}]}     |
      | desk  | {start: "http://localhost:8301/beds/2", steps: [{act: waterCell, fields: {}}]} |
    Then each host has its run back, ended, with only its own:
      | host  | steps | notes          |
      | watch | 1     | watch was here |
      | desk  | 2     |                |
