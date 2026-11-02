Feature: The owner's gateway gives a plain client conditional writes GitHub doesn't have
  spec/ideas/gateway-pass-through.md, the conditional rung. GitHub's API takes
  no If-Match on a write: whatever a client sends replaces what is there, even
  what a teammate changed a moment ago. The gateway's owner can turn on the
  conditional rung (GATEWAY_DAM=conditional). Then a read passed through comes
  back with the gateway's own ETag, a digest of what GitHub answered; and a
  write that replaces a resource GitHub also reads (PATCH, PUT, DELETE) must
  carry it. The gateway reads the resource again, as the caller, and refuses a
  write whose validator is no longer current (412), or that has none (428),
  before GitHub is asked. A write that adds to a resource (a comment) is not
  held to it. A client needs no HEART for any of this: only to keep the ETag it
  read and send it back, as HTTP has always asked.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |
    And the GitHub gateway's dam is "conditional"

  Scenario: A read passed through carries the gateway's own ETag, and a read again with it is not modified
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through
    Then the GitHub gateway answers 200
    And the answer's ETag is the gateway's own
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through, with the ETag it read
    Then the GitHub gateway answers 304

  Scenario Outline: A write that replaces the issue is held to the ETag read: <case>
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through
    And <meanwhile>
    And "ada" sends PATCH "/repos/acme/rockets/issues/10" with {"state":"closed"} through the GitHub gateway, passed through, <validator>
    Then the GitHub gateway answers <status>
    And GitHub <asked> to PATCH "/repos/acme/rockets/issues/10"
    And the issue's title upstream is "<title>"

    Examples:
      | case                                     | meanwhile                                                            | validator                | status | asked     | title                 |
      | nothing changed since: done              | nothing happens upstream                                             | with the ETag it read    | 200    | was asked | Login fails on Safari |
      | a teammate changed it since: refused     | a teammate retitles issue 10 of "acme/rockets" upstream to "Safari login" | with the ETag it read    | 412    | was not asked | Safari login     |
      | no validator: refused                    | nothing happens upstream                                             | with no validator        | 428    | was not asked | Login fails on Safari |

  Scenario: The refusal says why, in the gateway's words and GitHub's shape
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway, passed through
    And a teammate retitles issue 10 of "acme/rockets" upstream to "Safari login"
    And "ada" sends PATCH "/repos/acme/rockets/issues/10" with {"state":"closed"} through the GitHub gateway, passed through, with the ETag it read
    Then the GitHub gateway answers 412
    And the answer's message is "the resource changed since it was read; read it again and decide again"

  Scenario: A write that adds to the issue is not held to its ETag
    When "ada" sends POST "/repos/acme/rockets/issues/10/comments" with {"body":"Seen on 1.2 too."} through the GitHub gateway, passed through
    Then the GitHub gateway answers 201
