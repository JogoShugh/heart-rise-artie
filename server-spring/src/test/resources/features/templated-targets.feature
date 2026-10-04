Feature: Templated affordance targets
  SPEC §6.9.1 and its [0.5] resolution rule. Layer 2 hrefs are RFC 6570
  Level 1 templates, so one affordance set serves every bed in a state.
  Each variable names a top-level Layer 1 property, matched exactly, so
  every conformant client expands a template to the same URI. That URI
  is where the server accepts the act.

  Scenario Outline: Every template variable resolves from Layer 1: <status>
    Given bed 2 is <status>
    When a client gets bed 2 with Prefer "forms=cid, schema=cid"
    And the client follows the affordance-set link
    Then every target is a template whose variables are top-level properties of bed 2

    Examples:
      | status           |
      | EMPTY            |
      | PLANTED          |
      | READY_TO_HARVEST |

  Scenario: The expanded template is the URI the act is accepted at
    Given bed 2 is PLANTED
    And a client got bed 2 as hal-schema-forms with Prefer "forms=cid, schema=cid"
    When the client follows the affordance-set link
    And the client expands the "waterCell" target from bed 2 and posts {"milliliters": 100} with the ETag it holds
    Then the response status is 200

  Scenario: Two beds in the same state share one affordance set
    Given bed 1 is PLANTED
    And bed 3 is PLANTED
    When a client gets bed 1 with Prefer "forms=cid, schema=cid"
    And a client gets bed 3 with Prefer "forms=cid, schema=cid"
    Then the two affordance-set links are identical
