Feature: Finding the affordance set: the heart-rise forms rel, full or short
  SPEC §6.1 (0.5). At L1 and L2, Layer 1 links to its affordance set under
  the rel https://github.com/jogoshugh/heart-rise/rels/forms. A server may
  write it in HAL's CURIE short form ("hr:forms" with a curies entry); the
  client expands the prefix and treats both the same. A link under any
  other name is not the affordance set.

  Background:
    Given the service serves schema "water" at its content address:
      """yaml
      type: object
      properties:
        milliliters: {type: integer}
      """
    And the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}", "hr:integrity": "{integrity:schema:water}"}
      """
    And the client works at rung L2

  Scenario Outline: The client finds the set under the <form> name
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        curies: [{name: <prefix>, href: "https://github.com/jogoshugh/heart-rise/rels/{rel}", templated: true}]
        <rel>: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the manifest offers exactly "waterCell"

    Examples:
      | form                    | prefix | rel                                                 |
      | full URI                | hr     | https://github.com/jogoshugh/heart-rise/rels/forms |
      | hr: short form          | hr     | hr:forms                                            |
      | short form, own prefix  | garden | garden:forms                                        |

  Scenario Outline: A link under another name is not the affordance set: <rel>
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        <rel>: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the client's current manifest is empty
    And the client sent these requests:
      | GET http://garden.test/beds/2 |

    Examples:
      | rel                                |
      | http://api.example.org/rels/forms  |
      | forms                              |
      | hr:forms                           |
