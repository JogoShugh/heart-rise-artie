Feature: Monday: the garden as an OpenAPI 3.1 description
  From OpenAPI to HEART, part two. The garden serves, at /openapi.json, a
  careful OpenAPI 3.1 document of everything it could ever be asked,
  generated from its declarations: one operation per act, each act's schema
  in components, the bed's status as a closed enum, and the rule about state
  as a sentence. It is published beside the exhibit as garden-1.0.0.json,
  and the server must keep serving exactly those bytes until the garden
  changes on purpose. Tuesday's version is in frost.feature.

  Scenario: With affordance links switched on, the description says so, in a version of its own
    heart.affordance-links (HEART_AFFORDANCE_LINKS) makes each plain answer
    link its bed's affordance set. That changes the description's bytes, so
    it is a release of its own, and the published 1.0.0 stays as it was.
    Given the garden serves affordance links
    When the garden's OpenAPI description is built
    Then the built description's version is "1.0.0+affordance-links"
    And the built description documents the Link header on "getBed" and "waterCell"
    And the built description names the affordance relation "https://w3id.org/heart-rise/rels/forms"

  Scenario: Monday's OpenAPI description is the published version 1.0.0, byte for byte
    When a client gets the OpenAPI description
    Then it is the published "garden-1.0.0.json"
    And its info version is "1.0.0"
    And its "Bed" status enum is "EMPTY, PLANTED, READY_TO_HARVEST"
    And the description of "waterCell" ends "Only allowed when status is PLANTED."
    And the operation "waterCell" takes the schema "WaterCellRequest", which is the water-cell form's schema
