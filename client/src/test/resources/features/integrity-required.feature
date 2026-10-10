Feature: A shared layer is fetched only through a link that carries its integrity
  HAL-SCHEMA-FORMS-HR Rule 2. At L1 and L2 the link to the affordance set
  carries integrity, and so does each schema reference (hr:integrity). A
  server using the profile SHALL send them. Shared layers are cached for a
  year and shared between resources, so by default the client refuses a link
  without one, before fetching anything. A client that must work with an
  older server can accept unchecked links; then it follows them unchecked.

  Background:
    Given the service serves schema "water" at its content address:
      """yaml
      type: object
      properties:
        milliliters: {type: integer, minimum: 1}
      """
    And the client works at rung L2

  Scenario: A forms link without integrity is refused, and the set is never fetched
    Given the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}", "hr:integrity": "{integrity:schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}"}
      """
    When the client tries to enter "http://garden.test/beds/2"
    Then the client raised an error mentioning "integrity"
    And the client sent these requests:
      | GET http://garden.test/beds/2 |
    And the cache holds nothing for "http://garden.test{set:planted}"

  Scenario: A schema reference without hr:integrity is refused, and the schema is never fetched
    Given the service serves affordance set "unpinned" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}"}
      """
    And bed 2 links to affordance set "unpinned"
    When the client tries to enter "http://garden.test/beds/2"
    Then the client raised an error mentioning "integrity"
    And the client sent these requests:
      | GET http://garden.test/beds/2        |
      | GET http://garden.test{set:unpinned} |
    And the cache holds nothing for "http://garden.test{schema:water}"

  Scenario: A client that accepts unchecked links follows both, for an older server
    Given the client accepts links without integrity
    And the service serves affordance set "unpinned" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:unpinned}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the manifest offers exactly "waterCell"
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword

  Scenario: A client that accepts unchecked links still refuses a link whose integrity does not match
    Given the client accepts links without integrity
    And the service serves affordance set "planted-v2" at "/forms/planted":
      """yaml
      _forms: {}
      """
    And bed 9 links to affordance set "planted-v2" with integrity "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    When the client tries to enter "http://garden.test/beds/9"
    Then the client raised an error mentioning "integrity"
    And the cache holds nothing for "http://garden.test/forms/planted"
