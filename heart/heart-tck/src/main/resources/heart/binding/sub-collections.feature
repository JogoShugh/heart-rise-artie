Feature: A resource's sub-collections, linked from it and served
  spec/PLAN-GITHUB-ISSUES.md, step I6. A collection under a resource, such as
  an issue's sub-issues, is linked from the resource by a relation of its
  profile and served at its own path, as a search's page is: its members
  under `item`, each titled. A client follows the link; it never builds the
  address. Here a pull request's stack is the pull requests split off it.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server
    And pull request 7 of "acme/rockets" is "open" in the pull request server

  Scenario: A pull request links its stack, and the stack lists what was split off it
    When a client posts {} to "/repos/acme/rockets/pulls/482/actions/split" as plain-json with If-Match ""
    And a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    Then the response links "pulls:stack" to "/repos/acme/rockets/pulls/482/stack"
    When the client follows the "pulls:stack" link
    Then the response status is 200
    And the response links "self" to "/repos/acme/rockets/pulls/482/stack"
    And the response lists these items
      | href                           | title             |
      | /repos/acme/rockets/pulls/1482 | Pull request 1482 |

  Scenario: A pull request nothing was split off has an empty stack
    When a client gets "/repos/acme/rockets/pulls/7/stack" as hal-schema-forms
    Then the response status is 200
    And the response lists these items
      | href | title |

  Scenario: The stack of a pull request that doesn't exist is not found
    When a client gets "/repos/acme/rockets/pulls/99/stack" as hal-schema-forms
    Then the response status is 404
