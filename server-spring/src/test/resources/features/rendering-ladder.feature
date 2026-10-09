Feature: Rendering ladder L0 to L2
  SPEC §6.4 and §6.10.1-6.10.3. A client picks its rung with Prefer tokens.
  With no Prefer header the server collapses all three layers into one
  response. Each rung's affordance set is different bytes, so each has its
  own content address (§6.5), and Layer 2 never negotiates (§6.1).

  Background:
    Given bed 2 is PLANTED

  Scenario: L0 is the default: state, forms, and full schemas in one response
    When a client gets bed 2 with no Prefer header
    Then the response status is 200
    And the response embeds the "waterCell" form with its full schema
    And the response has no affordance-set link
    And the response has no Preference-Applied header

  Scenario Outline: An unrecognised Prefer token degrades to L0: <prefer>
    The tokens are forms=link and schema=link: they ask for a layer to be
    delivered by link. The earlier name, cid, is withdrawn: it read as a
    multiformats Content Identifier, which the protocol does not use.
    When a client gets bed 2 with Prefer "<prefer>"
    Then the response embeds the "waterCell" form with its full schema
    And the response has no Preference-Applied header

    Examples:
      | prefer                |
      | embed=schemas         |
      | forms=cid             |
      | forms=cid, schema=cid |

  Scenario Outline: L1 and L2 link to an affordance set rendered for their rung: Prefer <prefer>
    When a client gets bed 2 with Prefer "<prefer>"
    Then the response status is 200
    And the response has no inline forms
    And Preference-Applied is "<prefer>"
    When the client follows the affordance-set link
    Then the response status is 200
    And the "waterCell" form <schema rendering>

    Examples:
      | prefer                | schema rendering                    |
      | forms=link             | embeds its full schema              |
      | forms=link, schema=link | points to its schema by $ref and integrity |

  Scenario: L1 and L2 affordance sets have different addresses
    When a client gets bed 2 with Prefer "forms=link"
    And a client gets bed 2 with Prefer "forms=link, schema=link"
    Then the two affordance-set links differ

  Scenario Outline: Each affordance set is immutable, does not vary, and hashes to its own address: Prefer <prefer>
    When a client gets bed 2 with Prefer "<prefer>"
    And the client follows the affordance-set link
    Then the response Cache-Control is "public, max-age=31536000, immutable"
    And the response has no Vary header
    And the response body hashes to the last path segment of its URL

    Examples:
      | prefer                |
      | forms=link             |
      | forms=link, schema=link |

  Scenario Outline: The affordance-set link carries the integrity of the bytes it leads to: Prefer <prefer>
    SPEC §6.5 and HAL-SCHEMA-FORMS-HR Rule 2: a client checks Layer 2 against
    the link that led to it, never against the URL. Repr-Digest is the
    optional extra (Rule 4).
    When a client gets bed 2 with Prefer "<prefer>"
    And the client follows the affordance-set link
    Then the response body matches the integrity of the link that led to it
    And the response Repr-Digest matches its body

    Examples:
      | prefer                |
      | forms=link             |
      | forms=link, schema=link |

  Scenario: Each schema reference carries the integrity of the schema it leads to
    When a client gets bed 2 with Prefer "forms=link, schema=link"
    And the client follows the affordance-set link
    And the client follows the "waterCell" schema reference
    Then the response status is 200
    And the response body matches the integrity of the link that led to it
    And the response Repr-Digest matches its body
    And the response Cache-Control is "public, max-age=31536000, immutable"

  Scenario: Layer 2 ignores Prefer: one address, one representation
    When a client gets bed 2 with Prefer "forms=link, schema=link"
    And the client follows the affordance-set link
    And the client follows the same affordance-set link with Prefer "forms=link"
    Then both affordance-set responses have identical bytes

  Scenario: The affordance-set link is named by the heart-rise rel, in its hr: short form
    When a client gets bed 2 with Prefer "forms=link, schema=link"
    Then the response declares curie "hr" as "https://github.com/jogoshugh/heart-rise/rels/{rel}"
    And the response links to its affordance set under "hr:forms"
    And the response has no link under "http://api.example.org/rels/forms"
