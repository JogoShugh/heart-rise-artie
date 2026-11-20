Feature: Every HEART identifier the garden sends leads to a page
  The profile, every relation, every problem type and every variable a HEART
  response names is a URI under https://w3id.org/heart-rise/. Each one must
  lead somewhere a person or a program can read what it means: w3id.org
  redirects to the identifier site, built into docs/ from
  standards/identifiers.json. This scenario gathers every kind of response
  the garden gives and checks that each identifier in them has its page.

  Scenario: Every identifier in every kind of response has a page
    When a client gathers every kind of response the garden gives
    Then every HEART identifier in them has a page in the identifier site
    And the identifiers it found include:
      | identifier                                              |
      | https://w3id.org/heart-rise                             |
      | https://w3id.org/heart-rise/hal-schema-forms            |
      | https://w3id.org/heart-rise/hal-forms                   |
      | https://w3id.org/heart-rise/rels/forms                  |
      | https://w3id.org/heart-rise/rels/unavailable            |
      | https://w3id.org/heart-rise/rels/form                   |
      | https://w3id.org/heart-rise/problems/stale              |
      | https://w3id.org/heart-rise/problems/not-offered        |
      | https://w3id.org/heart-rise/problems/invalid-input      |
      | https://w3id.org/heart-rise/problems/rule-refused       |
      | https://w3id.org/heart-rise/problems/precondition-required |
      | https://w3id.org/heart-rise/problems/not-authenticated  |
      | https://w3id.org/heart-rise/spec                        |
      | https://w3id.org/heart-rise/vars/bed-id                 |
