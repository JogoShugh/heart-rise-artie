Feature: Dialect and profile negotiation
  SPEC §6.1, §6.2, §6.4. Accept picks the dialect. Every Layer 1 response
  names its composite heart-rise profile in Link rel="profile", never in a
  media-type parameter. Accept-Profile picks the dialect only when Accept
  leaves it open. A profile the server cannot serve falls back to the
  default instead of failing (connegp §7.3.2). Only an Accept the server
  cannot satisfy at all gets 406.

  Background:
    Given bed 2 is PLANTED

  Scenario Outline: Accept and Accept-Profile pick the dialect and its profile: Accept "<accept>", Accept-Profile "<accept-profile>"
    When a client gets bed 2 with Accept "<accept>" and Accept-Profile "<accept-profile>"
    Then the response status is 200
    And the response Content-Type is "<content-type>"
    And the response links to profile "https://github.com/jogoshugh/heart-rise/<profile>"
    And the response carries its forms under "<forms-key>"

    Examples: Accept decides when it names a dialect
      | accept                                                     | accept-profile                                              | content-type                   | profile          | forms-key  |
      | application/hal+json                                       | <https://github.com/jogoshugh/heart-rise/hal-schema-forms> | application/hal+json           | hal-schema-forms | _forms     |
      | application/hal+json                                       |                                                             | application/hal+json           | hal-schema-forms | _forms     |
      | application/prs.hal-forms+json, application/hal+json;q=0.5 | <https://github.com/jogoshugh/heart-rise/hal-forms>        | application/prs.hal-forms+json | hal-forms        | _templates |
      | application/hal+json;q=0.5, application/prs.hal-forms+json |                                                             | application/prs.hal-forms+json | hal-forms        | _templates |

    Examples: Accept-Profile decides when Accept leaves the dialect open
      | accept | accept-profile                                              | content-type                   | profile          | forms-key  |
      | */*    | <https://github.com/jogoshugh/heart-rise/hal-schema-forms> | application/hal+json           | hal-schema-forms | _forms     |
      |        | <https://github.com/jogoshugh/heart-rise/hal-schema-forms> | application/hal+json           | hal-schema-forms | _forms     |
      |        | <https://github.com/jogoshugh/heart-rise/hal-forms>        | application/prs.hal-forms+json | hal-forms        | _templates |

    Examples: with nothing to go on, the default dialect and its profile
      | accept | accept-profile                          | content-type                   | profile   | forms-key  |
      |        |                                         | application/prs.hal-forms+json | hal-forms | _templates |
      |        | <https://example.org/unknown-profile>   | application/prs.hal-forms+json | hal-forms | _templates |

  Scenario: Layer 1 also names the parent heart-rise profile
    When a client gets bed 2 with Accept "application/hal+json" and Accept-Profile ""
    Then the response links to profile "https://github.com/jogoshugh/heart-rise"

  Scenario Outline: Layer 1 varies on every header that picks its representation: <accept>
    When a client gets bed 2 with Accept "<accept>" and Accept-Profile ""
    Then the response Vary lists "<vary>"

    Examples:
      | accept                         | vary                           |
      | application/hal+json           | Accept, Prefer, Accept-Profile |
      | application/prs.hal-forms+json | Accept, Accept-Profile         |

  Scenario: An Accept the server cannot satisfy gets 406
    When a client gets bed 2 with Accept "text/html" and Accept-Profile ""
    Then the response status is 406

  Scenario: The affordance-set link names its profile, not a media-type parameter
    When a client gets bed 2 with Prefer "forms=cid"
    Then the affordance-set link has type "application/hal+json" and profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    When the client follows the affordance-set link
    Then the response Content-Type is "application/hal+json"
