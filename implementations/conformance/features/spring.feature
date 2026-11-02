Feature: A Spring HATEOAS service, as it ships
  The Spring service answers as Spring HATEOAS's own affordances example
  (spring-projects/spring-hateoas-examples) does, a service that knows nothing of
  HEART: HAL-FORMS only, its templates' methods in lower case, no profile, no ETag,
  and every act answered 204 with no body. Its employees are Frodo, ring bearer,
  and Bilbo, burglar. A HEART client meets it in HAL-FORMS, by what it offers.

  Scenario: A HEART client walks every affordance, by what the service offers
    Given a client asking in HAL-FORMS for the Spring service
    When it enters the employees of the Spring service
    Then it offers exactly these acts
      | default |
    When it submits "default" with {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}
    Then it embeds these employees
      | firstName | role        |
      | Frodo     | ring bearer |
      | Bilbo     | burglar     |
      | Samwise   | gardener    |
    When it follows "employees" with no title
    Then it offers exactly these acts
      | default        |
      | deleteEmployee |
    When it submits "default" with {"firstName": "Frodo", "lastName": "Baggins", "role": "ring bearer, retired"}
    Then the state's "role" is "ring bearer, retired"
    When it submits "deleteEmployee" with {}
    Then the client holds "/employees/1", gone
    And the Spring service holds these employees
      | firstName |
      | Bilbo     |
      | Samwise   |

  Scenario: Asked in HEART's reference dialect, it names no profile, and a HEART client takes nothing from it
    Given a client asking in HAL Schema Forms for the Spring service
    When it tries to enter the employees of the Spring service
    Then it failed, saying "does not name the profile"

  Scenario Outline: The same employees, served as a HEART service, walked alike in <dialect>
    Switched to HEART, the Spring service keeps the same employees at the same
    addresses, served by heart-core: a profile, ETags, and both dialects. A new
    employee comes from hiring, which its bookmark offers; a dismissed one stays,
    with nothing more to offer.
    Given the Spring service answers as a HEART service
    And a client asking in <dialect> for the Spring service
    When it enters the Spring service at its bookmark
    And it submits "<hire>" with {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}
    Then the client holds "/employees/3"
    And it offers exactly these acts
      | <update>  |
      | <dismiss> |
    When it submits "<update>" with {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor"}
    Then the state's "role" is "gardener, mayor"
    When it submits "<dismiss>" with {}
    Then the state's "status" is "dismissed"
    And the Spring service holds these employees
      | firstName |
      | Frodo     |
      | Bilbo     |

    Examples:
      | dialect          | hire       | update       | dismiss       |
      | HAL-FORMS        | hire       | update       | dismiss       |
      | HAL Schema Forms | staff:hire | staff:update | staff:dismiss |
