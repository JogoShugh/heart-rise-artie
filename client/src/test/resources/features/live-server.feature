@live
Feature: ARTIE against the real server
  The same client code, over real HTTP, against server-spring and Postgres.
  Run with `./gradlew liveTest` while the server is up (see
  scripts/live-client-check.sh, which resets the seed data first).
  Seed data: bed 1 EMPTY, bed 2 PLANTED, bed 3 READY_TO_HARVEST.
  Assertions avoid exact ETag values, and every scenario leaves its bed in
  the state it found it (1 ml adds no growth), so the suite can run again
  without a reset.

  Scenario Outline: Bed 1 through a full cycle at rung <rung>: sow, water, harvest
    Given the client works at rung <rung>
    When the client enters "/beds/1"
    Then the manifest offers exactly "sowSeed"
    When the client acts on "sowSeed" with:
      """yaml
      seedType: basil
      depthCm: 2
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    And the manifest offers exactly "waterCell"
    When the client acts on "waterCell" with:
      """yaml
      milliliters: 500
      """
    Then the resource state contains:
      | key    | value            |
      | status | READY_TO_HARVEST |
    And the manifest offers exactly "harvest"
    When the client acts on "harvest" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key    | value |
      | status | EMPTY |
    And the manifest offers exactly "sowSeed"

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: At L2 a re-sync of an unchanged bed is one request, answered 304
    Given the client works at rung L2
    When the client enters "/beds/3"
    And the client enters "/beds/3"
    Then the wire carried 4 requests, the last answered 304
    And the manifest offers exactly "harvest"

  Scenario: Someone else writes first: the act is refused as stale and dropped
    Given the client enters "/beds/2"
    And a second client enters "/beds/2"
    When the second client acts on "waterCell" with:
      """yaml
      milliliters: 1
      """
    And the client tries to act on "waterCell" with:
      """yaml
      milliliters: 1
      """
    Then the client raised a "StaleAct" refusal
    And the client now holds the same ETag as the second client

  Scenario: A body that fails the server's schema is refused with its reasons
    Given the client enters "/beds/2"
    When the client tries to act on "waterCell" with:
      """yaml
      milliliters: 0
      """
    Then the client raised a "InvalidPayload" refusal
    And the client raised an error mentioning "milliliters"
