Feature: A repository offers to open an issue
  spec/PLAN-GITHUB-ISSUES.md, step I5. New things come from acts a resource
  offers, never from a bare create: a repository, read at GitHub's own
  /repos/{owner}/{repo}, offers "open an issue". Its act is GitHub's
  issues/create; GitHub's answer is the new issue, and the gateway answers
  201 with that issue's address in Location. A repository with issues
  turned off, or archived, says why it offers nothing.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |

  Scenario: A repository is read at GitHub's own path, and offers to open an issue
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets"
    And the GitHub gateway offers
      | act               |
      | github:open-issue |

  Scenario: The form asks for what GitHub's issues/create takes
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    Then the forms ask for these fields
      | act               | field     |
      | github:open-issue | title     |
      | github:open-issue | body      |
      | github:open-issue | assignees |
      | github:open-issue | labels    |
      | github:open-issue | milestone |

  Scenario: Opening an issue answers 201, naming the new issue in Location
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    And the client sends "github:open-issue" with {"title":"Add dark mode","labels":["idea"]} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/issues/11"
    And GitHub was asked to POST "/repos/acme/rockets/issues" with {"title":"Add dark mode","labels":["idea"]}
    When "ada" gets "/repos/acme/rockets/issues/11" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the pull request's title is now "Add dark mode"

  Scenario Outline: An act with no validator, with the development switch <switch>
    spec/ideas/off-the-rails.md. By default an act with no validator is
    refused, 428. With HEART_ACTS_WITHOUT_VALIDATOR=allowed, for a trial, it
    is checked against the state now and sent on.
    Given the GitHub gateway's switch for acts with no validator is <switch>
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    And the client sends "github:open-issue" with {"title":"Add dark mode"} and no validator
    Then the GitHub gateway answers <status>

    Examples:
      | switch | status |
      | off    | 428    |
      | on     | 201    |

  Scenario Outline: A repository that won't take issues says why
    Given the repository "acme/rockets" <condition> upstream
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    Then the GitHub gateway offers nothing
    And the GitHub gateway says these acts are absent, and why
      | act               | reason   |
      | github:open-issue | <reason> |

    Examples:
      | condition              | reason                                        |
      | has issues turned off  | Issues are turned off for this repository.    |
      | is archived            | The repository is archived; it takes no issues. |
