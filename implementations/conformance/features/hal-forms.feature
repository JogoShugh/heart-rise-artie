Feature: What a HEART client must do in HAL-FORMS, the L0 on-ramp
  HAL-FORMS is one of HEART's two dialects (SPEC §6.1). It is served at L0 only,
  with each form inline under _templates and its fields in a properties array.
  A client that speaks it asks for it, and reads whichever dialect comes back.

  The garden is the one client.feature uses, and "the small service" is one the
  steps serve. A template's type is a hint for a person filling in a form, so a
  client refuses only what its other members disallow.

  Scenario: Find a bed from the bookmark and read what it offers
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    Then the state's "status" is "PLANTED"
    And the representation is at rung L0, in HAL-FORMS
    And it offers exactly "waterCell"

  Scenario: Find a bed by part of its name
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "searchBeds" with {"name": "bed-2"}
    Then it holds these embedded resources
      | relation | title | status  |
      | item     | bed-2 | PLANTED |

  Scenario: An act in HAL-FORMS is conditional, and its answer is the resource's new state
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it submits "waterCell" with {"milliliters": 250}
    Then the act carried If-Match with the validator it read
    And the state's "moistureLevel" is 65
    And it offers exactly "harvest"

  Scenario: When someone else acts first, the act is stale
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    And another HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And the other client waters bed 2 with 100 ml, in HAL-FORMS
    And it tries to submit "waterCell" with {"milliliters": 100}
    Then it was refused as "stale" with status 412
    And the state's "moistureLevel" is 50

  Scenario: A body a rule refuses is told apart in HAL-FORMS
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it tries to submit "waterCell" with {"milliliters": 700}
    Then it was refused as "rule-refused" with status 422

  Scenario: A body outside a template's limits is refused before anything is sent
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it tries to submit "waterCell" with {"milliliters": 0}
    Then it was refused as "outside-schema" before sending, because "milliliters"

  Scenario: A client asks for HAL-FORMS, and for no layering
    Given a HAL-FORMS service offering "note" with the properties []
    And a HAL-FORMS client at rung L2 with no credential
    When it enters that service
    Then the last read accepted "application/prs.hal-forms+json, application/hal+json;q=0.5", at the HAL-FORMS profile, with no cache
    And the last read asked for no layering
    And the representation is at rung L0, in HAL-FORMS

  Scenario: A client that asks for HAL-FORMS reads the reference dialect, if that is what comes back
    Given a service offering "note" with the schema {"type": "object"}
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the representation is at rung L0, in HAL Schema Forms
    And it offers exactly "note"

  Scenario: The HAL-FORMS media type is enough, for a service that names no HEART profile
    Given a HAL-FORMS service offering "note" with the properties []
    And that service names no profile
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the representation is at rung L0, in HAL-FORMS
    And it offers exactly "note"

  Scenario: In HAL-FORMS the forms are the templates, and a layering header means nothing
    Given a HAL-FORMS service offering "note" with the properties []
    And that service also says it applied forms by link
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the representation is at rung L0, in HAL-FORMS
    And it offers exactly "note"

  Scenario Outline: What a template does not disallow is sent: <case>
    Given a HAL-FORMS service offering "note" with the properties <properties>
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with <body>
    Then the act "note" was sent 1 times
    And no schema failure was noted

    Examples:
      | case                     | properties                                                  | body                  |
      | a boolean for a checkbox | [{"name": "done", "type": "checkbox"}]                      | {"done": true}        |
      | text for a number        | [{"name": "n", "type": "number"}]                           | {"n": "5"}            |
      | an undeclared property   | [{"name": "a"}]                                             | {"a": 1, "b": 2}      |
      | an absent optional       | [{"name": "a", "required": false, "minLength": 2}]          | {}                    |
      | a member it does not use | [{"name": "a", "readOnly": true, "prompt": "A", "x": 1}]    | {"a": 1}              |
      | a nameless property      | [{"prompt": "no name", "required": true}, "text"]           | {}                    |
      | values at the limits     | [{"name": "n", "min": 1, "max": 3}]                         | {"n": 3}              |

  Scenario Outline: What a template disallows is refused before anything is sent: <case>
    Given a HAL-FORMS service offering "note" with the properties <properties>
    And a HAL-FORMS client with no credential
    When it enters that service
    And it tries to submit "note" with <body>
    Then it was refused as "<kind>" before sending, because "<says>"

    Examples:
      | case             | properties                                                 | body         | kind           | says                       |
      | a required value | [{"name": "a", "required": true}]                          | {}           | outside-schema | /a required                |
      | below min        | [{"name": "n", "min": 2}]                                  | {"n": 1}     | outside-schema | /n minimum                 |
      | above max        | [{"name": "n", "max": 2}]                                  | {"n": 3}     | outside-schema | /n maximum                 |
      | under minLength  | [{"name": "s", "minLength": 2}]                            | {"s": "a"}   | outside-schema | /s minLength               |
      | over maxLength   | [{"name": "s", "maxLength": 1}]                            | {"s": "ab"}  | outside-schema | /s maxLength               |
      | against regex    | [{"name": "s", "regex": "^a$"}]                            | {"s": "b"}   | outside-schema | /s pattern                 |
      | outside options  | [{"name": "s", "options": {"inline": ["a"]}}]              | {"s": "b"}   | not-a-choice   | s: b is not one of the choices |

  Scenario: A regex the client cannot compile blocks nothing, and is noted locally once
    Given a HAL-FORMS service offering "note" with the properties [{"name": "s", "regex": "["}]
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {"s": "a"}
    Then the act "note" was sent 1 times
    And the local log holds one schema failure, for "note"

  Scenario: A template says little, and the defaults fill the rest
    Given a HAL-FORMS service whose template "note" is {}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {}
    Then the last request was GET with the body ""
    And the last request was for "/"

  Scenario: A template with no target acts on the resource, as JSON
    Given a HAL-FORMS service whose template "note" is {"method": "POST"}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {"a": 1}
    Then the last request was POST with the body "{\"a\":1}"
    And the last request was for "/"
    And it had the content type "application/json"

  Scenario: A template's own title and content type are kept
    Given a HAL-FORMS service whose template "note" is {"method": "PUT", "target": "/a", "title": "A note", "contentType": "application/vnd.garden+json"}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {}
    Then it had the content type "application/vnd.garden+json"
    And the last request was for "/a"

  Scenario Outline: Fields of a <method> go in the query, form-encoded, replacing any there
    Given a HAL-FORMS service whose template "note" is {"method": "<method>", "target": "/find?old=1"}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {"name": "a b"}
    Then the last request was <method> with the body ""
    And the last request was for "/find?name=a+b"

    Examples:
      | method |
      | GET    |
      | DELETE |

  Scenario: Options written inline are the choices
    Given a HAL-FORMS service offering "note" with the properties [{"name": "s", "options": {"inline": ["a", {"value": "b", "prompt": "Bee"}]}}]
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the choices for "s" of "note" are
      | value | prompt |
      | a     | a      |
      | b     | Bee    |

  Scenario: Options may come from another resource
    Given a HAL-FORMS service offering "note" with the properties [{"name": "s", "options": {"link": {"href": "/sizes"}, "valueField": "code", "promptField": "label"}}]
    And that service serves "/sizes" with status 200 and the body [{"code": "c", "label": "Cee"}]
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the choices for "s" of "note" are
      | value | prompt |
      | c     | Cee    |
