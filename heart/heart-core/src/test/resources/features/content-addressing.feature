Feature: Content addressing
  SPEC §6.5. Layer 2 and Layer 3 resources are named by what they contain.
  Their bytes are the RFC 8785 (JCS) canonical form, and that same form is
  hashed and served. heart-core does this for any server, whatever its domain.
  An address is the first 16 hex digits of the SHA-256 digest: a short name,
  with no "sha256-" in front, because it is not a full digest and claims to be
  none. The integrity value carries the full digest, and only it is checked.

  Scenario: Canonical form sorts keys and drops whitespace, from a tree or from text
    Given the JSON tree {"b": 1, "a": [true, null]}
    Then its canonical form is {"a":[true,null],"b":1}
    And the JSON text "{ \"b\" : 1, \"a\" : [ true, null ] }" has the same canonical form

  Scenario Outline: A canonical body's address, integrity and digest
    Given the canonical body <body>
    Then its address is "<hex>"
    And its integrity is "sha256-<base64>"
    And its Repr-Digest is "sha-256=:<base64>:"

    Examples:
      | body                    | hex              | base64                                       |
      | {"a":[true,null],"b":1} | 51705a2c9eb3e7e4 | UXBaLJ6z5+QQpY9pancMOsOIWgz0Prf8iPXkfBHU0w0= |
      | {}                      | 44136fa355b3678a | RBNvo1WzZ4oRRq0W9+hknpT7T8If536DEMBg9hyq/4o= |

  Scenario: Bodies are served by their address, and one body met twice is one resource
    When the bodies {"a":1}, {"a":2} and {"a":1} are named by their addresses
    Then 2 bodies are named
    And each named body hashes to its name

  Scenario: Two different bodies that would share a name are refused, never one dropped
    Sixteen hex digits make a clash among a server's few dozen bodies about
    one chance in ten quadrillion. If it ever happens, the server refuses to
    start: a map keyed by address would otherwise keep one body and drop the
    other without a word, and a client would meet the drop only as an
    integrity mismatch. To force the clash, every body is given one name.
    When the bodies {"a":1} and {"a":2} are named by a naming that gives every body the name "0"
    Then naming them is refused, saying two different bodies share the name "0"

  Scenario: Content shared by two resource types is held once across the server
    Every binding serves Layers 2 and 3 of all declared types from one place,
    named the same way, so a clash between two types is refused as well.
    When the content of the lamp type, declared twice, is gathered for the server
    Then the server holds each affordance set, schema and explanation of the lamp type once
