Feature: What a client does with what a service sends
  The small service the steps serve, set up differently by each scenario: the
  refusals a client must tell apart, the requests it must form from a form, and
  the things it must not do, such as carry a credential to another origin.

  Background:
    Given a service offering "note" with the schema {"type": "object"}
    And a client at rung L0 with no credential

  Scenario Outline: Each refusal is told apart: status <status>, <says>
    Given that service answers "note" with status <status> and the body <body>
    When it enters that service
    And it tries to submit "note" with {}
    Then it was refused as "<kind>" with status <status>
    And the refusal says "<says>"
    And the resource was read <reads> times

    Examples:
      | status | body                                                                                                               | kind                  | says                              | reads |
      | 412    | {"detail": "changed"}                                                                                              | stale                 | changed; re-read, not resent      | 2     |
      | 409    | {"title": "taken"}                                                                                                 | not-offered           | note was refused (409): taken     | 2     |
      | 428    | {"title": "no validator"}                                                                                          | precondition-required | note was refused (428): no validator | 1  |
      | 401    | {"title": "who"}                                                                                                   | not-authenticated     | note was refused (401): who       | 1     |
      | 500    | {"title": "broke"}                                                                                                 | failed                | note was refused (500): broke     | 1     |
      | 500    | not json                                                                                                           | failed                | (500): Internal Server Error      | 1     |
      | 422    | {"type": "https://w3id.org/heart-rise/problems/rule-refused", "violations": [{"pointer": "/a", "message": "big"}]} | rule-refused          | /a big                            | 1     |
      | 422    | {"violations": [{"pointer": "/b", "detail": "small"}]}                                                             | invalid-input         | /b small                          | 1     |
      | 422    | {"violations": [{"message": "only words"}]}                                                                        | invalid-input         | only words                        | 1     |

  Scenario: A resource that does not name the profile is not read
    Given that service names no profile
    When it tries to enter that service
    Then it failed, saying "does not name the profile"

  Scenario: A read that is refused is named as a read
    Given that service refuses every read with status 503
    When it tries to enter that service
    Then it was refused as "failed" with status 503
    And the refusal says "read was refused (503)"

  Scenario: Nothing can be done before entering
    When it tries to read the resource again
    Then it failed, saying "enter a service first"

  Scenario: Choices are asked of an offered form only
    When it enters that service
    And it tries to read the choices of "ghost"
    Then it failed, saying "ghost is not offered now"

  Scenario Outline: A link is followed only if it is offered: <case>
    Given that service links "item" titled "first"
    When it enters that service
    And it tries to follow "<rel>" titled "<title>"
    Then <outcome>

    Examples:
      | case            | rel   | title  | outcome                                                  |
      | title and rel   | item  | first  | it holds a representation                                |
      | no such title   | item  | second | it failed, saying "no item link titled second is offered" |
      | no such rel     | ghost | first  | it failed, saying "no ghost link titled first is offered" |

  Scenario: A link is followed by its relation alone, when no title is asked for
    Given that service links "item" titled "first"
    When it enters that service
    And it tries to follow "ghost" with no title
    Then it failed, saying "no ghost link is offered"

  Scenario: A credential goes only to the bookmark's own origin
    Given a client at rung L0 with the garden's credential
    And that service sends "note" to another origin
    When it enters that service
    And it submits "note" with {}
    Then the service was sent the credential
    And the other origin was sent none

  Scenario Outline: An act carries what its form says, by <method>
    Given that service offers "note" by <method>
    When it enters that service
    And it submits "note" with {}
    Then the last request was <method> with the body "<body>"
    And it carried the validator it read
    And it had the content type "<type>"

    Examples:
      | method | body | type             |
      | POST   | {}   | application/json |
      | PUT    | {}   | application/json |
      | DELETE |      |                  |

  Scenario: A form that names no method or content type is a JSON POST
    Given that service's form names no method
    When it enters that service
    And it submits "note" with {}
    Then the last request was POST with the body "{}"
    And it had the content type "application/json"

  Scenario: A form's own content type is sent
    Given that service's form takes "application/vnd.garden+json"
    When it enters that service
    And it submits "note" with {}
    Then it had the content type "application/vnd.garden+json"

  Scenario Outline: A safe form's address comes from the form: <case>
    Given that service reads "note" at "<target>", with pointers <pointers> into the state <state>
    When it enters that service
    And it submits "note" with <fields>
    Then the last request was for "<url>"

    Examples:
      | case                      | target            | pointers           | state                 | fields                       | url                        |
      | a field                   | /beds/{id}        | {}                 | {}                    | {"id": "x y"}                | /beds/x%20y                |
      | the state's member        | /beds/{id}        | {}                 | {"id": 3}             | {}                           | /beds/3                    |
      | a field before the state  | /beds/{id}        | {}                 | {"id": 3}             | {"id": 4}                    | /beds/4                    |
      | a pointer into the state  | /beds/{id}        | {"id": "/a/b"}     | {"a": {"b": 5}}       | {"id": 4}                    | /beds/5                    |
      | a pointer into a list     | /beds/{id}        | {"id": "/a/1"}     | {"a": [5, 6]}         | {}                           | /beds/6                    |
      | an escaped pointer        | /beds/{id}        | {"id": "/a~1b~0c"} | {"a/b~c": 7}          | {}                           | /beds/7                    |
      | a query of one value      | /find{?tag}       | {}                 | {}                    | {"tag": "red"}               | /find?tag=red              |
      | a query of many values    | /find{?tag,count} | {}                 | {}                    | {"tag": ["a", "b"], "count": 2} | /find?tag=a&tag=b&count=2 |
      | a query with nothing      | /find{?tag}       | {}                 | {}                    | {}                           | /find                      |
      | a query from a null       | /find{?tag}       | {"tag": "/t"}      | {"t": null}           | {}                           | /find                      |
      | a field the form does not name | /find        | {}                 | {}                    | {"tag": "red"}               | /find?tag=red              |

  Scenario Outline: A template variable with no usable value stops the act: <case>
    Given that service reads "note" at "/beds/{id}", with pointers {} into the state <state>
    When it enters that service
    And it tries to submit "note" with {}
    Then it failed, saying "the template variable id has no string or number value; nothing was sent"

    Examples:
      | case      | state           |
      | no value  | {}              |
      | a boolean | {"id": true}    |
      | an object | {"id": {"a": 1}} |

  Scenario: Choices may be plain values, and only where nothing is asked of them
    Given that service declares the schema {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}, with the state {"sizes": ["s", 2, null, ["x"]]}
    When it enters that service
    Then the choices for "size" of "note" are
      | value | prompt |
      | s     | s      |
      | 2     | 2      |

  Scenario: A plain value is no choice when the options ask for members
    Given that service declares the schema {"properties": {"size": {"hr:options": {"pointer": "/sizes", "where": {"a": 1}}}}}, with the state {"sizes": ["s"]}
    When it enters that service
    Then the choices for "size" of "note" are
      | value | prompt |

  Scenario: A choice's value and prompt have default names, and the prompt falls back to the value
    Given that service declares the schema {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}, with the state {"sizes": [{"value": 1, "prompt": "one"}, {"value": 2}, {"prompt": "none"}]}
    When it enters that service
    Then the choices for "size" of "note" are
      | value | prompt |
      | 1     | one    |
      | 2     | 2      |

  Scenario: Options that name neither pointer nor link offer nothing
    Given that service declares the schema {"properties": {"size": {"hr:options": {}}}}, with the state {}
    When it enters that service
    Then the choices for "size" of "note" are
      | value | prompt |

  Scenario: A pointer to something that is not a list offers nothing
    Given that service declares the schema {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}, with the state {"sizes": 3}
    When it enters that service
    Then the choices for "size" of "note" are
      | value | prompt |

  Scenario: A field in a pointer is escaped
    Given that service declares the schema {"properties": {"size": {"hr:options": {"pointer": "/by/{team}"}}}}, with the state {"by": {"a/b~c": ["x"]}}
    When it enters that service
    Then the choices for "size" of "note", by "team", are
      | team  | value | prompt |
      | a/b~c | x     | x      |

  Scenario Outline: The choices stand between a value and the wire: <case>
    Given that service declares the schema <schema>, with the state {"sizes": ["s"], "none": []}
    When it enters that service
    And it tries to submit "note" with <fields>
    Then it was refused as "not-a-choice" before sending, because "<says>"

    Examples:
      | case                 | schema                                                                                     | fields             | says                            |
      | required with none   | {"required": ["size"], "properties": {"size": {"hr:options": {"pointer": "/none"}}}}       | {}                 | size has no choices now         |
      | one of a list        | {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}                            | {"size": ["s", "z"]} | size: z is not one of the choices |

  Scenario Outline: Leaving a property out needs no choice: <case>
    Given that service declares the schema <schema>, with the state {"sizes": ["s"], "none": []}
    When it enters that service
    And it submits "note" with {}
    Then the act "note" was sent 1 times

    Examples:
      | case                | schema                                                                               |
      | optional with none  | {"properties": {"size": {"hr:options": {"pointer": "/none"}}}}                       |

  Scenario: A linked affordance set must carry its integrity
    Given that service applies L2, with its affordance set linked without an integrity value
    When it tries to enter that service
    Then it failed, saying "was linked without an integrity value; nothing was sent"

  Scenario: A representation at L2 must link its affordance set
    Given that service applies L2, with no affordance set
    When it tries to enter that service
    Then it failed, saying "applied L2 but links no affordance set"

  Scenario: An answer's Content-Location names the resource held
    Given that service answers acts from "/elsewhere"
    When it enters that service
    And it submits "note" with {}
    Then the client holds "/elsewhere"

  Scenario: An answer with no Content-Location is the resource acted on
    When it enters that service
    And it submits "note" with {}
    Then the client holds "/"

  Scenario: A schema the service changes is checked as the new one
    When it enters that service
    And that service now declares the schema {"type": "object", "required": ["text"]}
    And it reads the resource again
    And it tries to submit "note" with {}
    Then it was refused as "outside-schema" before sending, because "text"
