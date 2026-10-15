Feature: A long search comes in pages, linked by next, prev, first and last
  SPEC §6.11. heart-core renders each page's links (its index.feature);
  these scenarios follow them over HTTP. The client follows the links by
  relation and never builds the query that selects a page. A filter carries
  over into every page link. The server's page size sets the page length.

  Background:
    Given lamps 1 to 5 are "ON" in the lamp server
    And the server lists 2 lamps per page

  Scenario: Following next to the last page, which links back but not on
    When a client gets "/lamps" as hal-schema-forms
    And the client follows the "next" link
    And the client follows the "next" link
    Then the response lists the items "/lamps/5" titled "Lamp 5"
    And the response links "prev" to "/lamps?page=2"
    And the response has no link under "next"

  Scenario: A filter carries over into the page links
    When a client gets "/lamps?name=lamp" as hal-schema-forms
    Then the response links "next" to "/lamps?name=lamp&page=2"
    When the client follows the "next" link
    Then the response lists the items "/lamps/3" titled "Lamp 3", "/lamps/4" titled "Lamp 4"

  Scenario: A search that fits on one page has no page links
    When a client gets "/lamps?name=3" as hal-schema-forms
    Then the response lists the items "/lamps/3" titled "Lamp 3"
    And the response has no link under "next"
    And the response has no link under "prev"
    And the response has no link under "last"

  Scenario: A page past the end is empty, and links back to the first
    When a client gets "/lamps?page=9" as hal-schema-forms
    Then the response status is 200
    And the response lists the items nothing
    And the response links "first" to "/lamps"
    And the response has no link under "next"

  Scenario: HAL-FORMS pages the same way
    When a client gets "/lamps" as hal-forms
    Then the response links "next" to "/lamps?page=2"
