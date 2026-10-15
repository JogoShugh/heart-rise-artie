Feature: Plain JSON: the same state with every control stripped
  The OpenAPI twin (spec/ideas/openapi-vs-heart-demo.md). A client that
  asks for application/json gets what a typical JSON data API returns: the
  resource's state and nothing else. No links, no forms, no profile and no
  validator to send back. Acts are the same POSTs at the same addresses and
  need no If-Match. The server still enforces the same rules, so an act the
  state does not allow is refused after it is sent (409), where HEART never
  offers it.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario: A plain read is the state alone
    When a client gets "/lamps/7" as plain-json
    Then the response status is 200
    And the response has Content-Type "application/json"
    And the response body is exactly {"id":7,"status":"ON","brightness":50}
    And the response's only Link is the forms link to an affordance set
    And the response carries no ETag header

  Scenario: A plain answer links the affordance set of the state it shows
    The body stays the plain state. A standing link in the Link header names
    the content-addressed affordance set of the state shown, the same set
    HEART's L1 representation links to. A client that ignores Link headers
    sees nothing new; one that follows it learns which acts are allowed now,
    and can drop the operations of the OpenAPI description that are not.
    When a client gets "/lamps/7" as plain-json
    Then the plain response's forms link names an affordance set that offers "home:switch-off, home:dim"
    And that affordance set's bytes match the link's integrity

  Scenario: After an act, the plain answer links the new state's affordance set
    When a client posts {} to "/lamps/7/actions/switch-off" as plain-json with If-Match ""
    Then the response status is 200
    And the plain response's forms link names an affordance set that offers "home:switch-on"

  Scenario: An act needs no If-Match in plain JSON, and answers with the plain state
    When a client posts {"brightness": 20} to "/lamps/7/actions/dim" as plain-json with If-Match ""
    Then the response status is 200
    And the response has Content-Type "application/json"
    And the response body is exactly {"id":7,"status":"ON","brightness":20}
    And lamp 7 has brightness 20 at version 4

  Scenario: An act the state does not allow is refused after it is sent
    When a client posts {} to "/lamps/7/actions/switch-on" as plain-json with If-Match ""
    Then the response status is 409
    And the response is a problem of type "not-offered"
    And lamp 7 is still at version 3

  Scenario: A body that breaks the act's schema is refused
    When a client posts {"brightness": 500} to "/lamps/7/actions/dim" as plain-json with If-Match ""
    Then the response status is 422
    And the response is a problem of type "invalid-input"

  Scenario: HEART still needs If-Match
    When a client posts {"brightness": 20} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match ""
    Then the response status is 428

  Scenario: A plain search lists each match by id and title
    Given lamp 2 is "OFF" at version 1 in the lamp server
    When a client gets "/lamps?name=lamp" as plain-json
    Then the response status is 200
    And the response has Content-Type "application/json"
    And the response body is exactly {"count":2,"items":[{"id":2,"title":"Lamp 2"},{"id":7,"title":"Lamp 7"}]}

  Scenario: A bookmark act needs no If-Match in plain JSON
    When a client posts {"brightness": 30} to "/actions/install-lamp" as plain-json with If-Match ""
    Then the response status is 201
    And the response has Location "/lamps/8"
    And the response body is exactly {"id":8,"status":"OFF","brightness":30}

  Scenario: Ranked below a dialect, application/json does not win
    When a client gets "/lamps/7" with Accept "application/hal+json, application/json;q=0.5"
    Then the response has Content-Type "application/hal+json"
