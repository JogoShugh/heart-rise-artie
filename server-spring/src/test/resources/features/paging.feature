Feature: A long bed list comes in pages, linked by next, prev, first and last
  SPEC §6.11. A collection too long for one response is split into pages
  linked under the IANA-registered relations next, prev, first and last
  (RFC 8288's registry; the paging pattern of RFC 5005), in HAL's _links. The
  client follows them by relation; the query parameter that selects a page
  is the server's own naming, and the client never builds it. Filters carry
  over into every page link. count is the number of matches across all
  pages. heart.page-size sets the page length.

  Background:
    Given bed 1 is EMPTY and named "North border"
    And bed 2 is PLANTED and named "South border"
    And bed 3 is PLANTED and named "Herb spiral"
    And bed 4 is EMPTY and named "East border"
    And bed 5 is EMPTY and named "West border"
    And the server lists 2 beds per page

  Scenario: The first page links to the next and to the first, not to a previous one
    When a client gets "/beds"
    Then the response status is 200
    And the page lists the items "/beds/1" titled "North border", "/beds/2" titled "South border"
    And the response counts 5 matches
    And the response links next to "/beds?page=2"
    And the response links first to "/beds"
    And the response links last to "/beds?page=3"
    And the response has no link under "prev"

  Scenario: Following next to the last page, which links back but not on
    When a client gets "/beds"
    And the client follows the "next" link
    And the client follows the "next" link
    Then the response status is 200
    And the page lists the items "/beds/5" titled "West border"
    And the response links prev to "/beds?page=2"
    And the response has no link under "next"

  Scenario: A filter carries over into the page links
    When a client gets "/beds?name=border"
    Then the page lists the items "/beds/1" titled "North border", "/beds/2" titled "South border"
    And the response counts 4 matches
    And the response links next to "/beds?name=border&page=2"
    When the client follows the "next" link
    Then the page lists the items "/beds/4" titled "East border", "/beds/5" titled "West border"
    And the response has no link under "next"

  Scenario: A list that fits on one page has no page links
    When a client gets "/beds?name=herb"
    Then the page lists the items "/beds/3" titled "Herb spiral"
    And the response has no link under "next"
    And the response has no link under "prev"
    And the response has no link under "last"

  Scenario: A page past the end is empty and links back to the first
    When a client gets "/beds?page=9"
    Then the response status is 200
    And the page lists the items nothing
    And the response links first to "/beds"
    And the response has no link under "next"

  Scenario: HAL-FORMS pages the same way
    When a client gets "/beds" as hal-forms
    Then the response links next to "/beds?page=2"
