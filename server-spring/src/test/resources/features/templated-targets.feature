Feature: Templated affordance targets
  SPEC §6.9.1 and HAL-SCHEMA-FORMS-HR Rule 1. Layer 2 hrefs are RFC 6570
  Level 1 templates, so one affordance set serves every bed in a state.
  Each form declares, in hr:templatePointers, a JSON Pointer into Layer 1
  for every variable, so every conformant client expands a template to
  the same URI, and the URI never depends on a client guessing. That URI
  is where the server accepts the act.

  Scenario Outline: Every template variable has a declared pointer that resolves in Layer 1: <status>
    Given bed 2 is <status>
    When a client gets bed 2 with Prefer "forms=link, schema=link"
    And the client follows the affordance-set link
    Then every target is a template whose variables each have a template pointer into bed 2

    Examples:
      | status           |
      | EMPTY            |
      | PLANTED          |
      | READY_TO_HARVEST |

  Scenario: The expanded template is the URI the act is accepted at
    Given bed 2 is PLANTED
    And a client got bed 2 as hal-schema-forms with Prefer "forms=link, schema=link"
    When the client follows the affordance-set link
    And the client expands "garden:water-cell" from bed 2 by its template pointers and posts {"milliliters": 100} with its ETag
    Then the response status is 200

  Scenario: Two beds in the same state share one affordance set
    Given bed 1 is PLANTED
    And bed 3 is PLANTED
    When a client gets bed 1 with Prefer "forms=link, schema=link"
    And a client gets bed 3 with Prefer "forms=link, schema=link"
    Then the two affordance-set links are identical
