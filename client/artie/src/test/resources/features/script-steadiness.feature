Feature: A script keeps going where the service moved under it: a list paged on, an act decided again
  spec/PLAN-NATIVE-VS-HEART-TRIAL.md, "the runs". A script made from a run
  replays its selections; the service it runs against keeps moving. Two
  things that stopped a generated script live, and what ARTIE does now:
  a titled link the run followed on a list's first page is on a later
  page once the list grew, so ARTIE follows the list's next links to find
  it, building no address; and an act refused 412, the resource changed
  since it was read (GitHub works out a new pull request's mergeability a
  moment after it opens), is decided again: ARTIE has read it again, and
  when the form is still offered and the lock file still holds, the
  script's act is sent once more, with the new ETag. A second 412 stops
  the script, as does a form no longer offered.

  Scenario: A titled link not on the page held is found on the pages after it, by their next links
    Given the service answers GET "http://garden.test/beds" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      count: 3
      _links:
        self: {href: /beds}
        next: {href: "/beds?page=2"}
        item:
          - {href: /beds/1, title: Bed one}
      """
    And the service answers GET "http://garden.test/beds?page=2" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      count: 3
      _links:
        self: {href: "/beds?page=2"}
        item:
          - {href: /beds/9, title: Bed nine}
      """
    And the service answers GET "http://garden.test/beds/9" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 9
      _links:
        self: {href: /beds/9}
      """
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/beds") {
          follow("item", "Bed nine")
      }
      """
    Then the script finished
    And the client holds "http://garden.test/beds/9"

  Scenario Outline: An act refused 412 is decided again once, on the resource read again: <case>
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms:
        water:
          _links:
            target: {href: /beds/2/actions/water}
          method: POST
          contentType: application/json
          schema: {type: object, properties: {milliliters: {type: integer}}}
      """
    And the service answers POST "http://garden.test/beds/2/actions/water" with:
      """
      412 Precondition Failed
      Content-Type: application/json

      error: stale
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms:
        <forms>
      """
    And the service answers POST "http://garden.test/beds/2/actions/water" with:
      """
      <second>
      Content-Type: application/hal+json
      ETag: "seq-3.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-4.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    And ARTIE runs with the guard "<guard>"
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/beds/2") {
          act("water", "milliliters" to 100)
      }
      """
    Then <ended>

    Examples:
      | case                                   | guard         | forms                                                                                                 | second                  | ended                                                                                                                  |
      | the form still offered: sent again     | lenient       | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 200 OK                  | the script finished                                                                                                    |
      | no longer offered: the script stops    | lenient       | {}                                                                                                    | 200 OK                  | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"          |
      | a second 412: the script stops         | lenient       | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 412 Precondition Failed | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"          |
      | off the rails: the first 412 stops it  | off-the-rails | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 200 OK                  | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"          |
