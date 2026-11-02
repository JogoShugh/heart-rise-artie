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
  the script, as does a form no longer offered, and so does a change to a
  member the act itself writes: someone else wrote it since the script
  read it, and sending the script's value again would overwrite theirs
  unseen, a lost update (RFC 9110 §13.1.1).

  A form can go out of date too (exhibits/mcp-over-openapi, the changes
  made midway): the service moved its act, or changed its method, after
  the client read the form. The old target answers 404 or 405, which the
  server can't tell from any other. ARTIE reads the resource again; when
  it has changed since the form was read (a new ETag), the act is refused
  as stale, as a 412 is, and decided again on the form offered now.

  And words can go out of date before anything is sent (the goal run in
  exhibits/mcp-over-openapi): a step's words are decided from the
  resource as the step read it, a goal planner's value among them; ARTIE
  syncs before its reasoner chooses (SPEC §6.8), and when the resource
  changed in between, an act on those words, sent with the new ETag,
  would write over the change and never meet a 412. So an act chosen on
  a resource changed since the step read it is refused as stale, by the
  client, before anything is sent, and the step decides again, told what
  changed.

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
      <now>
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
      | case                                            | guard         | now               | forms                                                                                                 | second                  | ended                                                                                                                                                                  |
      | the form still offered: sent again              | lenient       | watered: today    | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 200 OK                  | the script finished                                                                                                                                                    |
      | no longer offered: the script stops             | lenient       | watered: today    | {}                                                                                                    | 200 OK                  | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"                                                          |
      | a second 412: the script stops                  | lenient       | watered: today    | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 412 Precondition Failed | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"                                                          |
      | someone else wrote what it writes: it stops     | lenient       | milliliters: 250  | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 200 OK                  | the script stopped at step 1, "water": stale, refused by the client: "since it was read, someone else changed milliliters, which this act writes; sending it again would overwrite theirs" |
      | off the rails: the first 412 stops it           | off-the-rails | watered: today    | {water: {_links: {target: {href: /beds/2/actions/water}}, method: POST, contentType: application/json, schema: {type: object, properties: {milliliters: {type: integer}}}}} | 200 OK                  | the script stopped at step 1, "water": stale, refused by the server: "the resource changed since it was read"                                                          |

  Scenario Outline: A form out of date, refused <status>, is decided again on the form offered now: <case>
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
      <status>
      Content-Type: application/problem+json

      title: <title>
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
        water:
          _links:
            target: {href: <target>}
          method: <method>
          contentType: application/json
          schema: {type: object, properties: {milliliters: {type: integer}}}
      """
    And the service answers <method> "http://garden.test<target>" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-3.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/beds/2") {
          act("water", "milliliters" to 100)
      }
      """
    Then the script finished

    Examples:
      | case                               | status                 | title              | target                   | method |
      | the act moved to a new address     | 404 Not Found          | Not Found          | /beds/2/actions/irrigate | POST   |
      | the act takes a new method         | 405 Method Not Allowed | Method Not Allowed | /beds/2/actions/water    | PUT    |

  Scenario: An act chosen on a resource changed since the step read it is not sent, and the step decides again
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      watered: never
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
    And the service answers GET "http://garden.test/beds/2" 3 times with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      watered: today
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
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-3.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      watered: twice today
      _links:
        self: {href: /beds/2}
      _forms: {}
      """
    And ARTIE's reasoner will choose to act on "water" with {"milliliters": 100}
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      heart("http://garden.test/beds/2") {
          act(intent = "water it")
      }
      """
    Then the script finished
    And ARTIE's reasoner was asked, in order
      | words                                                                                                                                                                                                                                                                                 |
      | water it                                                                                                                                                                                                                                                                              |
      | water it. Your choice here, water with {"milliliters":"100"}, was refused: the resource changed since this step read it; nothing was sent. Since then: watered was "never", now "today". Decide again from what is there now. If these words would undo or write over that change, do not act: ask, naming it. |
    And the client sent POST "http://garden.test/beds/2/actions/water" with headers:
      | If-Match | "seq-2.hsf.L0" |
