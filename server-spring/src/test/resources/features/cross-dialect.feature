Feature: Cross-dialect legality (L5)
  SPEC §6.10.6. The same bed, negotiated as HAL-FORMS and as HAL Schema
  Forms, gives different bytes but the same answer to "which acts are
  legal right now". Both renderings come from one source of truth (§6.2).

  Scenario Outline: Both dialects offer the same acts in every state: <status>
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

  Scenario Outline: HAL-FORMS is the L0 on-ramp: it ignores layering preferences: Prefer "<prefer>"
    Given bed 2 is PLANTED
    When a client gets bed 2 as hal-forms with Prefer "<prefer>"
    Then the response carries its forms under "_templates"
    And the response has no Preference-Applied header
    And the response ETag is "seq-1.hf.L0"

    Examples:
      | prefer                |
      | forms=cid             |
      | forms=cid, schema=cid |
