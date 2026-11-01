Feature: One resource in two dialects (L5)
  The dialects differ in every byte and must agree on what is legal now (SPEC
  §6.10.6). Each bed in the seed garden is in a different state, so each offers a
  different act.

  Scenario Outline: Both dialects say the same of bed <bed>
    Given the garden is at its seed state
    And a client at rung L0 with the garden's credential
    And another HAL-FORMS client with the garden's credential
    When each client reads bed <bed> in its own dialect
    Then both hold the same state
    And both offer the act "<act>", by the same method

    Examples:
      | bed | act        |
      | 1   | sow-seed   |
      | 2   | water-cell |
      | 3   | harvest    |
