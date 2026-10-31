Feature: ARTIE sends each act by the method its form says
  SPEC §6.2. A form says the method its act is served by. ARTIE sends a POST
  or a PUT with a JSON body, a PATCH with a JSON Merge Patch, and a DELETE
  with no body: a DELETE's fields fill its target's query template (Rule 1).
  In HAL-FORMS, a DELETE's properties go in the query, as a GET's do.

  Background:
    Given the service answers GET "http://shop.test/shelves/3" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      label: Tea
      _forms:
        relabel:
          _links:
            target: {href: /shelves/3/actions/relabel}
          method: PATCH
          contentType: application/merge-patch+json
          schema:
            type: object
            properties:
              label: {type: string}
            required: [label]
        setPrice:
          _links:
            target: {href: /shelves/3/actions/set-price}
          method: PUT
          contentType: application/json
          schema:
            type: object
            properties:
              price: {type: integer}
            required: [price]
        removeTag:
          _links:
            target: {href: "/shelves/3/actions/remove-tag{?count,tag}", templated: true}
          method: DELETE
          schema:
            type: object
            properties:
              count: {type: integer}
              tag: {type: string}
            required: [tag]
      """

  Scenario Outline: An act with a body goes by <method>, its body as its form's content type says
    Given the service answers <method> "<sent to>" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      label: Tea
      _forms: {}
      """
    When the client enters "http://shop.test/shelves/3"
    And the client acts on "<form>" with:
      """yaml
      <fields>
      """
    Then the client sent <method> "<sent to>" with headers:
      | Content-Type | <content type> |
    And that request had the JSON body <body>

    Examples:
      | form     | fields             | method | sent to                                      | content type                 | body                  |
      | relabel  | {label: Green tea} | PATCH  | http://shop.test/shelves/3/actions/relabel   | application/merge-patch+json | {"label":"Green tea"} |
      | setPrice | {price: 500}       | PUT    | http://shop.test/shelves/3/actions/set-price | application/json             | {"price":500}         |

  Scenario: A DELETE goes with no body, its fields in its target's query
    Given the service answers DELETE "http://shop.test/shelves/3/actions/remove-tag?count=2&tag=sale" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      label: Tea
      _forms: {}
      """
    When the client enters "http://shop.test/shelves/3"
    And the client acts on "removeTag" with:
      """yaml
      tag: sale
      count: 2
      """
    Then the client sent these requests after entering:
      | DELETE http://shop.test/shelves/3/actions/remove-tag?count=2&tag=sale |
    And that request had no body

  Scenario: In HAL-FORMS, a DELETE's properties go in the query, and it has no body
    Given the service answers GET "http://shop.test/shelves/4" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      ETag: "seq-0.hf.L0"
      Link: <https://w3id.org/heart-rise/hal-forms>; rel="profile"

      id: 4
      _links:
        self: {href: /shelves/4}
      _templates:
        removeTag:
          method: DELETE
          target: /shelves/4/actions/remove-tag
          properties:
            - {name: tag, type: text, required: true}
      """
    And the service answers DELETE "http://shop.test/shelves/4/actions/remove-tag?tag=sale" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      ETag: "seq-1.hf.L0"
      Link: <https://w3id.org/heart-rise/hal-forms>; rel="profile"

      id: 4
      _links:
        self: {href: /shelves/4}
      """
    And the client works in dialect hal-forms
    When the client enters "http://shop.test/shelves/4"
    And the client acts on "removeTag" with:
      """yaml
      tag: sale
      """
    Then the client sent these requests after entering:
      | DELETE http://shop.test/shelves/4/actions/remove-tag?tag=sale |
    And that request had no body
