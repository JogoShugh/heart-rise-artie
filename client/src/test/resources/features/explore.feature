Feature: ARTIE explores any HEART server from its bookmark
  Fielding's rule 6: enter with nothing but the bookmark, and from then on
  choose only among what the server offers. The explorer does exactly that,
  for any domain: it reads the bookmark, runs the search the samples name,
  follows the item it finds, and then, again and again, takes the first act
  the resource offers that the samples still have a body for. It stops when
  no offered act has a sample left, or at its step limit. The samples are the
  only domain knowledge it has: example bodies, never URIs or orders.

  Background:
    Given a lamp service whose bookmark searches lamps by name, with lamp 7 off

  Scenario: Search, follow the item, then take offered acts in sample order
    Given explore samples searching "lamp 7" with acts:
      | rel            | bodies                                  |
      | home:switch-on | {}                                      |
      | home:dim       | {"brightness": 30}; {"brightness": 60}  |
      | home:switch-off| {}                                      |
    When ARTIE explores the lamp service
    Then it took the acts "home:switch-on, home:dim, home:dim, home:switch-off"
    And it stopped because "no offered act has a sample left"

  Scenario: An act the resource does not offer is never sent, even with a sample
    Given explore samples searching "lamp 7" with acts:
      | rel             | bodies |
      | home:switch-off | {}     |
    When ARTIE explores the lamp service
    Then it took no acts
    And it stopped because "no offered act has a sample left"

  Scenario: The explorer stops at its step limit
    Given explore samples searching "lamp 7", at most 2 steps, with acts:
      | rel            | bodies                                                    |
      | home:switch-on | {}                                                        |
      | home:dim       | {"brightness": 10}; {"brightness": 20}; {"brightness": 30} |
    When ARTIE explores the lamp service
    Then it took the acts "home:switch-on, home:dim"
    And it stopped because "the step limit"

  Scenario: Samples are read from a JSON file a domain keeps
    When the explore samples are read from:
      """json
      {"search": {"rel": "home:search-lamps", "fields": {"name": "lamp 7"}, "item": "Lamp 7"},
       "maxSteps": 9,
       "acts": {"home:switch-on": [{}], "home:dim": [{"brightness": 30}]}}
      """
    Then the samples search with "home:search-lamps" for the item "Lamp 7", at most 9 steps
    And the samples have 1 body for "home:switch-on" and 1 for "home:dim", in that order
