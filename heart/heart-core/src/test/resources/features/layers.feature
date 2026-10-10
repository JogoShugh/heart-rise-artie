Feature: A declared resource type is rendered in three layers
  SPEC §6.4, §6.5, §6.9 and §6.12. A server declares a resource type once:
  its relations, its acts with their schemas, which acts each offer key
  allows, and why the others are absent. heart-core renders every layer from
  that declaration, for any domain. The lamp here is declared in the test
  alone: it is on or off, can be switched, and can be dimmed while on.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamp 7 is "ON" at version 3

  Scenario: L0 collapses state, forms and full schemas into Layer 1
    When lamp 7 is rendered at L0
    Then the body has "status" "ON" and "id" 7
    And the body links "self" to "/lamps/7"
    And the body offers exactly "home:switch-off, home:dim"
    And the "home:dim" form targets "/lamps/7/actions/dim" with POST
    And the "home:dim" form embeds its schema, with its address as "$id"
    And the body links "hr:unavailable" to an explanation, with its integrity

  Scenario Outline: L<n> links to the shared affordance set for its rung
    When lamp 7 is rendered at L<n>
    Then the body has no forms
    And the body links "hr:forms" to an affordance set, with its integrity, type and profile
    When the affordance set is fetched
    Then its bytes hash to its address and match the link's integrity
    And the set offers exactly "home:switch-off, home:dim"
    And the "home:dim" form targets "/lamps/{id}/actions/dim", templated, with the pointer "id" at "/id"
    And the "home:dim" form <schema>

    Examples:
      | n | schema                                             |
      | 1 | embeds its schema, with its address as "$id"       |
      | 2 | refers to its schema by "$ref", with its integrity |

  Scenario: Two lamps with the same offer key share every Layer 2 resource
    Given lamp 8 is "ON" at version 12
    Then lamps 7 and 8 link to the same affordance set at L1 and at L2

  Scenario: The explanation says why each absent act is absent, in that key
    When lamp 7 is rendered at L0
    And the explanation is fetched
    Then it explains "home:switch-on" as "The lamp is already on."
    And it explains nothing else

  Scenario: Every Layer 2 and Layer 3 resource is served by its address
    Then every affordance set, schema and explanation of the lamp type is listed by its address
    And each listed body hashes to its address
