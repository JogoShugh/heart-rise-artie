Feature: The service index, the home document and search pages, from the declarations
  SPEC §6.11 and §6.11.1. Fielding: a REST API is entered with no prior
  knowledge beyond the initial URI. heart-core builds the bookmark's
  representations from the declared resource types: a search form and a
  lookup form for each, the JSON Home document that says what the server
  serves, and the pages of a search. Every binding serves these same bodies.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamps 1 to 5 exist

  Scenario: In HAL Schema Forms, the index offers a search form and a lookup form per type
    When the service index is rendered in HAL Schema Forms
    Then the index is titled "Lamps" and links "self" to "/"
    And it offers the GET form "home:search-lamps" with target "/lamps{?name}" and required field "name"
    And it offers the GET form "home:get-lamp" with target "/lamps/{id}" and required field "id"

  Scenario: In HAL-FORMS, the same two forms are GET templates whose fields go in the query
    When the service index is rendered in HAL-FORMS
    Then it offers the GET template "searchLamps" with target "/lamps" and required property "name"
    And it offers the GET template "getLamp" with target "/lamps" and required property "id"

  Scenario: The index's validator names its bytes, so it changes only when they do
    Then the index's validator in each dialect is "index-" with the dialect's tag and the first 16 hex digits of its body's digest

  Scenario Outline: The home document says what a server capped at <max> serves
    When the home document is rendered for a server capped at <max>
    Then it names "item" at template "/lamps/{id}" and "collection" at template "/lamps{?name,id}"
    And the "item" hints offer both dialects with their profiles, and need an ETag to act
    And the "item" hint "accept-prefer" is <declared>

    Examples:
      | max | declared                  |
      | L2  | "forms=link, schema=link" |
      | L0  | absent                    |

  Scenario Outline: A search page lists its items, titled, and pages by IANA relations: page <page>
    When page <page> of the lamps matching "" is rendered, 2 to a page
    Then the page counts 5 and lists the items <items>
    And its paging links are <links>

    Examples:
      | page | items                                         | links                                                                  |
      | 1    | "/lamps/1" titled "Lamp 1", "/lamps/2" titled "Lamp 2" | first "/lamps", last "/lamps?page=3", next "/lamps?page=2"             |
      | 2    | "/lamps/3" titled "Lamp 3", "/lamps/4" titled "Lamp 4" | first "/lamps", last "/lamps?page=3", next "/lamps?page=3", prev "/lamps" |
      | 3    | "/lamps/5" titled "Lamp 5"                    | first "/lamps", last "/lamps?page=3", prev "/lamps?page=2"             |
