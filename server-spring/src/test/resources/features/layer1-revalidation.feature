Feature: Layer 1 revalidation
  SPEC §6.1, §6.2, §6.7. Layer 1 changes, so it is never content-addressed.
  Every response says no-cache and carries a strong ETag. A client holding
  that ETag revalidates with If-None-Match and gets 304 while nothing changed.
  The ETag differs per dialect and per rung, and carries the state version
  so an act's If-Match can be checked against any of them ([0.5] §6.2).

  Background:
    Given bed 2 is PLANTED

  Scenario Outline: Layer 1 is no-cache with a strong ETag per dialect and rung: <dialect>, Prefer "<prefer>"
    When a client gets bed 2 as <dialect> with Prefer "<prefer>"
    Then the response status is 200
    And the response Cache-Control is "no-cache"
    And the response ETag is <etag>

    Examples:
      | dialect          | prefer                | etag           |
      | hal-schema-forms |                       | "seq-1.hsf.L0" |
      | hal-schema-forms | forms=cid             | "seq-1.hsf.L1" |
      | hal-schema-forms | forms=cid, schema=cid | "seq-1.hsf.L2" |
      | hal-forms        |                       | "seq-1.hf.L0"  |

  Scenario Outline: Unchanged state revalidates to 304 with no body: <dialect>, Prefer "<prefer>"
    Given a client got bed 2 as <dialect> with Prefer "<prefer>"
    When the client revalidates bed 2 with the ETag it holds
    Then the response status is 304
    And the response has no body
    And the response ETag is the one the client holds
    And the response Cache-Control is "no-cache"

    Examples:
      | dialect          | prefer                |
      | hal-schema-forms |                       |
      | hal-schema-forms | forms=cid, schema=cid |
      | hal-forms        |                       |

  Scenario: Changed state revalidates to 200 with a new ETag
    Given a client got bed 2 as hal-schema-forms with Prefer "forms=cid, schema=cid"
    And someone else waters bed 2 with 100 ml
    When the client revalidates bed 2 with the ETag it holds
    Then the response status is 200
    And the response ETag is "seq-2.hsf.L2"

  Scenario: An ETag from another rung does not revalidate
    Given a client got bed 2 as hal-schema-forms with Prefer "forms=cid"
    When the client revalidates bed 2 at Prefer "forms=cid, schema=cid" with the ETag it holds
    Then the response status is 200
    And the response ETag is "seq-1.hsf.L2"

  Scenario Outline: An act accepts an If-Match from any variant of the current state: <dialect>, Prefer "<prefer>"
    Given a client got bed 2 as <dialect> with Prefer "<prefer>"
    When the client waters bed 2 with 100 ml using the ETag it holds
    Then the response status is 200

    Examples:
      | dialect          | prefer                |
      | hal-schema-forms |                       |
      | hal-schema-forms | forms=cid, schema=cid |
      | hal-forms        |                       |
