Feature: Cross-dialect legality (L5)
  SPEC §6.10.6. The same bed, negotiated as HAL-FORMS and as HAL Schema
  Forms, gives different bytes but the same answer to "which acts are
  legal right now". Both renderings come from one source of truth (§6.2).

  Scenario Outline: Both dialects offer the same acts in every state
    Given bed 2 is <status>
    When a client gets bed 2 as hal-forms with Prefer ""
    And a client gets bed 2 as hal-schema-forms with Prefer ""
    Then both dialects offer exactly the act targets <targets>
    And the two responses have different ETags

    Examples:
      | status           | targets                        |
      | EMPTY            | /beds/2/actions/sow-seed       |
      | PLANTED          | /beds/2/actions/water-cell     |
      | READY_TO_HARVEST | /beds/2/actions/harvest        |
