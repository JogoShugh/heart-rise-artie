Feature: Explaining absent acts
  SPEC §6.12 (2026-10-10). An affordance set links, under hr:unavailable and
  with integrity, to a resource listing the acts absent in this state, each
  by relation with a short reason. It is a function of state alone, so it
  is shared and immutable like the set; at L0 the link is in Layer 1.

  Background:
    Given bed 2 is PLANTED

  Scenario Outline: The affordance set links to the explanation, which hashes to its integrity: Prefer <prefer>
    When a client gets bed 2 with Prefer "<prefer>"
    And the client follows the affordance-set link
    Then the response declares the CURIE "hr" as "https://github.com/jogoshugh/heart-rise/rels/{rel}"
    When the client follows the "hr:unavailable" link, keeping its integrity
    Then the response status is 200
    And the response Cache-Control is "public, max-age=31536000, immutable"
    And the response body matches the integrity of the link that led to it
    And the response explains "/profiles/garden#sow-seed" and "/profiles/garden#harvest"

    Examples:
      | prefer                  |
      | forms=link              |
      | forms=link, schema=link |

  Scenario: At L0 the link is in Layer 1
    When a client gets bed 2 with no Prefer header
    And the client follows the "hr:unavailable" link
    Then the response status is 200
    And the response explains "/profiles/garden#sow-seed" and "/profiles/garden#harvest"

  Scenario: Two beds in the same state share one explanation
    Given bed 4 is PLANTED
    When a client gets bed 2 with no Prefer header
    And a client gets bed 4 with no Prefer header
    Then the two "hr:unavailable" links are the same
