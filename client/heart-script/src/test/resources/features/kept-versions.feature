Feature: Resources kept by name: their own address, a check that a value moved, and an act on the version kept
  A release asks a watch to look at the pull request it opened: the watch needs the pull request's
  own address, which the server handed over, so `${pr.@self}` reads the address the resource kept as
  `pr` was kept at. A smoke test checks that renewing moved a loan's due date, whatever the policy
  says it moves to: a fact `is-not` holds when the member differs from a value. And it checks that a
  renewal on the version read before the last one is refused as stale: `as-of` acts with the
  version of the resource kept by that name, not the one held, so the server can say it moved on.

  Background:
    Given a service holds these resources
      | address | title   | state  |
      | /       | Library |        |
      | /loan   | Matilda | lent   |
    And these links
      | at | relation | title   | to    |
      | /  | item     | Matilda | /loan |
    And these forms
      | at    | relation | leads to | outcome |
      | /loan | renew    | /loan    | done    |

  Scenario: A resource kept by name is read at the address it was kept at
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - follow: item
          title: Matilda
        - keep-as: loan
        - note: "the loan is at ${loan.@self}"
      """
    Then the script finished
    And the script noted "the loan is at /loan"

  Scenario Outline: A fact that a member is not a value
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - follow: item
          title: Matilda
        - check: [{field: state, is-not: <value>}]
      """
    Then the script <outcome>

    Examples:
      | value    | outcome                                                       |
      | returned | finished                                                      |
      | lent     | stopped at step 2, "check": "state: expected not lent, was lent" |

  Scenario: An act as of the version kept by name carries that version, not the one held
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - follow: item
          title: Matilda
        - keep-as: before
        - act: renew
        - act: renew
          as-of: before
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                  |
      | enter  | scripted://service/   |
      | follow | item "Matilda"        |
      | act    | renew                 |
      | act    | renew as of "/loan@0" |
