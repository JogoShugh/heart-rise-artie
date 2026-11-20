Feature: The gateway as hub: a read held until its resource changes, woken by GitHub's signed webhooks
  spec/PLAN-LISTENERS.md, slice 6. A client that watches a resource reads
  it again with the ETag it holds, and may prefer to wait (RFC 7240,
  Prefer: wait=N): while nothing changed, the gateway holds the read, up to
  N seconds, rather than answering 304 at once. A webhook GitHub sends, its
  body signed with the secret both know (X-Hub-Signature-256, an HMAC of
  SHA-256), names what changed; the gateway wakes each held read of a
  resource it names, or of a list of them, or of a list under one, and
  reads it again. Every client waits the same way, whatever the server
  behind the gateway: events made uniform by HEART, and still only hints,
  since what the client is answered is the resource as it is now.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Veracode scan         | open  |              | false  | ada    |
    And issue 10 of "acme/rockets" has these comments upstream
      | id  | author | body                   |
      | 501 | ada    | Is the scan done?      |
    And the GitHub gateway takes GitHub's webhooks, signed with the secret "hub-secret"

  Scenario: A read that prefers to wait is held while unchanged, until a webhook names what changed, then answered
    Given while the next read is held, "bob" comments "Veracode scan failed." on issue 10 of "acme/rockets" upstream
    And GitHub sends its webhook for that comment, signed with "hub-secret", 200 milliseconds after the read is sent
    When "ada" gets "/repos/acme/rockets/issues/10/comments" through the GitHub gateway
    And "ada" gets it again with the ETag she was given, preferring to wait 5 seconds
    Then the GitHub gateway answers 200
    And the answer came in less than 3 seconds
    And the answer says Preference-Applied "wait=5"
    And the answer has an item titled "Veracode scan failed."

  Scenario: A read that prefers to wait, when nothing changes, is answered 304 once its wait is over
    When "ada" gets "/repos/acme/rockets/issues/10/comments" through the GitHub gateway
    And "ada" gets it again with the ETag she was given, preferring to wait 1 seconds
    Then the GitHub gateway answers 304
    And the answer came in no less than 1 seconds
    And the answer says Preference-Applied "wait=1"

  Scenario Outline: A webhook is taken only when its signature is the secret's: <case>
    When GitHub sends its webhook for a comment on issue 10 of "acme/rockets", signed with "<secret>"
    Then the GitHub gateway answers <status>

    Examples:
      | case                | secret       | status |
      | the secret's        | hub-secret   | 202    |
      | another secret's    | wrong-secret | 401    |
