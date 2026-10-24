Feature: Open offers: offer keys first seen at run time
  A domain server lists every offer key its type has, and heart-core renders
  each key's affordance sets and explanation once, at start. A server that
  learns its acts at run time, such as a gateway in front of another API,
  can't list its keys up front: a key is whichever set of acts the upstream
  allows a resource now. Its offers are open. heart-core renders a key's
  content the first time a state with that key is rendered, and from then on
  serves it from the same shared place, named by content, as any other.

  Background:
    Given a lamp type whose offers are open, with no keys declared

  Scenario: A state whose key was never declared is rendered, and its set is served by its address
    When a lamp in the key "ON" is rendered at L1
    Then the server holds the affordance set its "hr:forms" link names, matching the link's integrity
    And that set offers exactly
      | act             |
      | home:switch-off |
      | home:dim        |

  Scenario: Two resources with one new key share one affordance set
    When a lamp in the key "ON" is rendered at L1
    And another lamp in the key "ON" is rendered at L1
    Then both link the same affordance set
    And the server holds 1 affordance set for each rendering

  Scenario: Why acts are absent is held for a new key too
    When a lamp in the key "OFF" is rendered at L0
    Then the server holds the explanation its "hr:unavailable" link names
    And the explanation says "home:dim" is absent because "The lamp is off; switch it on."

  Scenario: A new key that offers an act the type doesn't declare is refused when it is first seen
    When a lamp in the key "BROKEN" is rendered at L1
    Then rendering it is refused, saying key BROKEN offers undeclared acts [explode]

  Scenario: A type with closed offers still knows only its declared keys
    Given the lamp type as declared, with closed offers
    Then the server holds 4 affordance sets for it before any lamp is rendered
