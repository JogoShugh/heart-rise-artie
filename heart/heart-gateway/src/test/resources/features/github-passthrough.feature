Feature: GitHub reads the connector lists, but doesn't model, pass through the gateway
  spec/PLAN-GATEWAY-URL-LINKS.md, decided 2026-10-18. A connector may list GitHub
  GET operations it doesn't model as HEART resources: a user, a user's
  followers, a pull request's commits. The gateway serves each at GitHub's own
  path, as the caller, with its link members lifted like any other answer's,
  and no forms: readable, not yet modelled. A list is served as a HEART
  collection: an item link for each member, titled by the connector's pointer,
  each member embedded, and GitHub's next page as the page's next link. Links
  to them are named after the member they were (github:followers). Any other
  GitHub read is not served, and links to it keep GitHub's address.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And "ada" has these followers upstream
      | login |
      | lin   |
      | kay   |
      | bo    |

  Scenario: A user is read through the gateway: state, typed links, and no forms
    When "ada" gets "/users/ada" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links these, by type
      | relation         | href                   | type                 | templated |
      | self             | /users/ada             |                      |           |
      | alternate        | https://github.com/ada | text/html            |           |
      | github:followers | /users/ada/followers   | application/hal+json |           |
    And the answer's state still has these members
      | member |
      | login  |
    And the answer offers no forms

  Scenario: A user's followers are a HEART collection, a page at a time
    When "ada" gets "/users/ada/followers" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists
      | href       | title |
      | /users/lin | lin   |
      | /users/kay | kay   |
    And the answer embeds these items
      | self       | login |
      | /users/lin | lin   |
      | /users/kay | kay   |
    And the answer links "next" to "/users/ada/followers?page=2"

  Scenario: A pull request's commits pass through, followed from its link
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And "ada" follows the answer's "github:commits" link through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists one item, titled "Fix the login bug", and embeds it

  Scenario: A GitHub read the connector doesn't list is not served, and its links keep GitHub's address
    When "ada" gets "/users/ada/repos" through the GitHub gateway
    Then the GitHub gateway answers 404
