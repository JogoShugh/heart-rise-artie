Feature: ARTIE sends a credential only to its own origin, and never shows it
  A credential (Basic or Bearer) is added to a request just above the wire,
  below the client's cache, and only when the request's origin (scheme,
  host and port) is the one the credential is for. A representation chooses
  where its links lead, so a link to another origin gets nothing. Nothing a
  model is shown, nothing in the event log and no error message carries the
  credential: the Reason step has no field for a header, and a credential
  prints as its scheme alone. Each fixture names its profile, as every
  HEART representation does.

  Scenario Outline: A credential goes to its own origin: <case>
    Given ARTIE holds the <scheme> credential "<value>" for "<origin>"
    And the service answers GET "<url>" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      """
    When the client enters "<url>"
    Then the request to "<url>" carried Authorization "<sent>"

    Examples:
      | case                      | scheme | value        | origin                  | url                          | sent                     |
      | Bearer                    | Bearer | t0k3n        | https://garden.test     | https://garden.test/beds/2   | Bearer t0k3n             |
      | Basic                     | Basic  | coach:s3cret | https://garden.test     | https://garden.test/beds/2   | Basic Y29hY2g6czNjcmV0   |
      | default port written out  | Bearer | t0k3n        | https://GARDEN.test:443 | https://garden.test/beds/2   | Bearer t0k3n             |
      | plain HTTP on loopback    | Bearer | t0k3n        | http://localhost:8080   | http://localhost:8080/beds/2 | Bearer t0k3n             |
      | plain HTTP to 127.0.0.1   | Bearer | t0k3n        | http://127.0.0.1:8080   | http://127.0.0.1:8080/beds/2 | Bearer t0k3n             |
      | plain HTTP to Docker's host | Bearer | t0k3n      | http://host.docker.internal:8080 | http://host.docker.internal:8080/beds/2 | Bearer t0k3n |

  Scenario Outline: A credential does not go to another origin: <case>
    Given ARTIE holds the Bearer credential "t0k3n" for "https://garden.test"
    And the service answers GET "<url>" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      """
    When the client enters "<url>"
    Then the request to "<url>" carried no Authorization

    Examples:
      | case           | url                             |
      | another port   | https://garden.test:8443/beds/2 |
      | another scheme | http://garden.test/beds/2       |
      | another host   | https://evil.test/beds/2        |

  Scenario: A link to another origin is followed without the credential
    Given ARTIE holds the Bearer credential "t0k3n" for "https://garden.test"
    And the service answers GET "https://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        item: {href: "https://evil.test/collect", title: Elsewhere}
      """
    And the service answers GET "https://evil.test/collect" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 9
      """
    When the client enters "https://garden.test/beds/2"
    And the client follows the "item" link titled "Elsewhere"
    Then the request to "https://garden.test/beds/2" carried Authorization "Bearer t0k3n"
    And the request to "https://evil.test/collect" carried no Authorization

  Scenario: A request that already carries Authorization keeps it
    Given ARTIE holds the Bearer credential "t0k3n" for "https://garden.test"
    And the service answers GET "https://garden.test/beds/2" with:
      """
      200 OK

      """
    When a GET "https://garden.test/beds/2" with Authorization "Bearer other" goes through ARTIE's credentials
    Then the last request had header "Authorization" set to "Bearer other"

  Scenario: A credential is never sent over plain HTTP beyond the machine
    Given ARTIE holds the Bearer credential "t0k3n" for "http://garden.test"
    When the client tries to enter "http://garden.test/beds/2"
    Then the client raised an error mentioning "will not send a credential over plain HTTP to http://garden.test:80"
    And the client sent no request

  Scenario: An address carrying a user name and password is refused before any request
    When the client tries to enter "https://coach:s3cret@garden.test/beds/2"
    Then the client raised an error mentioning "carries a user name or password in its address"
    And the error does not contain "s3cret"
    And the client sent no request

  Scenario Outline: A credential prints as its scheme alone
    Then the <scheme> credential "<value>" prints as "<printed>"
    And a request carrying it prints with "Authorization=****" and without "<value>"

    Examples:
      | scheme | value        | printed      |
      | Bearer | t0k3n        | Bearer(****) |
      | Basic  | coach:s3cret | Basic(****)  |

  Scenario Outline: The service refuses, and ARTIE says why without the credential: <case>
    Given ARTIE holds <credential>
    And the service answers GET "https://garden.test/beds/2" with:
      """
      <status>
      Content-Type: application/problem+json
      WWW-Authenticate: <challenge>

      type: https://garden.test/problems/not-authenticated
      title: Credentials are needed
      status: <code>
      """
    When the client tries to enter "https://garden.test/beds/2"
    Then the client raised a "<refusal>" refusal
    And the client raised an error mentioning "<says>"
    And the error does not contain "t0k3n"
    And the client sent 1 request

    Examples:
      | case                        | credential                                          | status           | code | challenge                                | refusal          | says                                                                     |
      | no credential configured    | no credentials                                      | 401 Unauthorized | 401  | Basic realm="garden", Bearer realm="garden" | NotAuthenticated | https://garden.test:443 asks for a credential (Basic, Bearer); ARTIE sent none |
      | credential refused          | the Bearer credential "t0k3n" for "https://garden.test" | 401 Unauthorized | 401  | Bearer realm="garden"                    | NotAuthenticated | https://garden.test:443 refused the Bearer credential ARTIE sent         |
      | credential not allowed this | the Bearer credential "t0k3n" for "https://garden.test" | 403 Forbidden    | 403  | Bearer realm="garden"                    | Forbidden        | https://garden.test:443 does not allow this with the Bearer credential ARTIE sent |

  Scenario: A renewed credential is sent once after a 401, and only once
    Given ARTIE holds the Bearer credential "old" for "https://garden.test", renewed as "new"
    And the service answers GET "https://garden.test/beds/2" with:
      """
      401 Unauthorized
      WWW-Authenticate: Bearer realm="garden"

      """
    And the service answers GET "https://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      """
    When the client enters "https://garden.test/beds/2"
    Then the client sent 2 requests
    And the requests carried Authorization "Bearer old", then "Bearer new"

  Scenario: An act refused with 401 is a NotAuthenticated refusal, not an unexpected answer
    Given ARTIE holds the Bearer credential "t0k3n" for "https://garden.test"
    And the service answers GET "https://garden.test/beds/2" with:
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
              milliliters: {type: integer, minimum: 1, maximum: 1000}
            required: [milliliters]
      """
    And the service answers POST "https://garden.test/beds/2/actions/water-cell" with:
      """
      401 Unauthorized
      WWW-Authenticate: Bearer realm="garden", error="invalid_token"

      """
    When the client enters "https://garden.test/beds/2"
    And the client tries to act on "waterCell" with:
      """yaml
      milliliters: 250
      """
    Then the client raised a "NotAuthenticated" refusal
    And the error does not contain "t0k3n"

  Scenario: The secret is in no request but its own, no event and nothing the model is shown
    Given ARTIE holds the Bearer credential "S3CRET-CANARY" for "https://garden.test"
    And the client keeps an event log
    And the reasoner is Claude, through the scripted Claude API
    And the service answers GET "https://garden.test/beds/2" with:
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
              milliliters: {type: integer, minimum: 1, maximum: 1000}
            required: [milliliters]
      """
    And the Claude API will answer with a tool call selecting "waterCell" with {"milliliters": 250}
    And the service answers POST "https://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _forms: {}
      """
    When the agent handles "water bed 2 with 250 ml" at "https://garden.test/beds/2"
    Then the agent acted
    And "S3CRET-CANARY" appears on the wire only in the Authorization header of requests to "https://garden.test"
    And "S3CRET-CANARY" appears in no Claude request
    And "S3CRET-CANARY" appears in no event
