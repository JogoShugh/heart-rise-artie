Feature: The GitHub connector, as shipped, on GitHub's own answers in every state
  spec/PLAN-GITHUB-PULLS.md, phase 8. The connector ships with the gateway
  (connectors/github). Here it reads GitHub's own example answers, the pull
  request and the repository from GitHub's description, as a small GitHub
  in memory returns them, with the fields that say a pull request's state
  set to each state it can be in. For each, the gateway offers what GitHub
  would allow, and says why not the rest.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows the callers "ada" and "bob"

  Scenario Outline: A pull request that is <state> offers <offers>
    Given GitHub's own example pull request, number 1347 of "octocat/Hello-World", is <state>
    When "ada" gets "/repos/octocat/Hello-World/pulls/1347" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the GitHub gateway offers "<offers>"
    And the GitHub gateway says "<absent>" is absent because "<reason>"

    Examples:
      | state                   | offers                     | absent        | reason                                                                       |
      | open and clean          | github:close, github:merge | github:reopen | The pull request is already open.                                            |
      | a draft                 | github:close               | github:merge  | A draft can't be merged; mark it ready for review first.                     |
      | behind its base         | github:close               | github:merge  | The branch is behind its base; update it first.                              |
      | blocked                 | github:close               | github:merge  | Branch protection blocks the merge: a required check or review is missing. |
      | still being checked     | github:close               | github:merge  | GitHub is still checking whether it can be merged; read it again shortly.    |
      | in conflict             | github:close               | github:merge  | The branch has conflicts with its base; resolve them first.                  |
      | merged                  |                            | github:reopen | A merged pull request can't be reopened.                                     |
      | closed                  | github:reopen              | github:merge  | A closed pull request can't be merged; reopen it first.                      |

  Scenario: A caller who can't push is offered nothing, by the repository's own answer
    Given GitHub's own example pull request, number 1347 of "octocat/Hello-World", is open and clean
    And "bob" can't push to "octocat/Hello-World"
    When "bob" gets "/repos/octocat/Hello-World/pulls/1347" through the GitHub gateway
    Then the GitHub gateway offers ""
    And the GitHub gateway says "github:merge" is absent because "You can't push to this repository."

  Scenario: The shipped connector's search lists GitHub's own example at its own path
    Given GitHub's own example pull request, number 1347 of "octocat/Hello-World", is open and clean
    When "ada" gets "/repos/octocat/Hello-World/pulls?state=open" through the GitHub gateway
    Then the answer lists "/repos/octocat/Hello-World/pulls/1347" titled "Amazing new feature"
