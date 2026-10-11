Feature: An event store for any declared resource type
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, step A5.8. A resource's state is
  the fold of its events, in order. The store keeps one stream per resource,
  numbers each event, and refuses a second event at a number already taken,
  so two writers cannot both win. It can also fold a stream as of a past
  instant, and give the stream itself. The same scenarios run against the
  in-memory store and against Postgres.

  Scenario Outline: Events fold into state, in order (<store>)
    Given the <store> event store
    When lamp 7 records SwitchedOn, then Dimmed to 30, then Dimmed to 60
    Then lamp 7's state is "ON" at brightness 60, version 3
    And lamp 7's history is "SwitchedOn, Dimmed, Dimmed", numbered 1 to 3

    Examples:
      | store     |
      | in-memory |
      | Postgres  |

  Scenario Outline: A second writer at a number already taken is refused (<store>)
    Given the <store> event store
    And lamp 7 records SwitchedOn
    When two writers both record an event as lamp 7's number 2
    Then exactly one is stored, and the other is refused as a concurrent write
    And lamp 7's version is 2

    Examples:
      | store     |
      | in-memory |
      | Postgres  |

  Scenario Outline: State as of a past instant folds only the events up to it (<store>)
    Given the <store> event store
    And lamp 7 recorded:
      | event      | brightness | days ago |
      | SwitchedOn |            | 3        |
      | Dimmed     | 30         | 2        |
      | Dimmed     | 90         | 1        |
    When lamp 7's state is read as of 36 hours ago
    Then lamp 7's state is "ON" at brightness 30, version 2

    Examples:
      | store     |
      | in-memory |
      | Postgres  |

  Scenario Outline: A snapshot shortens the fold and never changes the answer (<store>)
    Given the <store> event store, snapshotting every 5 events
    When lamp 7 records SwitchedOn and then 11 Dims, the last to 12
    Then lamp 7's state is "ON" at brightness 12, version 12
    And the state read from the latest snapshot equals the state folded from the first event

    Examples:
      | store     |
      | in-memory |
      | Postgres  |

  Scenario Outline: A resource with no events is in its empty state (<store>)
    Given the <store> event store
    Then lamp 8's state is "OFF" at brightness 0, version 0

    Examples:
      | store     |
      | in-memory |
      | Postgres  |
