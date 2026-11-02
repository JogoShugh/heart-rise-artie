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

  Scenario Outline: Changed under its clients (<change>), it is still walked by what it offers, in <dialect>
    The service changes after its clients were written: its acts move to new
    addresses, an input is renamed, an act needs a field it did not, or an act takes
    another method. A HEART client meets each change in the forms it is offered.
    Given the employees service has changed: <change>
    And a client asking in <dialect> at rung <rung> for the employees service
    When it enters the employees service at its bookmark
    And it submits "<hire>" with <hired>
    Then the client holds "/employees/3"
    When it submits "<update>" with <updated>
    Then the state's "role" is "gardener, mayor"

    Examples:
      | change   | dialect          | rung | hire       | update       | hired                                                                | updated                                                                         |
      | moved    | HAL-FORMS        | L0   | hire       | update       | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor"}         |
      | moved    | HAL Schema Forms | L2   | staff:hire | staff:update | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor"}         |
      | renamed  | HAL-FORMS        | L0   | hire       | update       | {"firstName": "Samwise", "lastName": "Gamgee", "position": "gardener"} | {"firstName": "Samwise", "lastName": "Gamgee", "position": "gardener, mayor"}     |
      | renamed  | HAL Schema Forms | L2   | staff:hire | staff:update | {"firstName": "Samwise", "lastName": "Gamgee", "position": "gardener"} | {"firstName": "Samwise", "lastName": "Gamgee", "position": "gardener, mayor"}     |
      | required | HAL-FORMS        | L0   | hire       | update       | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor", "reason": "elected"} |
      | required | HAL Schema Forms | L2   | staff:hire | staff:update | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener, mayor", "reason": "elected"} |
      | method   | HAL-FORMS        | L0   | hire       | update       | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"role": "gardener, mayor"}                                                     |
      | method   | HAL Schema Forms | L2   | staff:hire | staff:update | {"firstName": "Samwise", "lastName": "Gamgee", "role": "gardener"}     | {"role": "gardener, mayor"}                                                     |

  Scenario Outline: Changed to ask for notice first, dismissing is offered only once notice is given, in <dialect>
    Given the employees service has changed: notice
    And a client asking in <dialect> at rung <rung> for the employees service
    When it enters the employees service at its bookmark
    And it submits "<search>" with {"name": "Bilbo"}
    And it follows the "item" link titled "Bilbo Baggins"
    Then it offers exactly these acts
      | <notice> |
      | <update> |
    When it submits "<notice>" with {}
    Then the state's "status" is "on notice"
    And it offers exactly these acts
      | <dismiss> |
      | <update>  |
    When it submits "<dismiss>" with {}
    Then the state's "status" is "dismissed"

    Examples:
      | dialect          | rung | search                 | notice            | dismiss       | update       |
      | HAL-FORMS        | L0   | searchEmployees        | giveNotice        | dismiss       | update       |
      | HAL Schema Forms | L2   | staff:search-employees | staff:give-notice | staff:dismiss | staff:update |

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
