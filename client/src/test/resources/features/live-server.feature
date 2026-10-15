@live
Feature: ARTIE against the real server
  The same client code, over real HTTP, against server-spring and Postgres.
  Run with `./gradlew liveTest` while the server is up (see
  scripts/live-client-check.sh, which resets the seed data first).
  Seed data: bed 1 EMPTY, bed 2 PLANTED, bed 3 READY_TO_HARVEST, bed 4
  PLANTED (read only, so beds 2 and 4 share a state).
  Assertions avoid exact ETag values, and every scenario leaves its bed in
  the state it found it (1 ml adds no growth). Each scenario still starts
  from the seed data: under Buster a mutated scenario can stop halfway, and
  the next scenario must not inherit a half-grown bed, or its requests stop
  matching the dry run its mutations were planned from.

  Background:
    Given the beds are at their seed state

  Scenario Outline: Bed 1 through a full cycle at rung <rung>: sow, water, harvest
    Given the client works at rung <rung>
    When the client enters "/beds/1"
    Then the manifest offers exactly "garden:sow-seed"
    When the client acts on "garden:sow-seed" with:
      """yaml
      seedType: basil
      depthCm: 2
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    And the manifest offers exactly "garden:water-cell"
    When the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 500
      """
    Then the resource state contains:
      | key    | value            |
      | status | READY_TO_HARVEST |
    And the manifest offers exactly "garden:harvest"
    When the client acts on "garden:harvest" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key    | value |
      | status | EMPTY |
    And the manifest offers exactly "garden:sow-seed"

    Examples:
      | rung |
      | L0   |
      | L1   |
      | L2   |

  Scenario Outline: Over CoAP, bed 1 through a full cycle at rung <rung>, with nothing sent over HTTP
    SPEC §6.0.1. The same server, the same client core, a second protocol:
    the bed's coap: URI is reached through the CoAP binding, over UDP.
    Given the client works at rung <rung>
    When the client enters "/beds/1" over CoAP
    Then the manifest offers exactly "garden:sow-seed"
    When the client acts on "garden:sow-seed" with:
      """yaml
      seedType: basil
      depthCm: 2
      """
    And the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 500
      """
    And the client acts on "garden:harvest" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key    | value |
      | status | EMPTY |
    And the manifest offers exactly "garden:sow-seed"
    And the current representation is at rung <rung>
    And nothing went over HTTP

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: Over CoAP, every event names the span of the request it is about
    SPEC §6.0.1: the CoAP binding carries traceparent as an option, so an
    event about a CoAP request ties to that request as it does over HTTP.
    Given the client keeps an event log
    When the client enters "/beds/1" over CoAP
    And the client acts on "garden:sow-seed" with:
      """yaml
      seedType: basil
      depthCm: 2
      """
    Then every event names a span

  Scenario: Over coaps, from the bookmark alone: look a bed up by its id and act
    The same service, reached beyond loopback over DTLS with ARTIE's
    pre-shared key for the coaps origin (ARTIE_COAPS_PORT, 5784 by default).
    When the client enters "/" over coaps
    And the client acts on "garden:get-bed" with:
      """yaml
      id: 2
      """
    Then the manifest offers exactly "garden:water-cell"
    When the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 1
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    And nothing went over HTTP

  Scenario: Over CoAP, from the bookmark alone: look a bed up by its id and act
    When the client enters "/" over CoAP
    And the client acts on "garden:get-bed" with:
      """yaml
      id: 2
      """
    Then the manifest offers exactly "garden:water-cell"
    When the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 1
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    And nothing went over HTTP

  @needs-L2
  Scenario: At L2 a re-sync of an unchanged bed is one request, answered 304
    Given the client works at rung L2
    When the client enters "/beds/3"
    And the client enters "/beds/3"
    Then the wire carried 4 requests, the last answered 304
    And the manifest offers exactly "garden:harvest"

  @needs-L2
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
    And the client acts on "sowSeed" with:
      """yaml
      seedType: basil
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |
    When the client acts on "waterCell" with:
      """yaml
      milliliters: 500
      """
    And the client acts on "harvest" with:
      """yaml
      {}
      """
    Then the resource state contains:
      | key    | value |
      | status | EMPTY |

  Scenario: Someone else writes first: the act is refused as stale and dropped
    Given the client enters "/beds/2"
    And a second client enters "/beds/2"
    When the second client acts on "garden:water-cell" with:
      """yaml
      milliliters: 1
      """
    And the client tries to act on "garden:water-cell" with:
      """yaml
      milliliters: 1
      """
    Then the client raised a "StaleAct" refusal
    And the client now holds the same ETag as the second client

  Scenario: A body that fails the server's schema is refused with its reasons
    Given the client enters "/beds/2"
    When the client tries to act on "garden:water-cell" with:
      """yaml
      milliliters: 0
      """
    Then the client raised a "InvalidPayload" refusal
    And the client raised an error mentioning "milliliters"

  Scenario Outline: From the bookmark alone at rung <rung>: search by name, follow the result, act
    Given the client works at rung <rung>
    When the client enters "/"
    Then the manifest offers exactly "garden:search-beds, garden:get-bed, garden:build-bed"
    When the client acts on "garden:search-beds" with:
      """yaml
      name: BED-2
      """
    And the client follows the "item" link titled "bed-2"
    Then the manifest offers exactly "garden:water-cell"
    When the client acts on "garden:water-cell" with:
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

  Scenario: From the bookmark alone, build a new bed and sow it
    The bookmark's act brings a bed into being; ARTIE lands on the new bed
    and acts on it, with no URI of its own.
    When the client enters "/"
    And the client acts on "garden:build-bed" with:
      """yaml
      label: Herbs
      widthFt: 4
      lengthFt: 8
      """
    Then the resource state contains:
      | key      | value |
      | status   | EMPTY |
      | label    | Herbs |
      | widthFt  | 4     |
      | lengthFt | 8     |
    And the manifest offers exactly "garden:sow-seed"
    When the client acts on "garden:sow-seed" with:
      """yaml
      seedType: thyme
      """
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |

  Scenario: From the bookmark alone: look a bed up by its id
    When the client enters "/"
    And the client acts on "garden:get-bed" with:
      """yaml
      id: 3
      """
    Then the manifest offers exactly "garden:harvest"

  Scenario: From the bookmark alone in HAL-FORMS: search by name, follow the result, act
    Given the client works in dialect hal-forms
    When the client enters "/"
    Then the manifest offers exactly "searchBeds, getBed, buildBed"
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

  Scenario: The client discovers the rung the server serves, works at it, and acts
    The server is started with HEART_MAX_RUNG at L0, L1 or L2 (L2 by
    default); the same variable tells this scenario what to expect.
    Given the client works at rung L2
    When the client discovers "/"
    And the client enters "/beds/2"
    Then the client asked for the rung the server serves when it entered "/beds/2"
    And the current representation is at the rung the server serves
    And the manifest offers exactly "garden:water-cell"
    When the client acts on "garden:water-cell" with:
      """yaml
      milliliters: 1
      """
    Then the current representation is at the rung the server serves
    And the manifest offers exactly "garden:water-cell"

  Scenario: A search is paged by next links, and following them reaches every bed it counts
    SPEC §6.11. The page size is the server's (HEART_PAGE_SIZE); start it
    with HEART_PAGE_SIZE=2 to see more than one page. ARTIE follows next by
    relation and builds no URI.
    When the client enters "/"
    And the client acts on "garden:search-beds" with:
      """yaml
      name: bed
      """
    Then following "next" from the search reaches every item it counts

  Scenario Outline: The server explains why an act is not offered, at rung <rung>
    SPEC §6.12. Whatever state bed 2 is in, sowing is not offered (the
    seed data plants it, and no scenario harvests it), and the server says why.
    Given the client works at rung <rung>
    When the client enters "/beds/2"
    Then the client has an explanation for "garden:sow-seed"

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: From the bookmark alone in HAL-FORMS: look a bed up by its id through the query
    Given the client works in dialect hal-forms
    When the client enters "/"
    And the client acts on "getBed" with:
      """yaml
      id: 3
      """
    And the client follows the "item" link titled "bed-3"
    Then the resource state contains:
      | key    | value            |
      | status | READY_TO_HARVEST |

  @llm
  Scenario: A real model waters a bed from a plain request, through the real server
    SPEC §6.8. ARTIE's Reason step is a real Claude call; everything around
    it stays deterministic. The model is shown the real server's offers,
    with their relation names, titles and descriptions, and no URI or
    method. ARTIE checks its pick and sends the act, and the server applies
    it. The last step reads the bed back, so the act is known to have
    happened. Runs only under llmTest, with an Anthropic API key.
    Given the reasoner is Claude, using the ANTHROPIC_API_KEY environment variable
    When the agent handles "please water bed 2 with 250 ml" at "/beds/2"
    Then the agent acted on "garden:water-cell" with milliliters 250
    And the resource state contains:
      | key    | value            |
      | status | READY_TO_HARVEST |
    And the server's history of bed 2 ends with "CellWatered" of 250 ml
