Feature: Content addressing
  SPEC §6.5. Layer 2 and Layer 3 resources are named by what they contain.
  Their bytes are the RFC 8785 (JCS) canonical form, and that same form is
  hashed and served. heart-core does this for any server, whatever its domain.

  Scenario: Canonical form sorts keys and drops whitespace, from a tree or from text
    Given the JSON tree {"b": 1, "a": [true, null]}
    Then its canonical form is {"a":[true,null],"b":1}
    And the JSON text "{ \"b\" : 1, \"a\" : [ true, null ] }" has the same canonical form

  Scenario Outline: A canonical body's address, integrity and digest
    Given the canonical body <body>
    Then its address is "sha256-<hex>"
    And its integrity is "sha256-<base64>"
    And its Repr-Digest is "sha-256=:<base64>:"

    Examples:
      | body                    | hex              | base64                                       |
      | {"a":[true,null],"b":1} | 51705a2c9eb3e7e4 | UXBaLJ6z5+QQpY9pancMOsOIWgz0Prf8iPXkfBHU0w0= |
      | {}                      | 44136fa355b3678a | RBNvo1WzZ4oRRq0W9+hknpT7T8If536DEMBg9hyq/4o= |
