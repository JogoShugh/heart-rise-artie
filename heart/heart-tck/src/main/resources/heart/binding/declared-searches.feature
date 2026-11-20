Feature: A binding serves a declared search at its collection path
  spec/PLAN-GITHUB-PULLS.md, phase 2. A type may declare its collection path
  and the fields of its search and lookup, as GitHub lists a repository's
  pull requests at /repos/{owner}/{repo}/pulls, filtered by state. The
  binding serves the search there: the path's variables and the filters
  choose the matches, and each page lists them at their item paths. A client
  fills the index's forms and follows them; it builds no address itself.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server
    And pull request 7 of "acme/rockets" is "open" in the pull request server
    And pull request 9 of "acme/rockets" is "closed" in the pull request server
    And pull request 3 of "acme/engines" is "open" in the pull request server

  Scenario: A search fills the collection path and filters by the query
    When a client gets "/repos/acme/rockets/pulls?state=open" as hal-schema-forms
    Then the response status is 200
    And the response lists these items
      | href                          | title            |
      | /repos/acme/rockets/pulls/7   | Pull request 7   |
      | /repos/acme/rockets/pulls/482 | Pull request 482 |
    And the response links "self" to "/repos/acme/rockets/pulls?state=open"

  Scenario: Without a filter, the search lists every pull request of the repository
    When a client gets "/repos/acme/engines/pulls" as hal-schema-forms
    Then the response lists these items
      | href                        | title          |
      | /repos/acme/engines/pulls/3 | Pull request 3 |

  Scenario: A client fills the index's search form and follows it
    When a client gets "/" as hal-schema-forms
    And the client fills the "pulls:list" form with these values and follows it
      | field | value   |
      | owner | acme    |
      | repo  | rockets |
      | state | closed  |
    Then the response lists these items
      | href                        | title          |
      | /repos/acme/rockets/pulls/9 | Pull request 9 |

  Scenario: A client fills the index's lookup form and follows it to the pull request
    When a client gets "/" as hal-schema-forms
    And the client fills the "pulls:get" form with these values and follows it
      | field  | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 482     |
    Then the response status is 200
    And the response links "self" to "/repos/acme/rockets/pulls/482"
