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
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |

  Scenario: What GitHub's example pull request offers in each state
    Then GitHub's own example pull request, number 1347 of "octocat/Hello-World", offers in each state
      | state               | close | reopen | merge | edit | request-review | update-branch |
      | open and clean      | yes   | no     | yes   | yes  | yes            | yes           |
      | a draft             | yes   | no     | no    | yes  | yes            | yes           |
      | behind its base     | yes   | no     | no    | yes  | yes            | yes           |
      | blocked             | yes   | no     | no    | yes  | yes            | yes           |
      | still being checked | yes   | no     | no    | yes  | yes            | yes           |
      | in conflict         | yes   | no     | no    | yes  | yes            | no            |
      | merged              | no    | no     | no    | yes  | no             | no            |
      | closed              | no    | yes    | no    | yes  | no             | no            |

  Scenario: Why GitHub's example pull request leaves an act out, in each state
    Then GitHub's own example pull request, number 1347 of "octocat/Hello-World", says why an act is absent
      | state               | act            | reason                                                                     |
      | open and clean      | reopen         | The pull request is already open.                                          |
      | a draft             | merge          | A draft can't be merged; mark it ready for review first.                   |
      | behind its base     | merge          | The branch is behind its base; update it first.                            |
      | blocked             | merge          | Branch protection blocks the merge: a required check or review is missing. |
      | still being checked | merge          | GitHub is still checking whether it can be merged; read it again shortly.  |
      | in conflict         | merge          | The branch has conflicts with its base; resolve them first.                |
      | in conflict         | update-branch  | The branch has conflicts with its base; resolve them first.                |
      | merged              | reopen         | A merged pull request can't be reopened.                                   |
      | merged              | request-review | A closed pull request can't have reviews requested; reopen it first.       |
      | closed              | close          | The pull request is already closed.                                        |
      | closed              | merge          | A closed pull request can't be merged; reopen it first.                    |
      | closed              | update-branch  | A closed pull request's branch isn't updated; reopen it first.             |

  Scenario: A caller who can't push is offered nothing, by the repository's own answer
    Given GitHub's own example pull request, number 1347 of "octocat/Hello-World", is open and clean
    And "bob" can't push to "octocat/Hello-World"
    When "bob" gets "/repos/octocat/Hello-World/pulls/1347" through the GitHub gateway
    Then the GitHub gateway offers nothing
    And the GitHub gateway says "github:merge" is absent because "You can't push to this repository."

  Scenario: The shipped connector's search lists GitHub's own example at its own path
    Given GitHub's own example pull request, number 1347 of "octocat/Hello-World", is open and clean
    When "ada" gets "/repos/octocat/Hello-World/pulls?state=open" through the GitHub gateway
    Then the answer lists "/repos/octocat/Hello-World/pulls/1347" titled "Amazing new feature"
