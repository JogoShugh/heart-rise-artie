Feature: The owner's gateway refuses, in front of GitHub, what its owner's policy and GitHub's own rules don't allow
  spec/ideas/gateway-pass-through.md, the described, connector and offered
  rungs. GitHub's token scopes say little about which operations a client
  may use, and nothing about whether a write makes sense now. The gateway's
  owner can put three rungs in front of GitHub for a client passing through,
  each refusing before GitHub is asked:
  - connector: an operation the connector doesn't list is not the owner's to
    pass (403). The gateway reads only the operations its connector lists, so
    one GitHub has but the connector doesn't is refused here too.
  - described: a body that breaks GitHub's own description of the operation
    (422), each violation at its pointer.
  - offered: a write the connector's rules say is not offered now (409), with
    the rule's own reason, the same one a HEART client is told. The write is
    matched to its act by its operation and the values the act fixes.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state  | state_reason | locked | author |
      | 10     | Login fails on Safari | open   |              | false  | ada    |
      | 11     | Crash on start        | closed | completed    | false  | ada    |

  Scenario: The connector rung: an operation the connector doesn't list is refused
    Given the GitHub gateway's dam is "connector"
    When "ada" sends POST "/repos/acme/rockets/issues/10/reactions" with {"content":"heart"} through the GitHub gateway, passed through
    Then the GitHub gateway answers 403
    And the answer's message is "the owner's gateway passes no POST /repos/acme/rockets/issues/10/reactions through: its connector lists no such operation"
    And GitHub was asked nothing

  Scenario: The described rung: a body that breaks GitHub's description is refused, at its pointer
    Given the GitHub gateway's dam is "described"
    When "ada" sends PATCH "/repos/acme/rockets/issues/10" with {"state":"shut"} through the GitHub gateway, passed through
    Then the GitHub gateway answers 422
    And the answer's message mentions "/state"
    And GitHub was not asked to PATCH "/repos/acme/rockets/issues/10"

  Scenario Outline: The offered rung: a write the rules don't offer now is refused, with their reason: <case>
    Given the GitHub gateway's dam is "offered"
    When "ada" sends PATCH "/repos/acme/rockets/issues/<number>" with <body> through the GitHub gateway, passed through
    Then the GitHub gateway answers <status>
    And the answer's message is "<message>"

    Examples:
      | case                        | number | body                 | status | message                    |
      | closing a closed issue      | 11     | {"state":"closed"}   | 409    | The issue is already closed. |
      | reopening an open issue     | 10     | {"state":"open"}     | 409    | The issue is already open. |
      | closing an open issue: sent | 10     | {"state":"closed"}   | 200    |                            |
      | editing a closed issue: sent | 11    | {"title":"Crash"}    | 200    |                            |
