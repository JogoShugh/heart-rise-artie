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
    And the index offers GET form "garden:search-beds" with target "/beds{?name}" and field "name"
    And the index offers GET form "garden:get-bed" with target "/beds/{id}" and field "id"

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
    And the client fills the "garden:get-bed" form with id 2 and follows it
    Then the response status is 200
    And the response is bed 2

  Scenario: Beds can also be looked up by id in the query, which lists exactly that bed
    When a client gets "/beds?id=2"
    Then the response status is 200
    And the response lists the items "/beds/2" titled "South border"

  Scenario: HAL-FORMS is a first-class entry point: the same two forms, as query GET templates
    HAL-FORMS cannot put a field into a target's path, but a GET template
    puts its fields in the query string (the HTML mutate-action-URL
    algorithm), so lookup by id uses /beds?id= instead of /beds/{id}.
    When a client gets the service index as hal-forms
    Then the response status is 200
    And the response Content-Type is "application/prs.hal-forms+json"
    And the response links to profile "https://github.com/jogoshugh/heart-rise/hal-forms"
    And the response Cache-Control is "no-cache"
    And the response has an ETag
    And the index offers HAL-FORMS GET template "searchBeds" with target "/beds" and required property "name"
    And the index offers HAL-FORMS GET template "getBed" with target "/beds" and required property "id"

  Scenario Outline: Search answers in the dialect asked for: <dialect>
    When a client gets "/beds?name=herb" as <dialect>
    Then the response status is 200
    And the response Content-Type is "<media type>"
    And the response links to profile "https://github.com/jogoshugh/heart-rise/<dialect>"
    And the response lists the items "/beds/3" titled "Herb spiral"

    Examples:
      | dialect          | media type                     |
      | hal-schema-forms | application/hal+json           |
      | hal-forms        | application/prs.hal-forms+json |

  Scenario: Both dialects' indexes offer the same forms
    HAL Schema Forms names a form by relation and HAL-FORMS by template
    name, so the two are matched by title, which both dialects carry.
    When a client gets the service index
    And a client gets the service index as hal-forms
    Then the two indexes offer forms with the same titles
