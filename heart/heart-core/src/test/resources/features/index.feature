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
    And it offers these GET forms
      | form              | target        | required field |
      | home:search-lamps | /lamps{?name} | name           |
      | home:get-lamp     | /lamps/{id}   | id             |

  Scenario: In HAL-FORMS, the same two forms are GET templates whose fields go in the query
    When the service index is rendered in HAL-FORMS
    Then it offers these GET templates
      | template    | target | required property |
      | searchLamps | /lamps | name              |
      | getLamp     | /lamps | id                |

  Scenario: The index's validator names its bytes, so it changes only when they do
    Then the index's validator in each dialect is "index-" with the dialect's tag and the first 16 hex digits of its body's digest

  Scenario Outline: The home document says what a server capped at <max> serves
    When the home document is rendered for a server capped at <max>
    Then it names these resources at their templates
      | resource   | template         |
      | item       | /lamps/{id}      |
      | collection | /lamps{?name,id} |
    And the "item" hints offer both dialects with their profiles, and need an ETag to act
    And the "item" hint "accept-prefer" is <declared>

    Examples:
      | max | declared                  |
      | L2  | "forms=link, schema=link" |
      | L0  | absent                    |

  Scenario: Each search page lists its items, titled, and pages by IANA relations
    Then each page of the lamps matching "", 2 to a page, counts 5 and lists
      | page | href     | title  |
      | 1    | /lamps/1 | Lamp 1 |
      | 1    | /lamps/2 | Lamp 2 |
      | 2    | /lamps/3 | Lamp 3 |
      | 2    | /lamps/4 | Lamp 4 |
      | 3    | /lamps/5 | Lamp 5 |
    And each page of the lamps matching "", 2 to a page, links
      | page | rel   | href          |
      | 1    | first | /lamps        |
      | 1    | last  | /lamps?page=3 |
      | 1    | next  | /lamps?page=2 |
      | 2    | first | /lamps        |
      | 2    | last  | /lamps?page=3 |
      | 2    | next  | /lamps?page=3 |
      | 2    | prev  | /lamps        |
      | 3    | first | /lamps        |
      | 3    | last  | /lamps?page=3 |
      | 3    | prev  | /lamps?page=2 |
