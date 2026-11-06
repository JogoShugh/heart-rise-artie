Feature: The bookmark offers acts, like any resource
  The service index is a resource, and it can offer acts of its own: domain
  acts, such as installing a lamp or a customer reporting a problem, whose
  result is a resource that did not exist before. A type declares the acts
  the bookmark offers for it. Each is a form like any other: its target is
  the bookmark's own `/actions/{path}`, and a client sends the bookmark's
  validator in If-Match (SPEC §6.2). heart-core checks it like any act: no
  validator is 428, a stale one is 412, a body that breaks the act's schema
  is 422. The bookmark always offers its acts, so never 409.

  Background:
    Given a lamp resource type declared with the profile "home"

  Scenario: In HAL Schema Forms, the bookmark offers the act as a form on its own actions
    When the service index is rendered in HAL Schema Forms
    Then it offers these POST forms
      | form              | target                | required field |
      | home:install-lamp | /actions/install-lamp | brightness     |

  Scenario: In HAL-FORMS, the act is a template whose properties come from its schema
    When the service index is rendered in HAL-FORMS
    Then it offers these POST templates
      | template    | target                | required property | min | max |
      | installLamp | /actions/install-lamp | brightness        | 1   | 100 |

  Scenario: The act's schema is served by its content, like every act's
    Then the schema of "installLamp" is served at its content address

  Scenario Outline: The bookmark's act is checked like any act: <case>
    When a client acts on the bookmark with "installLamp", validator <validator>, and body <body>
    Then the act is refused as <refusal>
    And no lamp was installed

    Examples:
      | case                          | validator | body               | refusal                  |
      | no validator                  | none      | {"brightness": 20} | precondition required    |
      | a stale validator             | stale     | {"brightness": 20} | stale                    |
      | a body that breaks the schema | current   | {"brightness": 0}  | invalid at "/brightness" |

  Scenario: A checked act on the bookmark is performed with its body bound to the act's input class
    When a client acts on the bookmark with "installLamp", validator current, and body {"brightness": 20}
    Then the act was performed
    And lamp 1 is "OFF" with brightness 20 at version 1
