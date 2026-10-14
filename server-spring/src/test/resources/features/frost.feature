@tuesday
Feature: Tuesday: a hard frost can freeze a planted bed
  From OpenAPI to HEART, part three. Overnight the garden team ships a
  change: a hard frost can freeze a planted bed. Watering a frozen bed would
  split the roots, so it is not offered; a new act, thaw, covers the bed with
  fleece and returns it to planted. Only the server changes. These scenarios
  run against the garden with garden.frost.enabled=true (RunTuesdayTest);
  every other feature runs on Monday, with no frost, where nothing changed.

  Scenario: The frost freezes every planted bed, and only those
    Given bed 1 is EMPTY
    And bed 2 is PLANTED
    And bed 3 is READY_TO_HARVEST
    When the frost hits
    Then bed 1 is "EMPTY" with no crop and growth 0
    And bed 2 is "FROZEN" with crop "basil", growth 0 and moisture 0
    And bed 3 is "READY_TO_HARVEST" with crop "basil", growth 100 and moisture 50

  Scenario: A frozen bed offers only thaw, and says why it cannot be watered
    Given bed 2 is FROZEN
    When a client reads bed 2
    Then the bed offers exactly "garden:thaw"
    And "garden:water-cell" is absent because "The bed is frozen; watering now would split the roots. Thaw it first."
    And "garden:sow-seed" is absent because "The bed is frozen with a crop in it; thaw it first."
    And "garden:harvest" is absent because "The crop is frozen and not ready; thaw it first."

  Scenario: Thawing covers the bed with fleece, and it can be watered again
    Given bed 2 is FROZEN
    When a client posts {} to thaw on bed 2 with If-Match *
    Then bed 2 is "PLANTED" with crop "basil", growth 0 and moisture 0
    And the bed offers exactly "garden:water-cell"
    And "garden:thaw" is absent because "The bed is not frozen."

  Scenario: A client that read the bed before the frost acts on a stale state, and is told so
    Given bed 2 is PLANTED
    And a client read bed 2
    When the frost hits
    And the client posts {"milliliters": 250} to water-cell on bed 2 with the ETag it read
    Then the answer is 412
    And bed 2 is "FROZEN" with crop "basil", growth 0 and moisture 0

  Scenario: A plain JSON client, which needs no ETag, learns the rule by being refused
    Given bed 2 is FROZEN
    When a plain JSON client posts {"milliliters": 250} to water-cell on bed 2
    Then the answer is 409
    And bed 2 is "FROZEN" with crop "basil", growth 0 and moisture 0

  Scenario: Tuesday's OpenAPI description is the published version 1.1.0, byte for byte
    When a client gets the OpenAPI description
    Then it is the published "garden-1.1.0.json"
    And its info version is "1.1.0"
    And its "Bed" status enum is "EMPTY, PLANTED, READY_TO_HARVEST, FROZEN"
    And its operations include "POST /beds/{id}/actions/thaw"
    And the description of "waterCell" ends "Only allowed when status is PLANTED."
    And the description of "thaw" ends "Only allowed when status is FROZEN."
