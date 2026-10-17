Feature: ARTIE names itself in User-Agent
  RFC 9110 §10.1.5. A server, or a filter in front of it, tells clients
  apart by User-Agent. Each ARTIE client sends its own name, so two clients
  in one run do not look like one. A User-Agent already on a request is
  left alone.

  Background:
    Given the service answers GET "http://garden.test/beds/1" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      status: EMPTY
      _links:
        self: {href: /beds/1}
      """
    And the service answers GET "http://garden.test/beds/1" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      status: EMPTY
      _links:
        self: {href: /beds/1}
      """

  Scenario: Every request ARTIE sends names ARTIE in User-Agent
    When the client enters "http://garden.test/beds/1"
    Then every request on the wire had a User-Agent starting with "ARTIE/"

  Scenario: A second client names itself differently from the first
    When the client enters "http://garden.test/beds/1"
    And a second client enters "http://garden.test/beds/1"
    Then every request on the wire had a User-Agent starting with "ARTIE/"
    And the two requests on the wire had different User-Agent values

  Scenario: A request that already carries a User-Agent keeps it
    When a GET "http://garden.test/beds/1" with User-Agent "curl/8.7" goes through ARTIE's naming
    Then the last request had header "User-Agent" set to "curl/8.7"
