Feature: A safe form's address comes from the form
  HAL-SCHEMA-FORMS-HR Rule 1: a form's target is a URI template, filled from
  the form's fields, from pointers into the state, and from the state's own
  members; a field the template does not take goes in the query, and a
  target that is not a template is kept as written. The build service's job
  "nightly" is shown offering a GET form "ci:find" each scenario writes.

  Background:
    Given a client at rung L0 with the build service's credential

  Scenario Outline: A safe form's address comes from the form: <case>
    Given the build service's job also holds <state>
    And the build service's job offers "ci:find" as <kind> to "<target>", with pointers <pointers>
    When it enters the job "nightly"
    And it tries to submit "ci:find" with <fields>
    Then it sent GET for "<url>", with no body

    Examples:
      | case                           | kind               | target            | pointers           | state           | fields                          | url                       |
      | a field                        | a GET              | /beds/{id}        | {}                 | {}              | {"id": "x y"}                   | /beds/x%20y               |
      | the state's member             | a GET              | /beds/{id}        | {}                 | {"id": 3}       | {}                              | /beds/3                   |
      | a field before the state       | a GET              | /beds/{id}        | {}                 | {"id": 3}       | {"id": 4}                       | /beds/4                   |
      | a pointer into the state       | a GET              | /beds/{id}        | {"id": "/a/b"}     | {"a": {"b": 5}} | {"id": 4}                       | /beds/5                   |
      | a pointer into a list          | a GET              | /beds/{id}        | {"id": "/a/1"}     | {"a": [5, 6]}   | {}                              | /beds/6                   |
      | an escaped pointer             | a GET              | /beds/{id}        | {"id": "/a~1b~0c"} | {"a/b~c": 7}    | {}                              | /beds/7                   |
      | a pointer that is no text      | a GET              | /beds/{id}        | {"id": 5}          | {}              | {"id": 4}                       | /beds/4                   |
      | a query of one value           | a GET              | /find{?tag}       | {}                 | {}              | {"tag": "red"}                  | /find?tag=red             |
      | a query of many values         | a GET              | /find{?tag,count} | {}                 | {}              | {"tag": ["a", "b"], "count": 2} | /find?tag=a&tag=b&count=2 |
      | a query with nothing           | a GET              | /find{?tag}       | {}                 | {}              | {}                              | /find                     |
      | a query from a null            | a GET              | /find{?tag}       | {"tag": "/t"}      | {"t": null}     | {}                              | /find                     |
      | a field the form does not name | a GET              | /find             | {}                 | {}              | {"tag": "red"}                  | /find?tag=red             |
      | a target that is no template   | an untemplated GET | /a{b}             | {}                 | {}              | {"b": "c"}                      | /a%7Bb%7D?b=c             |

  Scenario Outline: A template variable with no usable value stops the act: <case>
    Given the build service's job also holds <state>
    And the build service's job offers "ci:find" as a GET to "/beds/{bed}", with pointers {}
    When it enters the job "nightly"
    And it tries to submit "ci:find" with {}
    Then it failed, saying "the template variable bed has no string or number value; nothing was sent"

    Examples:
      | case      | state             |
      | no value  | {}                |
      | a boolean | {"bed": true}     |
      | an object | {"bed": {"a": 1}} |
