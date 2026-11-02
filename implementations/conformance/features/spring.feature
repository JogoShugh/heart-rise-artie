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
