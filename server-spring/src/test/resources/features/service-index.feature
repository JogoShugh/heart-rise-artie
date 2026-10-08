Feature: Service index: one bookmark reaches every bed
  Fielding: "A REST API should be entered with no prior knowledge beyond the
  initial URI (bookmark)". GET / is the service index. It offers two GET
  forms, each filling its target template from its own fields (HAL Schema
  Forms): search beds by part of their name, and get a bed by its id.
  Search results link to each bed under the IANA "item" relation (RFC 6573),
  titled with the bed's name.

  Background:
    Given bed 1 is EMPTY and named "North border"
    And bed 2 is PLANTED and named "South border"
    And bed 3 is PLANTED and named "Herb spiral"

  Scenario: The index offers search and lookup, names its profile, and is revalidated on every use
    When a client gets the service index
    Then the response status is 200
    And the response Content-Type is "application/hal+json"
    And the response links to profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the response Cache-Control is "no-cache"
    And the response has an ETag
    And the index offers GET form "searchBeds" with target "/beds{?name}" and field "name"
    And the index offers GET form "getBed" with target "/beds/{id}" and field "id"

  Scenario Outline: Searching by part of a name lists the matching beds as titled items: <name>
    When a client gets "/beds?name=<name>"
    Then the response status is 200
    And the response links to profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the response lists the items <items>

    Examples:
      | name    | items                                                |
      | border  | "/beds/1" titled "North border", "/beds/2" titled "South border" |
      | HERB    | "/beds/3" titled "Herb spiral"                       |
      | compost | nothing                                              |

  Scenario: The lookup form's target, filled from its field, is the bed
    When a client gets the service index
    And the client fills the "getBed" form with id 2 and follows it
    Then the response status is 200
    And the response is bed 2
