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

  Scenario Outline: A server that takes acts with no validator, a development switch: <case>
    spec/ideas/off-the-rails.md. For a fixture or a trial only, never by
    default: a server may take an act sent with no validator, as an API
    without HEART does. The act is still checked against the state now: not
    offered is still 409, a bad body still 422. A validator sent is still
    compared.
    Given the server takes acts with no validator
    When a client acts on lamp 7 with "<act>", validator <validator>, and body <body>
    Then <outcome>

    Examples:
      | case                         | act      | validator | body               | outcome                                             |
      | offered: performed           | dim      | none      | {"brightness": 20} | the act was performed                               |
      | not offered: still 409       | switchOn | none      | {}                 | the act is refused as not offered "home:switch-on"  |
      | a bad body: still 422        | dim      | none      | {"brightness": 0}  | the act is refused as invalid at "/brightness"      |
      | a validator sent is compared | dim      | stale     | {"brightness": 20} | the act is refused as stale                         |

  Scenario Outline: A refusal's detail names the act by its relation, and says why it is not offered: <case>
    The detail is for a person reading the problem. It names the act as
    the forms do, never by a name in the code, and gives the same reason
    the explanation of absent acts gives, never an internal key.
    When a client acts on lamp 7 with "<act>", validator <validator>, and body {}
    Then the refusal's detail is "<detail>"

    Examples:
      | case         | act      | validator | detail                                                                        |
      | no validator | switchOn | none      | home:switch-on needs a validator: the current one of the resource offering it |
      | not offered  | switchOn | current   | home:switch-on is not offered now: The lamp is already on.                    |

  Scenario: A checked act is performed with its body bound to the act's input class
    When a client acts on lamp 7 with "dim", validator current, and body {"brightness": 20}
    Then the act was performed
    And lamp 7 is "ON" with brightness 20 at version 4

  Scenario: A write that lands first while the act runs makes the act stale
    Given someone else writes to lamp 7 while the next act runs
    When a client acts on lamp 7 with "switchOff", validator current, and body {}
    Then the act is refused as stale
