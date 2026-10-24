Feature: An act says the HTTP method it is served by
  SPEC §6.2 and HAL-SCHEMA-FORMS-HR Rule 7. An act is declared with its
  method: POST, the default, PUT, PATCH or DELETE. Its relation's ALPS type
  agrees: a PUT or a DELETE is idempotent, a POST or a PATCH is unsafe, and an
  act is never safe. A PATCH takes a JSON Merge Patch (RFC 7396). A DELETE
  takes no body: its fields, if any, are its target's query, as a URI
  template the client fills (Rule 1). A bookmark's act brings a resource into
  being, so it is a POST. The method is part of the form's integrity.

  Background:
    Given a shelf resource type declared with the profile "store"

  Scenario: Each act's form says its method, its content type and its target
    When a stocked shelf's affordance set is rendered
    Then its forms say
      | form             | method | contentType                  | target                                      |
      | store:restock    | POST   | application/json             | /shelves/{id}/actions/restock               |
      | store:relabel    | PATCH  | application/merge-patch+json | /shelves/{id}/actions/relabel               |
      | store:set-price  | PUT    | application/json             | /shelves/{id}/actions/set-price             |
      | store:remove-tag | DELETE |                              | /shelves/{id}/actions/remove-tag{?count,tag} |
      | store:retire     | DELETE |                              | /shelves/{id}/actions/retire                |

  Scenario: At L0, a DELETE's target is still a template, for its query
    When shelf 3 is rendered at L0
    Then its forms say
      | form             | method | contentType                  | target                                 |
      | store:relabel    | PATCH  | application/merge-patch+json | /shelves/3/actions/relabel             |
      | store:remove-tag | DELETE |                              | /shelves/3/actions/remove-tag{?count,tag} |
      | store:retire     | DELETE |                              | /shelves/3/actions/retire              |
    And the "store:remove-tag" form's target is templated
    And the "store:retire" form's target is not templated

  Scenario: In HAL-FORMS, each template says its method and content type
    When shelf 3 is rendered in HAL-FORMS
    Then its templates say
      | template  | method | contentType                  | target                          |
      | restock   | POST   | application/json             | /shelves/3/actions/restock      |
      | relabel   | PATCH  | application/merge-patch+json | /shelves/3/actions/relabel      |
      | setPrice  | PUT    | application/json             | /shelves/3/actions/set-price    |
      | removeTag | DELETE |                              | /shelves/3/actions/remove-tag   |
      | retire    | DELETE |                              | /shelves/3/actions/retire       |

  Scenario: The OpenAPI twin serves each act by its method
    When the OpenAPI description of the shelf is rendered
    Then its act operations are exactly
      | method | path                               | operationId | body                         | query      |
      | POST   | /shelves/{id}/actions/restock      | restock     | application/json             |            |
      | PATCH  | /shelves/{id}/actions/relabel      | relabel     | application/merge-patch+json |            |
      | PUT    | /shelves/{id}/actions/set-price    | setPrice    | application/json             |            |
      | DELETE | /shelves/{id}/actions/remove-tag   | removeTag   |                              | count, tag |
      | DELETE | /shelves/{id}/actions/retire       | retire      |                              |            |

  Scenario: The method and content type are part of the form's integrity
    Then the shelf's "store:relabel" form's integrity is that of these members, canonical
      | member              | value                                       |
      | target              | /shelves/{id}/actions/relabel               |
      | hr:templatePointers | {"id": "/id"}                               |
      | method              | PATCH                                       |
      | contentType         | application/merge-patch+json                |
      | schema              | the integrity of the "store:relabel" schema |

  Scenario Outline: A declaration whose method its relation disagrees with is refused: <method> for <type>
    When a shelf act is declared <where> with the method <method> and a relation that is <type>
    Then the shelf's declaration is refused, saying "<why>"

    Examples:
      | where           | method | type       | why                                                                     |
      | on the resource | PUT    | unsafe     | act tryIt is a PUT, so its relation try-it must be idempotent           |
      | on the resource | DELETE | unsafe     | act tryIt is a DELETE, so its relation try-it must be idempotent        |
      | on the resource | POST   | idempotent | act tryIt is a POST, so its relation try-it must be unsafe              |
      | on the resource | PATCH  | idempotent | act tryIt is a PATCH, so its relation try-it must be unsafe             |
      | on the resource | GET    | safe       | act tryIt can't be a GET: an act is served by POST, PUT, PATCH or DELETE |
      | on the bookmark | PUT    | idempotent | bookmark act tryIt brings a resource into being, so it is a POST        |

  Scenario Outline: A DELETE act's input is its query, typed by its schema: <query>
    When the shelf act "removeTag" is asked by DELETE with the query "<query>"
    Then the act's input is <input>

    Examples:
      | query            | input                       |
      | tag=sale&count=2 | {"tag":"sale","count":2}    |
      | tag=sale         | {"tag":"sale"}              |
      | tag=two%20words  | {"tag":"two words"}         |
      | count=two        | {"count":"two"}             |

  Scenario Outline: An act asked by another method is not allowed, and says the one it is: <act> by <asked>
    When the shelf act "<act>" is asked by <asked>
    Then it is <outcome>

    Examples:
      | act       | asked  | outcome                  |
      | setPrice  | PUT    | allowed                  |
      | setPrice  | POST   | not allowed, Allow: PUT  |
      | relabel   | PUT    | not allowed, Allow: PATCH |
      | removeTag | DELETE | allowed                  |
      | restock   | DELETE | not allowed, Allow: POST |
