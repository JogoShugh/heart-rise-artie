Feature: Forms named by link relations, described for the reasoner
  SPEC §6.1, §6.8, §8.5; HAL-SCHEMA-FORMS-HR Rules 6 and 7 (2026-10-10). A
  form's key is a link relation, usually a CURIE. The client expands it
  through _links.curies, so the form can be named as written or by its full
  relation URI. The reasoner is shown each form's title and description,
  never its target or method. It may ask for the relation's ALPS
  descriptor, which deterministic code fetches; ALPS describes, it never
  makes an act legal.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
        curies: [{name: garden, href: "/profiles/garden#{rel}", templated: true}]
      _forms:
        garden:water-cell:
          title: Water the bed
          description: Pours water into every cell of a planted bed.
          _links: {target: {href: /beds/2/actions/water-cell}}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              milliliters: {type: integer, minimum: 1, title: Water per cell}
            required: [milliliters]
      """
    And the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _forms: {}
      """
    And the service answers GET "http://garden.test/profiles/garden" with:
      """
      200 OK
      Content-Type: application/alps+json
      Cache-Control: public, max-age=86400

      alps:
        version: "1.0"
        descriptor:
          - {id: water-cell, type: unsafe, title: Water the bed, doc: {format: text, value: "Adds water to each cell. Offered only while the bed is planted."}}
          - {id: harvest, type: unsafe, title: Harvest the crop, doc: {format: text, value: "Picks the crop."}}
      """

  Scenario: The form is found by the CURIE as written or by the relation it expands to
    When the client enters "http://garden.test/beds/2"
    Then the "garden:water-cell" form's relation is "http://garden.test/profiles/garden#water-cell"
    When the client acts on "http://garden.test/profiles/garden#water-cell" with:
      """yaml
      milliliters: 250
      """
    Then the client's last request was POST "http://garden.test/beds/2/actions/water-cell"

  Scenario: The reasoner is shown each form's title and description, never its target or method
    Given the reasoner will select "garden:water-cell" with {"milliliters": 250}
    When the agent handles "water bed 2" at "http://garden.test/beds/2"
    Then the reasoner was shown the form "garden:water-cell" titled "Water the bed" described as "Pours water into every cell of a planted bed."
    And the reasoner was shown the offers "garden:water-cell" and no URI or method
    And the agent acted

  Scenario: Describing a relation fetches its ALPS descriptor once, then the cache answers
    When the client enters "http://garden.test/beds/2"
    Then the client describes "garden:water-cell" as "Adds water to each cell. Offered only while the bed is planted."
    And the client describes "garden:water-cell" as "Adds water to each cell. Offered only while the bed is planted."
    And the client sent these requests:
      | GET http://garden.test/beds/2          |
      | GET http://garden.test/profiles/garden |
    And the request for "http://garden.test/profiles/garden" had header "Accept" set to "application/alps+json"

  Scenario: The reasoner may ask for a descriptor first; it is shown the detail, then chooses
    Given the reasoner will describe "garden:water-cell", then select "garden:water-cell" with {"milliliters": 250}
    When the agent handles "what does watering do? then water bed 2" at "http://garden.test/beds/2"
    Then the reasoner was last shown "garden:water-cell" with the detail "Adds water to each cell. Offered only while the bed is planted."
    And the agent acted

  Scenario: The Claude reasoner is shown titles and descriptions, and can ask for a descriptor
    Given the Claude API will answer with a tool call describing "garden:water-cell"
    When the Claude reasoner reasons about "what does watering do?" over the manifest of "http://garden.test/beds/2"
    Then the Claude request shows "Water the bed" and "Pours water into every cell of a planted bed."
    And the Claude request contains no URI from the manifest
    And the Claude reasoner chose to describe "garden:water-cell"

  Scenario: Describing a relation the manifest does not offer is rejected before any request
    Given the reasoner will describe "garden:harvest", then select "garden:water-cell" with {"milliliters": 250}
    When the agent handles "harvest bed 2" at "http://garden.test/beds/2"
    Then the agent rejected the selection because "garden:harvest is not in the current manifest"
    And the client sent no request after entering
