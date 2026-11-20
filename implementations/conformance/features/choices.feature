Feature: A form's choices from state
  HAL-SCHEMA-FORMS-HR Rule 8: a property may take its choices, with
  hr:options, from the resource that offers the form, at a JSON Pointer that
  may name another field of the form, or from another resource, read like
  any Layer 1 resource. Only items whose members equal every one of "where"
  are choices; a client offers only those, and sends no value outside them.
  Here the deploy service's "cd:deploy" takes its region from the app's own
  regions for the environment chosen, where available, and its release from
  the releases list, where it succeeded.

  Background:
    Given the deploy service's app "web" has the regions
      | environment | name       | label      | available |
      | staging     | us-east    | US East    | true      |
      | staging     | eu-west    | EU West    | false     |
      | staging     | us-west    | US West    | true      |
      | production  | eu-central | EU Central | true      |
    And the deploy service has the releases
      | app | version | status    |
      | web | 1.4.0   | succeeded |
      | web | 1.5.0   | succeeded |
      | web | 1.6.0   | failed    |
    And a client at rung L0 with the deploy service's credential
    When it enters the app "web"

  Scenario: A choice that depends on another field: the chosen environment's available regions
    Then the choices for "region" of "cd:deploy", by "environment", are
      | environment | value      | prompt     |
      | staging     | us-east    | US East    |
      | staging     | us-west    | US West    |
      | production  | eu-central | EU Central |

  Scenario: Choices from another resource, its address filled from the app's state
    Then the choices for "release" of "cd:deploy" are
      | value | prompt        |
      | 1.4.0 | Release 1.4.0 |
      | 1.5.0 | Release 1.5.0 |
    And the releases were read at "/releases?app=web"

  Scenario: The other resource is revalidated, not read again in full
    When it resolves the choices for "release" of "cd:deploy" twice
    Then the second read of the releases carried the ETag of the first, and was answered 304
    And the second answer held the same choices as the first

  Scenario Outline: A value outside the choices is refused before anything is sent: <case>
    When it tries to submit "cd:deploy" with <body>
    Then it was refused before sending, because "<says>"

    Examples:
      | case                              | body                                                                   | says                                          |
      | a region not available            | {"environment": "staging", "region": "eu-west", "release": "1.5.0"}    | region: eu-west is not one of the choices     |
      | a region of the other environment | {"environment": "production", "region": "us-east", "release": "1.5.0"} | region: us-east is not one of the choices     |
      | a release that failed             | {"environment": "staging", "region": "us-east", "release": "1.6.0"}    | release: 1.6.0 is not one of the choices      |

  Scenario: A value among the choices is sent
    When it submits "cd:deploy" with {"environment": "staging", "region": "us-west", "release": "1.5.0"}
    Then the act "cd:deploy" was sent 1 times
