Feature: Forms over HTTP: named by relations, shared, and accepted where their templates point
  SPEC §6.1, §6.5, §6.9.1 and §8.5. heart-core renders forms and affordance
  sets (its layers.feature); these scenarios check what only a served
  response shows: a set's caching headers, that it never varies, the CURIEs
  a response declares, and that an expanded template is where the act is
  taken.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario: At L0 a form is keyed by a declared CURIE, with a title and a description
    When a client gets "/lamps/7" as hal-schema-forms
    Then the response declares the CURIE "home" as "/profiles/home#{rel}"
    And the response declares the CURIE "hr" as "https://github.com/jogoshugh/heart-rise/rels/{rel}"
    And the "home:dim" form has a title and a description

  Scenario: An affordance set declares its CURIEs too, does not vary, and ignores Prefer
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    And the client follows the "hr:forms" link
    Then the response declares the CURIE "home" as "/profiles/home#{rel}"
    And the response has no Vary header
    When the client follows the same "hr:forms" link with Prefer "forms=link"
    Then the last two responses have identical bytes

  Scenario: A form's template, expanded by its pointers into Layer 1, is where the act is accepted
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    And the client follows the "hr:forms" link
    And the client expands "home:dim" by its template pointers and posts {"brightness": 20} with the ETag it holds
    Then the response status is 200
    And the response has Content-Location "/lamps/7"

  Scenario: HAL-FORMS keeps its template names, and declares no CURIEs
    When a client gets "/lamps/7" as hal-forms
    Then the response has no member "_links.curies"

  Scenario: A HAL-FORMS template's properties come from the act's schema, and can be filled in
    One schema per act (A4 rule 1): the HAL-FORMS template says what the
    form's schema says, so the two dialects cannot drift. A property is never
    read-only, whatever the Kotlin of its input class looks like.
    When a client gets "/lamps/7" as hal-forms
    Then the template targeting "/lamps/7/actions/dim" has the property "brightness", required, from 1 to 100
    And no property of any template is read-only

  Scenario: The ALPS profile says which relations change state, and which may be repeated
    When a client gets "/profiles/home"
    Then the ALPS document describes "switch-off" as "unsafe"
    And the ALPS document describes "dim" as "idempotent"
    And the ALPS document describes "search-lamps" as "safe"
