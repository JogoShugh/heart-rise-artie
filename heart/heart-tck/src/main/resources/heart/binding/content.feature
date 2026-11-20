Feature: Content-addressed resources and profiles, served over HTTP from a declaration
  SPEC §6.5, §6.12 and §8.5. A server that declares a resource type gets its
  affordance sets, schemas, absence explanations and ALPS profile served by
  its HTTP binding, with no handler of its own. Each content-addressed body
  is served as the exact bytes its address names, and never changes.

  Background:
    Given a server that declares only the lamp, with the profile "home"

  Scenario Outline: Every <kind> is served immutable, as the bytes its address names
    When a client gets every <kind> of the lamp by its address
    Then each answers 200 with Content-Type "<type>"
    And each has Cache-Control "public, max-age=31536000, immutable"
    And each body hashes to its address and matches its Repr-Digest

    Examples:
      | kind           | type                    |
      | affordance set | application/hal+json    |
      | schema         | application/schema+json |
      | explanation    | application/json        |

  Scenario Outline: An address the server does not hold is not found: <path>
    When a client gets "<path>"
    Then the response status is 404

    Examples:
      | path                                     |
      | /affordance-sets/0000000000000000 |
      | /schemas/0000000000000000         |
      | /unavailable/0000000000000000     |

  Scenario: The ALPS profile describes every relation, and can be revalidated
    When a client gets "/profiles/home"
    Then the response status is 200
    And the response has Content-Type "application/alps+json"
    And the response has Cache-Control "public, max-age=86400"
    And the response has an ETag
    And the profile describes these relations
      | relation     |
      | switch-on    |
      | switch-off   |
      | dim          |
      | install-lamp |
      | search-lamps |
      | get-lamp     |
