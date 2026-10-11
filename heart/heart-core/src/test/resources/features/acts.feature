Feature: An act is checked in SPEC order, then performed
  SPEC §6.2. heart-core checks every act of every declared resource type the
  same way, whatever its binding: no validator is 428, a stale validator is
  412, an act not offered now is 409, and a body that breaks the act's schema
  is 422. The first failure wins, and a refused act changes nothing. A write
  that lands first while the act runs makes the act stale too.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamp 7 is "ON" at version 3

  Scenario Outline: The first failing check decides: <case>
    When a client acts on lamp 7 with "<act>", validator <validator>, and body <body>
    Then the act is refused as <refusal>
    And lamp 7 is still "ON" at version 3

    Examples:
      | case                                        | act      | validator | body               | refusal                |
      | no validator, whatever else is wrong        | switchOn | none      | {"brightness": 0}  | precondition required  |
      | stale, though also not offered              | switchOn | stale     | {}                 | stale                  |
      | not offered, though the body is also bad    | switchOn | current   | {"x": "y", "brightness": "no"} | not offered "home:switch-on" |
      | offered, but the body breaks the schema     | dim      | current   | {"brightness": 0}  | invalid at "/brightness" |
      | offered, but the body is not JSON           | dim      | current   | {"brightness":     | malformed              |

  Scenario: A checked act is performed with its body bound to the act's input class
    When a client acts on lamp 7 with "dim", validator current, and body {"brightness": 20}
    Then the act was performed
    And lamp 7 is "ON" with brightness 20 at version 4

  Scenario: A write that lands first while the act runs makes the act stale
    Given someone else writes to lamp 7 while the next act runs
    When a client acts on lamp 7 with "switchOff", validator current, and body {}
    Then the act is refused as stale
