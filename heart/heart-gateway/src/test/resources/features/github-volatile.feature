Feature: What GitHub works out by itself is no change to the state an act was checked against
  Just before an act, the gateway reads the resource again and compares it
  with the state the act was checked against; a difference means someone
  changed it, and the act is stale (412). But some members change with no
  write at all: GitHub works out a pull request's mergeability in the
  background, so a new pull request reads "mergeable": null, then, a second
  later, true. Nobody changed it.

  A resource file names such members as volatile. The comparison leaves
  them out; what they decide is checked instead: the act must still be
  offered by the state read just before it, or it is refused, 409, with the
  reason. Every other member still counts, so a real change is still stale.

  A client's If-Match is compared whole, as ever (SPEC, decided 2026-10-08):
  when mergeability resolves, the forms change, so the representation the
  client holds is no longer current, and its client reads it again.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And GitHub hasn't yet worked out whether pull request 482 can be merged

  Scenario Outline: After the gateway reads pull request 482 to check the act, <meanwhile>
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And after the gateway's next read of pull request 482, <meanwhile>
    And the client sends "<act>" with <body> and the ETag it holds
    Then the GitHub gateway answers <status>

    Examples:
      | meanwhile                                                | act          | body                         | status |
      | GitHub works out that it can be merged cleanly           | github:edit  | {"title":"Fix the login"}    | 200    |
      | its title changes upstream to "Fix login"                | github:edit  | {"title":"Fix the login"}    | 412    |

  Scenario: An act the state no longer offers, once GitHub has worked it out, is refused with the reason
    Given GitHub has worked out that pull request 482 can be merged cleanly
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And after the gateway's next read of pull request 482, GitHub works out that it is in conflict
    And the client sends "github:merge" with {} and the ETag it holds
    Then the GitHub gateway answers 409
    And GitHub was never asked for "/repos/acme/rockets/pulls/482/merge"
