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
