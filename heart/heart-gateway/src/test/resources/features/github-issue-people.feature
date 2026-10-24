Feature: Labelling an issue, assigning it, and setting its milestone
  spec/PLAN-GITHUB-ISSUES.md, step I3. Each act is one of GitHub's
  operations, declared in the connector's issue file. Removing a label
  names the label in GitHub's path, /labels/{name}, not in a body: the
  form asks for it as it asks for any field, and the gateway puts it in
  the path, encoded.

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

  Scenario: Each form asks for what GitHub's operation takes
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the forms ask for these fields
      | act                  | field     |
      | github:label         | labels    |
      | github:unlabel       | name      |
      | github:assign        | assignees |
      | github:unassign      | assignees |
      | github:set-milestone | milestone |

  Scenario Outline: Each act is GitHub's operation, with the client's fields in its body or its path
    Given issue 10 of "acme/rockets" has these labels and assignees upstream
      | label            | assignee |
      | bug              | bob      |
      | good first issue |          |
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:<act>" with <sent> and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to <method> "<path>" with <github gets>

    Examples:
      | act           | sent                        | method | path                                                    | github gets                 |
      | label         | {"labels":["safari"]}       | POST   | /repos/acme/rockets/issues/10/labels                    | {"labels":["safari"]}       |
      | assign        | {"assignees":["ada"]}       | POST   | /repos/acme/rockets/issues/10/assignees                 | {"assignees":["ada"]}       |
      | unassign      | {"assignees":["bob"]}       | DELETE | /repos/acme/rockets/issues/10/assignees                 | {"assignees":["bob"]}       |
      | set-milestone | {"milestone":3}             | PATCH  | /repos/acme/rockets/issues/10                           | {"milestone":3}             |

  Scenario: Removing a label puts its name in GitHub's path, encoded, and sends no body
    Given issue 10 of "acme/rockets" has these labels and assignees upstream
      | label            | assignee |
      | bug              | bob      |
      | good first issue |          |
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:unlabel" with {"name":"good first issue"} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to DELETE "/repos/acme/rockets/issues/10/labels/good%20first%20issue"
    And the issue's labels are now
      | label |
      | bug   |
