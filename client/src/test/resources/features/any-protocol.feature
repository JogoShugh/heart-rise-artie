Feature: Identification is separate from interaction (SPEC §6.0)
  Fielding's first rule: "any protocol element that uses a URI for
  identification must allow any URI scheme to be used for the sake of that
  identification." ARTIE chooses how to reach a URI by its scheme, through
  the binding registered for it. HTTP is one binding. These scenarios add a
  second one, an in-memory protocol under the scheme "mem", which knows
  nothing of HTTP: no status codes, no headers. The same client syncs,
  acts and refuses through it, by the same rules.

  Background:
    Given the "mem" binding holds bed 1 EMPTY at "mem://garden/beds/1"

  Scenario: ARTIE syncs and acts through a binding that is not HTTP
    When the client enters "mem://garden/beds/1"
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the "mem" bed is PLANTED
    And the manifest offers exactly "water"
    And nothing went over HTTP

  Scenario: A stale validator is refused as stale in any binding
    Given the client enters "mem://garden/beds/1"
    And someone else sows the "mem" bed
    When the client tries to act on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the client raised a "StaleAct" refusal
    And the client holds the "mem" bed's current validator
    And nothing went over HTTP

  Scenario: A link to another scheme is followed through that scheme's binding
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      _links:
        self: {href: /}
        item:
          - {href: "mem://garden/beds/1", title: Bed one}
      """
    When the client enters "http://garden.test/"
    And the client follows the "item" link titled "Bed one"
    Then the client holds "mem://garden/beds/1"
    And the client's last request was GET "http://garden.test/"

  Scenario: A URI whose scheme has no binding is refused before anything is sent
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      _links:
        self: {href: /}
        item:
          - {href: "gopher://garden/beds/1", title: Bed one}
      """
    When the client enters "http://garden.test/"
    And the client tries to follow the "item" link titled "Bed one"
    Then the client raised an error mentioning "no binding for the scheme gopher"
    And the client sent no request for it
