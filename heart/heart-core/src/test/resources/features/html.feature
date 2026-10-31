Feature: HTML for people, rendered from the declaration
  spec/PLAN-HTML-DIALECT.md, decided 2026-10-30. heart-core renders a
  resource as HTML from its L0 representation and the explanation of the
  acts it leaves out, so every binding serves the same page: the state laid
  out for a person, a button for each act offered now, which opens a dialog
  that asks for the act's fields, and a greyed button, with the server's
  reason, for each act it won't offer. Labels are titled from member names;
  the names on the wire never change.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamp 7 is "ON" at version 3

  Scenario: The state is laid out for a person, each member titled from its name
    When lamp 7 is rendered as HTML
    Then the HTML shows the state
      | member     | label      | value |
      | id         | Id         | 7     |
      | status     | Status     | ON    |
      | brightness | Brightness | 50    |

  Scenario: Each act is a button in the type's order, offered or greyed with the server's reason
    An act listed as unavailable keeps its place and stays focusable, so a
    keyboard reaches it and a person learns why. An act neither offered nor
    explained has no button.
    When lamp 7 is rendered as HTML
    Then the HTML shows these acts, in order
      | act             | label        | offered | reason                  |
      | home:switch-on  | Switch on    | no      | The lamp is already on. |
      | home:switch-off | Switch off   | yes     |                         |
      | home:dim        | Dim the lamp | yes     |                         |

  Scenario: An act's dialog sends to the act's target, by its method, with the validator the HTML was rendered under
    When lamp 7 is rendered as HTML under the validator "seq-3.html.L0.abc"
    Then the "home:dim" dialog sends PUT to "/lamps/7/actions/dim" with If-Match "seq-3.html.L0.abc"
    And the "home:switch-off" dialog sends POST to "/lamps/7/actions/switch-off" with If-Match "seq-3.html.L0.abc"

  Scenario: An act's dialog asks for its fields; one with none still opens, as a confirmation
    When lamp 7 is rendered as HTML
    Then the "home:dim" dialog asks for these fields
      | field      | label      | control | required | constraints                         |
      | brightness | Brightness | number  | yes      | min=1 max=100 step=1 placeholder=50 |
      | label      | Label      | text    | no       | minlength=1 placeholder=reading     |
    And the "home:switch-off" dialog asks for no fields

  Scenario Outline: A field's control and constraints come from its schema: <kind>
    When a field "f" with the schema <schema> is rendered as HTML
    Then the field is a <control> with <constraints>

    Examples:
      | kind                | schema                                                                | control      | constraints                         |
      | an integer          | {"type":"integer","minimum":1,"maximum":100}                          | number       | min=1 max=100 step=1                |
      | a number            | {"type":"number"}                                                     | number       | step=any                            |
      | a boolean           | {"type":"boolean"}                                                    | checkbox     | none                                |
      | a choice            | {"type":"string","enum":["warm","cool"],"default":"warm"}             | select       | options=warm,cool selected=warm     |
      | a patterned text    | {"type":"string","pattern":"^[A-Z ]+$","maxLength":20}                | text         | pattern=^[A-Z ]+$ maxlength=20      |
      | an example          | {"type":"string","examples":["reading"]}                              | text         | placeholder=reading                 |
      | a list of texts     | {"type":"array","items":{"type":"string"}}                            | text list    | none                                |
      | a plain text        | {"type":"string"}                                                     | text         | none                                |

  Scenario Outline: A text member named for long text is a text area: <name>
    When a field "<name>" with the schema {"type":"string"} is rendered as HTML
    Then the field is a <control> with none

    Examples:
      | name    | control  |
      | body    | textarea |
      | content | textarea |
      | message | text     |

  Scenario: A field's words are shown as text, never run as markup
    The words come from a server, and through a gateway from an upstream's
    description: a page must never run them.
    When a field "f" with the schema {"type":"string","title":"<b>Name</b>","description":"<script>x()</script> & more"} is rendered as HTML
    Then its label reads "<b>Name</b>" and its hint reads "<script>x()</script> & more"
    And the rendered field has no element "b" or "script"
