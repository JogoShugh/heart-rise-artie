Feature: An act the state no longer offers is replayed when it repeats one that took effect
  SPEC §6.2, decision 8. A repeat with the same body is answered as the first was, so a retry after a
  lost answer is safe. The same act with another body is still refused: the first one stands.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamp 7 is "ON" at version 3
    And lamp 7 was switched on with the body "{}"

  Scenario: A repeat with the same body is replayed, not refused
    When a client acts on lamp 7 with "switchOn", validator current, and body {}
    Then the act was performed

  Scenario: A repeat with another body is still refused as not offered
    When a client acts on lamp 7 with "switchOn", validator current, and body {"brightness": 0}
    Then the act is refused as not offered "home:switch-on"
