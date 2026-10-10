Feature: Forms are named by link relations and describe themselves
  SPEC §6.1, §8.5 and HAL-SCHEMA-FORMS-HR Rules 6 and 7 (decided
  2026-10-10). In HAL Schema Forms a form's key is a link relation, written
  as a CURIE declared in _links.curies. Each form has a title and a
  description, and each field has JSON Schema annotations. The relation URI
  identifies a descriptor in the server's ALPS profile document, which says
  what the act does. HAL-FORMS at L0 keeps its own template names.

  Background:
    Given bed 2 is PLANTED

  Scenario: At L0 the bed's forms are keyed by a declared CURIE, with titles and descriptions
    When a client gets bed 2 with no Prefer header
    Then the response declares the CURIE "garden" as "/profiles/garden#{rel}"
    And the response offers exactly the forms "garden:water-cell"
    And the "garden:water-cell" form has a title and a description
    And the "garden:water-cell" form's field "milliliters" has a title and a description

  Scenario Outline: An affordance set declares the CURIE its form keys use: Prefer <prefer>
    When a client gets bed 2 with Prefer "<prefer>"
    And the client follows the affordance-set link
    Then the response declares the CURIE "garden" as "/profiles/garden#{rel}"
    And the response offers exactly the forms "garden:water-cell"
    And the "garden:water-cell" form has a title and a description

    Examples:
      | prefer                  |
      | forms=link              |
      | forms=link, schema=link |

  Scenario: The index's forms are relations too
    When a client gets the service index
    Then the response declares the CURIE "garden" as "/profiles/garden#{rel}"
    And the response offers exactly the forms "garden:search-beds, garden:get-bed"

  Scenario: The ALPS profile describes every relation, with the right safety for its method
    When a client gets "/profiles/garden"
    Then the response status is 200
    And the response Content-Type is "application/alps+json"
    And the response has an ETag
    And the ALPS document describes "sow-seed" as "unsafe"
    And the ALPS document describes "water-cell" as "unsafe"
    And the ALPS document describes "harvest" as "unsafe"
    And the ALPS document describes "search-beds" as "safe"
    And the ALPS document describes "get-bed" as "safe"

  Scenario: HAL-FORMS keeps its template names
    When a client gets bed 2 as hal-forms with Prefer ""
    Then the response has no "_links.curies"
