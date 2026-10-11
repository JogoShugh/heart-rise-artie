Feature: Garden beds
  A bed is one resource, /beds/{id}. Its state says what is planted, how far
  it has grown and how moist it is. Its forms say what may be done now, and
  nothing else: an empty bed is sown, a planted bed is watered until it is
  ready, and a ready bed is harvested, which empties it again.

  Only the bed's own rules are here. What every HEART server does (caching,
  validators, 428, 412, 409, 422 for a body its schema refuses, the layers,
  negotiation, search and paging) is tested once in the HEART library, and
  RunHeartTckTest runs the conformance features against this server.

  Scenario: An empty bed can only be sown, and says why it cannot be watered or harvested
    Given bed 1 is EMPTY
    When a client reads bed 1
    Then the bed offers exactly "garden:sow-seed"
    And "garden:water-cell" is absent because "Nothing is planted yet; sow a seed first."
    And "garden:harvest" is absent because "Nothing is growing yet; sow a seed and water it until it is ready."

  Scenario: Sowing plants the crop, and the bed can then only be watered
    Given bed 1 is EMPTY
    When a client posts {"seedType": "tomato", "depthCm": 2} to sow-seed on bed 1 with If-Match *
    Then bed 1 is "PLANTED" with crop "tomato", growth 0 and moisture 0
    And the bed offers exactly "garden:water-cell"
    And "garden:sow-seed" is absent because "The bed is already planted."
    And "garden:harvest" is absent because "The crop is not ready yet; it needs more water to grow."

  Scenario Outline: Watering grows the crop by a fifth of the water and moistens the bed by a tenth: <ml> ml
    Given bed 2 is PLANTED
    When a client posts {"milliliters": <ml>} to water-cell on bed 2 with If-Match *
    Then bed 2 is "<status>" with crop "basil", growth <growth> and moisture <moisture>

    Examples:
      | ml   | status           | growth | moisture |
      | 100  | PLANTED          | 20     | 10       |
      | 495  | PLANTED          | 99     | 49       |
      | 500  | READY_TO_HARVEST | 100    | 50       |
      | 1000 | READY_TO_HARVEST | 100    | 100      |

  Scenario: A ready bed can only be harvested, and says why it is not sown or watered
    Given bed 3 is READY_TO_HARVEST
    When a client reads bed 3
    Then the bed offers exactly "garden:harvest"
    And "garden:sow-seed" is absent because "The bed is still full; harvest the crop first."
    And "garden:water-cell" is absent because "The crop is ready; it needs harvesting, not more water."

  Scenario: Harvesting empties the bed, ready to be sown again
    Given bed 3 is READY_TO_HARVEST
    When a client posts {} to harvest on bed 3 with If-Match *
    Then bed 3 is "EMPTY" with no crop and growth 0
    And the bed offers exactly "garden:sow-seed"
