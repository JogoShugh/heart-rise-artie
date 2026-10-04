Feature: L1, and the L3 sharing property seen from the client
  SPEC §6.10.2, §6.10.4, §6.9.2, §6.9.3. At L1 the set arrives by link with
  its schemas embedded, so the walk is two requests. L3 is a property, not a
  rendering: two resources in the same state link to one set, so behind one
  cache the second costs only its Layer 1 request. That holds only when the
  server templates its targets; a server that puts instance ids in its sets
  mints one set per resource, and the correct expectation is one fetch each.

  Background:
    Given the service serves schema "water" at its content address:
      """yaml
      type: object
      properties:
        milliliters: {type: integer, minimum: 1}
      """

  Scenario: At L1 the client fetches the set, whose schemas are embedded, and nothing more
    Given the service serves affordance set "planted-l1" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema:
            $id: "{schema:water}"
            type: object
            properties:
              milliliters: {type: integer, minimum: 1}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L1"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        http://api.example.org/rels/forms: {href: "{set:planted-l1}"}
      """
    And the client works at rung L1
    When the client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2           |
      | GET http://garden.test{set:planted-l1}  |
    And the client sent GET "http://garden.test/beds/2" with headers:
      | Prefer | forms=cid |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword

  Scenario: Two clients behind one shared cache, two beds in one state: one set fetch (templated server)
    Given the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$id: "{schema:water}"}
      """
    And bed 2 and bed 3 both link to affordance set "planted"
    And the client works at rung L2
    And the second client shares the client's cache
    When the client enters "http://garden.test/beds/2"
    And a second client enters "http://garden.test/beds/3"
    Then the client sent these requests:
      | GET http://garden.test/beds/2        |
      | GET http://garden.test{set:planted}  |
      | GET http://garden.test{schema:water} |
      | GET http://garden.test/beds/3        |

  Scenario: A server with concrete targets mints a set per bed: two set fetches, one schema fetch
    Given the service serves affordance set "bed-2-only" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: /beds/2/actions/water-cell}}
          method: POST
          schema: {$id: "{schema:water}"}
      """
    And the service serves affordance set "bed-3-only" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: /beds/3/actions/water-cell}}
          method: POST
          schema: {$id: "{schema:water}"}
      """
    And bed 2 links to affordance set "bed-2-only"
    And bed 3 links to affordance set "bed-3-only"
    And the client works at rung L2
    When the client enters "http://garden.test/beds/2"
    And the client enters "http://garden.test/beds/3"
    Then the client sent these requests:
      | GET http://garden.test/beds/2           |
      | GET http://garden.test{set:bed-2-only}  |
      | GET http://garden.test{schema:water}    |
      | GET http://garden.test/beds/3           |
      | GET http://garden.test{set:bed-3-only}  |
