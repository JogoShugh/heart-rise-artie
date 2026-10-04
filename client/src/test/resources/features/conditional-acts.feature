Feature: Conditional acts and the four refusals
  SPEC §6.3, §6.8, §6.9.1 and the [0.5] rules. Every act carries the ETag
  the client holds in If-Match, unchanged. A templated target is expanded
  from Layer 1 by one rule only: {name} takes the top-level property of
  that name. Each refusal has its own handling:
    412  the world moved: re-sync and drop the act, never resend it
    409  not legal here: re-sync and report; no retry
    428  the server wants a precondition: re-sync, then resend once with it
    422  the body is wrong: report the server's reasons; no retry

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{id}/actions/water-cell", templated: true}
          method: POST
          contentType: application/json
          schema: {type: object}
      """
    And the client enters "http://garden.test/beds/2"

  Scenario: The act carries the held ETag and goes to the expanded template
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    When the client acts on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client sent POST "http://garden.test/beds/2/actions/water-cell" with headers:
      | If-Match     | "seq-1.hsf.L0"       |
      | Content-Type | application/json     |
      | Accept       | application/hal+json |
    And the client holds the ETag "seq-2.hsf.L0"

  Scenario: A template variable with no matching Layer 1 property is refused before any request
    Given the service answers GET "http://garden.test/beds/7" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 7
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{bedId}/actions/water-cell", templated: true}
          method: POST
          schema: {type: object}
      """
    And the client enters "http://garden.test/beds/7"
    When the client tries to act on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client raised an error mentioning "{bedId}"
    And the client sent no request after entering

  Scenario: 412 means the world moved: re-sync and drop the act
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      412 Precondition Failed
      Content-Type: application/json

      error: stale
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-5.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: READY_TO_HARVEST
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    When the client tries to act on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client raised a "StaleAct" refusal
    And the client sent these requests after entering:
      | POST http://garden.test/beds/2/actions/water-cell |
      | GET http://garden.test/beds/2                     |
    And the client holds the ETag "seq-5.hsf.L0"
    And the client's current manifest is empty

  Scenario: 409 means not legal here: re-sync and report, no retry
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      409 Conflict
      Content-Type: application/json

      error: not in the current affordance set
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    When the client tries to act on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client raised a "IllegalTransition" refusal
    And the client sent these requests after entering:
      | POST http://garden.test/beds/2/actions/water-cell |
      | GET http://garden.test/beds/2                     |

  Scenario: 428 means a precondition is required: re-sync, then resend once with the fresh ETag
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      428 Precondition Required
      Content-Type: application/json

      error: If-Match required
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{id}/actions/water-cell", templated: true}
          method: POST
          schema: {type: object}
      """
    And the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _forms: {}
      """
    When the client acts on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client sent these requests after entering:
      | POST http://garden.test/beds/2/actions/water-cell |
      | GET http://garden.test/beds/2                     |
      | POST http://garden.test/beds/2/actions/water-cell |
    And the client holds the ETag "seq-2.hsf.L0"

  Scenario: 422 means the body is wrong: report the reasons, no re-sync, no retry
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      422 Unprocessable Content
      Content-Type: application/json

      error: request body does not satisfy the affordance schema
      violations: ["milliliters: must be greater than or equal to 1"]
      """
    When the client tries to act on "waterCell" with:
      """yaml
      milliliters: 0
      """
    Then the client raised a "InvalidPayload" refusal
    And the client raised an error mentioning "milliliters: must be greater than or equal to 1"
    And the client sent these requests after entering:
      | POST http://garden.test/beds/2/actions/water-cell |
