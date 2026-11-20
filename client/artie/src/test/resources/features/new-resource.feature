Feature: An act that brings a resource into being: ARTIE holds the new resource
  SPEC §6.0, "New resource". The server names the new resource in
  Location. The body is a representation of whatever Content-Location
  names: the new resource itself, or the resource acted on. ARTIE holds the
  new resource next, and reads it only when the body is not it.

  Background:
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "index-1"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      title: Garden
      _forms:
        buildBed:
          _links:
            target: {href: /actions/build-bed}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              label: {type: string}
      """
    And the service answers GET "http://garden.test/beds/7" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 7
      label: Herbs
      status: EMPTY
      _links:
        self: {href: /beds/7}
      """

  Scenario Outline: The answer is <answer>: ARTIE holds bed 7, reading it <reads>
    Given the service answers POST "http://garden.test/actions/build-bed" with:
      """
      201 Created
      Content-Type: application/hal+json
      ETag: "<etag>"
      Location: /beds/7
      Content-Location: <content location>
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      <body>
      """
    When the client enters "http://garden.test/"
    And the client acts on "buildBed" with:
      """yaml
      label: Herbs
      """
    Then the resource state contains:
      | key   | value |
      | id    | 7     |
      | label | Herbs |
    And the client sent <requests> requests

    Examples:
      | answer                      | content location | etag         | body                                 | reads            | requests |
      | the new bed itself          | /beds/7          | seq-0.hsf.L0 | {id: 7, label: Herbs, status: EMPTY} | not at all       | 2        |
      | the index, the one acted on | /                | index-2      | {title: Garden}                      | once, at /beds/7 | 3        |
