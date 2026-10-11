Feature: A resource is created from the bookmark
  A declared type may name one creation act. The service index offers it as a
  POST form whose target is the type's collection. Its owning resource is the
  index, so a client sends the index's validator in If-Match (SPEC §6.2), and
  heart-core checks a creation like any other act: no validator is 428, a
  stale one is 412, and a body that breaks the act's schema is 422. The index
  always offers it, so a creation is never 409. A binding answers a creation
  with the new resource's Layer 1 and its location.

  Background:
    Given a lamp resource type declared with the profile "home"

  Scenario: In HAL Schema Forms, the index offers the creation as a POST form on the collection
    When the service index is rendered in HAL Schema Forms
    Then it offers the POST form "home:install-lamp" with target "/lamps" and required field "brightness"

  Scenario: In HAL-FORMS, the creation is a POST template whose properties come from its schema
    When the service index is rendered in HAL-FORMS
    Then it offers the POST template "installLamp" with target "/lamps" and required property "brightness" from 1 to 100

  Scenario: The home document says the collection also takes a conditional POST
    When the home document is rendered for a server capped at L2
    Then the "collection" hints allow "GET, POST" and need an ETag to act

  Scenario: The creation's schema is served by its content, like every act's
    Then the schema of "installLamp" is served at its content address

  Scenario Outline: A creation is checked like any act: <case>
    When a client creates a lamp with validator <validator> and body <body>
    Then the act is refused as <refusal>
    And no lamp was created

    Examples:
      | case                         | validator | body               | refusal                  |
      | no validator                 | none      | {"brightness": 20} | precondition required    |
      | a stale validator            | stale     | {"brightness": 20} | stale                    |
      | a body that breaks the schema | current  | {"brightness": 0}  | invalid at "/brightness" |

  Scenario: A checked creation is performed with its body bound to the act's input class
    When a client creates a lamp with validator current and body {"brightness": 20}
    Then the act was performed
    And lamp 1 is "OFF" with brightness 20 at version 1
