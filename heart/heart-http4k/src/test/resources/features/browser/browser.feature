Feature: The HTML dialect at work in a browser
  heart-forms.js is what a HEART page runs beyond htmx: it opens an act's
  dialog, checks a form against its act's schema before sending, shows a
  refusal in the dialog that asked, reads the resource again when it changed
  meanwhile, shows why a greyed act is not offered, fills a safe act's
  address from its template, and reads a row's acts in a list. These
  scenarios run it in headless Chrome: against the lamps heart-http4k serves
  on a real port, or against a resource rendered from its representation.
  What heart-forms.js ran is kept as coverage for SonarQube. Without Chrome
  on the machine they are skipped.

  Scenario: An act's dialog sends its fields; the resource shows the new state, and the page says what was done
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And lamp 7 is open in a browser
    When the person sends the act "dim" with
      | field      | value   |
      | brightness | 80      |
      | label      | reading |
    Then the resource shows "brightness" as "80"
    And the page announces "Dim the lamp: done."

  Scenario: A refusal is shown in the dialog that asked, beside the field it names
    The lamp's own rule: it will not dim to the brightness it already has.
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And lamp 7 is open in a browser
    When the person sends the act "dim" with
      | field      | value |
      | brightness | 50    |
    Then the dialog of "dim" is still open
    And the field "brightness" says "The lamp is already at 50"
    And the resource shows "brightness" as "50"

  Scenario: An act sent after the resource changed meanwhile reads it again, and asks to check and send again
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And lamp 7 is open in a browser
    And another client dims lamp 7 to 30
    When the person sends the act "dim" with
      | field      | value |
      | brightness | 80    |
    Then the resource shows "brightness" as "30"
    And the dialog of "dim" is open again, saying "This changed since you opened it. Check it, then send again."
    And the field "brightness" still holds "80"

  Scenario: An act no longer offered when sent says so, with the reason, beside its greyed button
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And lamp 7 is open in a browser
    And another client switches lamp 7 off
    When the person sends the act "dim" with
      | field      | value |
      | brightness | 80    |
    Then the page announces that "Dim the lamp" is no longer offered, with its reason
    And the reason beside the greyed "dim" is shown

  Scenario: A greyed act shows why it is not offered when pressed, and hides it when pressed again
    Given lamp 7 is "OFF" at brightness 50, served by heart-http4k
    And lamp 7 is open in a browser
    When the person presses the greyed act "dim"
    Then the reason beside the greyed "dim" is shown
    When the person presses the greyed act "dim"
    Then the reason beside the greyed "dim" is hidden

  Scenario: A search is sent as an address, filled from its template
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And the bookmark is open in a browser
    When the person sends the search "search-lamps" with
      | field | value  |
      | name  | Lamp 7 |
    Then the browser asks for an address whose query has "name" as "Lamp 7"

  Scenario: A row's acts are read from its match when asked, offered or greyed with why
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    And the lamps named "Lamp" are open in a browser
    When the person asks for the acts of the row "Lamp 7"
    Then the row's acts read
      | act                                 |
      | Switch on: The lamp is already on.  |
      | Switch off                          |
      | Dim the lamp                        |

  Scenario: Arriving with an act's dialog named in the address opens it
    Given lamp 7 is "ON" at brightness 50, served by heart-http4k
    When lamp 7 is opened in a browser at its act "dim"
    Then the dialog of "dim" is open

  Scenario Outline: A form is checked against its act's whole schema before anything is sent
    HTML's own constraints say what one field needs; the schema says more.
    Each value here passes the browser's own checks and fails the schema's.
    Given a resource whose act "x:measure" has the schema <schema>, open in a browser
    When the person sends the act "x:measure" with
      | field   | value   |
      | <field> | <value> |
    Then <where> says "<said>"
    And nothing was sent

    Examples:
      | schema                                                                                                                          | field | value | where                      | said                                   |
      | {"type":"object","properties":{"size":{"type":"number","multipleOf":0.5}},"required":["size"]}                                   | size  | 0.3   | the field "size"           | A multiple of 0.5.                     |
      | {"type":"object","properties":{"size":{"type":"number"},"note":{"type":"string"}},"anyOf":[{"required":["size"]},{"required":["note"]}]} | size  |       | the dialog of "x:measure"  | Fill in at least one of: Size, Note.   |
      | {"type":"object","properties":{"size":{"type":"number","exclusiveMaximum":10}},"required":["size"]}                              | size  | 10    | the field "size"           | Less than 10.                          |

  Scenario: A list field takes another entry when asked, and its entries are sent as a list
    Given a resource whose act "x:tag" has the schema {"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"},"minItems":2,"uniqueItems":true}},"required":["tags"]}, open in a browser
    When the person opens the act "x:tag", adds an entry to "tags", and fills the entries "red" and "red"
    And sends the act "x:tag"
    Then the field "tags" says "Each entry only once."
    And nothing was sent
