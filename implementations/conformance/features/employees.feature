Feature: The employees service, a HEART service that can be handicapped as Spring HATEOAS ships
  The employees service holds Spring HATEOAS's example employees, Frodo, ring
  bearer, and Bilbo, burglar, served by heart-core: a profile, ETags, and both
  dialects. A new employee comes from hiring, which its bookmark offers; a dismissed
  one stays, with nothing more to offer.

  Handicapped as Spring, it answers as Spring HATEOAS's own affordances example
  (spring-projects/spring-hateoas-examples) does, a service that knows nothing of
  HEART: HAL-FORMS only, its templates' methods in lower case, no profile, no ETag,
  and every act answered 204 with no body. A HEART client meets it in HAL-FORMS,
  by what it offers.

  Scenario Outline: A HEART client walks the employees in <dialect> at rung <rung>
    Given a client asking in <dialect> at rung <rung> for the employees service
    When it enters the employees service at its bookmark
    And it submits "<hire>" with {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}
    Then the client holds "/employees/3"
    And it offers exactly these acts
      | <dismiss> |
      | <update>  |
    When it submits "<update>" with {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor"}
    Then the state's "role" is "gardener, mayor"
    When it submits "<dismiss>" with {}
    Then the state's "status" is "dismissed"
    And the employees service holds these employees
      | firstName |
      | Frodo     |
      | Bilbo     |

    Examples:
      | dialect          | rung | hire       | update       | dismiss       |
      | HAL-FORMS        | L0   | hire       | update       | dismiss       |
      | HAL Schema Forms | L2   | staff:hire | staff:update | staff:dismiss |

  Scenario: Handicapped as Spring, it is walked by every affordance it offers
    Given the employees service is handicapped as Spring HATEOAS
    And a client asking in HAL-FORMS at rung L0 for the employees service
    When it enters the employees of the employees service
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
    And the employees service holds these employees
      | firstName |
      | Bilbo     |
      | Samwise   |

  Scenario: Handicapped as Spring and asked in HEART's reference dialect, it names no profile, and a HEART client takes nothing from it
    Given the employees service is handicapped as Spring HATEOAS
    And a client asking in HAL Schema Forms at rung L0 for the employees service
    When it tries to enter the employees of the employees service
    Then it failed, saying "does not name the profile"
