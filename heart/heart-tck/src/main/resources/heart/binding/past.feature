Feature: A resource's past, when it has one
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, step A5.8. A resource whose state
  is folded from events can also say what it was at any past instant, and
  what happened to it, in order. The HTTP binding serves both for any
  declared type that supplies them: the state as of an instant at
  /{collection}/{id}?asOf=, rendered exactly like the current state, and the
  events at /{collection}/{id}/history.

  Background:
    Given lamp 7 recorded these events in the lamp server
      | event      | brightness | days ago |
      | SwitchedOn |            | 3        |
      | Dimmed     | 30         | 1        |

  Scenario: The history lists what happened, in order
    When a client gets "/lamps/7/history"
    Then the response status is 200
    And the history is
      | number | type       |
      | 1      | SwitchedOn |
      | 2      | Dimmed     |
    And the last event's "brightness" is 30

  Scenario: The state as of a past instant reads exactly like the current state
    When a client gets lamp 7 as hal-schema-forms as of 2 days ago
    Then the response status is 200
    And the response body has "brightness" 50
    And the response has ETag "\"seq-1.hsf.L0.{digest}\""
    And the response offers
      | form            |
      | home:switch-off |
      | home:dim        |

  Scenario: An instant that is not ISO 8601 is a bad request
    When a client gets "/lamps/7?asOf=yesterday" as hal-schema-forms
    Then the response status is 400

  Scenario: A lamp the server does not hold has no history
    When a client gets "/lamps/99/history"
    Then the response status is 404
