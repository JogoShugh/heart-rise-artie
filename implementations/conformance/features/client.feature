Feature: What a HEART client must do, against a real HEART service
  The client side of HEART conformance, written from the specification and
  the Internet-Draft. Any client can be held to it; heart-ts is the first
  run against it. The service is the reference garden: bed 1 is empty, bed 2
  is planted with tomato at moisture 40, and every request needs the garden's
  credential. Nothing here names a URI the client should build: it enters at
  the bookmark and goes on from what each representation offers.

  Background:
    Given the garden is at its seed state

  Scenario Outline: From the bookmark, find bed 2 and read what it offers, at <rung>
    Given a client at rung <rung> with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    Then the state's "status" is "PLANTED"
    And the representation is at rung <rung>
    And it offers exactly "garden:water-cell"
    And every shared layer it fetched matched the integrity of its link

    Examples:
      | rung |
      | L0   |
      | L2   |

  Scenario: An act is conditional, and its answer is the resource's new state
    Given a client at rung L2 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And it submits "garden:water-cell" with {"milliliters": 250}
    Then the act carried If-Match with the validator it read
    And the state's "moistureLevel" is 65
    And it offers exactly "garden:harvest"

  Scenario: When someone else acts first, the act is stale: re-read, never resent
    Given a client at rung L0 with the garden's credential
    And another client at rung L0 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And the other client waters bed 2 with 100 ml
    And it tries to submit "garden:water-cell" with {"milliliters": 100}
    Then it was refused as "stale" with status 412
    And the water act was sent once
    And the state's "moistureLevel" is 50

  Scenario Outline: A body the server refuses is told apart from one a rule refuses: <case>
    Given a client at rung L0 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And it tries to submit "garden:water-cell" with <body>
    Then it was refused as "<kind>" with status 422
    And the refusal says "<says>"

    Examples:
      | case                | body                  | kind          | says                                              |
      | breaks the schema   | {"milliliters": 0}    | invalid-input | milliliters                                       |
      | refused by a rule   | {"milliliters": 700}  | rule-refused  | The bed would be waterlogged: at most 600 ml now. |

  Scenario: An act the representation does not offer is refused before anything is sent
    Given a client at rung L0 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 1}
    And it tries to submit "garden:harvest" with {}
    Then it was refused before sending, because "garden:harvest is not offered now"

  Scenario: A link is followed by its relation and title
    Given a client at rung L0 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:search-beds" with {"name": "bed-2"}
    And it follows the "item" link titled "bed-2"
    Then the state's "label" is "bed-2"

  Scenario: Without a credential, the service refuses
    Given a client at rung L0 with no credential
    When it tries to enter at the bookmark
    Then it was refused as "not-authenticated" with status 401

  Scenario: A shared layer whose bytes do not match its link's integrity is refused
    Given a service whose affordance set does not match the integrity of its link
    And a client at rung L2 with no credential
    When it tries to enter that service
    Then it refused the affordance set for its integrity
