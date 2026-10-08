@live
Feature: ARTIE against the real server
  The same client code, over real HTTP, against server-spring and Postgres.
  Run with `./gradlew liveTest` while the server is up (see
  scripts/live-client-check.sh, which resets the seed data first).
  Seed data: bed 1 EMPTY, bed 2 PLANTED, bed 3 READY_TO_HARVEST, bed 4
  PLANTED (read only, so beds 2 and 4 share a state).
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
      | L1   |
      | L2   |

  Scenario: At L2 a re-sync of an unchanged bed is one request, answered 304
    Given the client works at rung L2
    When the client enters "/beds/3"
    And the client enters "/beds/3"
    Then the wire carried 4 requests, the last answered 304
    And the manifest offers exactly "harvest"

  Scenario: L3: two clients behind one shared cache, two beds in one state, one set fetch
    Given the client works at rung L2
    And the second client shares the client's cache
    When the client enters "/beds/2"
    And a second client enters "/beds/4"
    Then the wire carried 4 requests, the last answered 200
    And the two clients hold the same affordance-set link

  Scenario Outline: L5: both dialects show the same legal acts on bed <bed>
    Then on bed <bed> a hal-schema-forms client and a hal-forms client see the same act targets

    Examples:
      | bed |
      | 1   |
      | 2   |
      | 3   |
      | 4   |

  Scenario: Bed 1 through a full cycle at the HAL-FORMS on-ramp
    Given the client works in dialect hal-forms
    When the client enters "/beds/1"
    And the client acts on "default" with:
      """yaml
      seedType: basil
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    When the client acts on "default" with:
      """yaml
      milliliters: 500
      """
    And the client acts on "default" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key    | value |
      | status | EMPTY |

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

  Scenario Outline: From the bookmark alone at rung <rung>: search by name, follow the result, act
    Given the client works at rung <rung>
    When the client enters "/"
    Then the manifest offers exactly "searchBeds, getBed"
    When the client acts on "searchBeds" with:
      """yaml
      name: BED-2
      """
    And the client follows the "item" link titled "bed-2"
    Then the manifest offers exactly "waterCell"
    When the client acts on "waterCell" with:
      """yaml
      milliliters: 1
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: From the bookmark alone: look a bed up by its id
    When the client enters "/"
    And the client acts on "getBed" with:
      """yaml
      id: 3
      """
    Then the manifest offers exactly "harvest"
