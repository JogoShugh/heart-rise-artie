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
    Then the body has these members
      | member | value |
      | status | ON    |
      | id     | 7     |
    And the body links "self" to "/lamps/7"
    And the body offers exactly
      | act             |
      | home:switch-off |
      | home:dim        |
    And the "home:dim" form targets "/lamps/7/actions/dim" with POST
    And the "home:dim" form embeds its schema, with its address as "$id"
    And the body links "hr:unavailable" to an explanation, with its integrity

  Scenario Outline: L<n> links to the shared affordance set for its rung
    When lamp 7 is rendered at L<n>
    Then the body has no forms
    And the body links "hr:forms" to an affordance set, with its integrity, type and profile
    When the affordance set is fetched
    Then its bytes hash to its address and match the link's integrity
    And the set offers exactly
      | act             |
      | home:switch-off |
      | home:dim        |
    And the "home:dim" form targets "/lamps/{id}/actions/dim", templated, with the pointer "id" at "/id"
    And the "home:dim" form <schema>

    Examples:
      | n | schema                                             |
      | 1 | embeds its schema, with its address as "$id"       |
      | 2 | refers to its schema by "$ref", with its integrity |

  Scenario: Each form carries its own integrity, over what it does, the same at every rung
    A replay compares the form it used with the form offered now: lenient, it
    acts when that form is unchanged in a set that changed
    (spec/ideas/replay-guards-lenient-strict.md). A form's hr:integrity is the
    integrity of these members, in canonical form (RFC 8785): its target
    template, its template pointers, its method, its content type, and its
    schema's integrity. Not its title or description: a retitled form does the
    same.
    Then the "home:dim" form's integrity is the same at L0, L1 and L2
    And the "home:dim" form's integrity is that of these members, canonical
      | member              | value                                  |
      | target              | /lamps/{id}/actions/dim                |
      | hr:templatePointers | {"id": "/id"}                          |
      | method              | POST                                   |
      | contentType         | application/json                       |
      | schema              | the integrity of the "home:dim" schema |
    And the forms "home:dim" and "home:switch-off" have different integrities

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
