Feature: Explaining why an act is not offered
  SPEC §6.12 (2026-10-10). An affordance set (or Layer 1, at L0) may link
  under hr:unavailable, with integrity, to a list of the acts absent in this
  state, each with a reason. The client fetches it only when asked why,
  checks its bytes, and never acts on what it finds there. Without such a
  link the client says nothing about why.

  Background:
    Given the service serves explanation "planted" at its content address:
      """yaml
      unavailable:
        - {rel: "/profiles/garden#sow-seed", title: Sow a seed, reason: The bed is already planted.}
        - {rel: "/profiles/garden#harvest", title: Harvest the crop, reason: The crop is not ready yet.}
      """
    And the service serves affordance set "planted" at its content address:
      """yaml
      _links:
        curies:
          - {name: garden, href: "/profiles/garden#{rel}", templated: true}
          - {name: hr, href: "https://w3id.org/heart-rise/rels/{rel}", templated: true}
        hr:unavailable: {href: "{unavailable:planted}", integrity: "{integrity:unavailable:planted}"}
      _forms:
        garden:water-cell:
          title: Water the bed
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          hr:templatePointers: {id: /id}
          method: POST
          schema: {type: object}
      """
    And bed 2 links to affordance set "planted"
    And the client works at rung L2

  Scenario: Asking why fetches the explanation once, checks it, and gives the reason
    When the client enters "http://garden.test/beds/2"
    Then the client explains "garden:harvest" as "The crop is not ready yet."
    And the client explains "http://garden.test/profiles/garden#sow-seed" as "The bed is already planted."
    And the client sent these requests:
      | GET http://garden.test/beds/2                 |
      | GET http://garden.test{set:planted}           |
      | GET http://garden.test{unavailable:planted}   |

  Scenario: An act that is offered, or one the list does not name, has no explanation
    When the client enters "http://garden.test/beds/2"
    Then the client has no explanation for "garden:water-cell"
    And the client has no explanation for "garden:prune"

  Scenario: An explanation whose bytes do not match the link's integrity is refused
    Given the service serves explanation "other" at its content address:
      """yaml
      unavailable: []
      """
    And the service serves affordance set "lying" at its content address:
      """yaml
      _links:
        curies: [{name: hr, href: "https://w3id.org/heart-rise/rels/{rel}", templated: true}]
        hr:unavailable: {href: "{unavailable:planted}", integrity: "{integrity:unavailable:other}"}
      _forms: {}
      """
    And bed 3 links to affordance set "lying"
    When the client enters "http://garden.test/beds/3"
    And the client tries to explain "garden:harvest"
    Then the client raised an error mentioning "integrity"

  Scenario: The reasoner can ask why; ARTIE answers from the explanation and sends no act
    Given the reasoner will ask why "garden:harvest" is not offered
    When the agent handles "why can't I harvest bed 2?" at "http://garden.test/beds/2"
    Then the agent explained "garden:harvest" as "The crop is not ready yet."
    And the client sent no request after entering

  Scenario: Without an hr:unavailable link the agent says it was given no reason
    Given the service answers GET "http://garden.test/beds/7" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 7
      _links: {self: {href: /beds/7}}
      _forms: {}
      """
    And the client works at rung L0
    And the reasoner will ask why "garden:harvest" is not offered
    When the agent handles "why can't I harvest bed 7?" at "http://garden.test/beds/7"
    Then the agent explained "garden:harvest" with no reason

  Scenario: The Claude reasoner can ask why an act is not offered
    Given the Claude API will answer with a tool call explaining "garden:harvest"
    When the Claude reasoner reasons about "why can't I harvest?" over the manifest of "http://garden.test/beds/2"
    Then the Claude reasoner chose to explain "garden:harvest"
