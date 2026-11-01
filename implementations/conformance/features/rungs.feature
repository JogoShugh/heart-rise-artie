Feature: The rungs of the ladder, L0 to L2
  A client asks for a rung with Prefer, and takes the rung of each answer from
  Preference-Applied (SPEC §6.4, §6.10). At L1 Layer 1 links to an affordance set
  that carries its schemas; at L2 the set points to them. Shared layers are
  immutable, so a client never reads the same one twice. Clients may share what
  they have read, by being given one cache.

  Scenario: Read a bed at L1, over two hops
    Given the garden is at its seed state
    And a client at rung L1 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    Then the state's "status" is "PLANTED"
    And the representation is at rung L1
    And it offers exactly "garden:water-cell"
    And every shared layer it fetched matched the integrity of its link

  Scenario: An act at L1 is conditional, and its answer is the new state
    Given the garden is at its seed state
    And a client at rung L1 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And it submits "garden:water-cell" with {"milliliters": 250}
    Then the act carried If-Match with the validator it read
    And the state's "moistureLevel" is 65
    And the representation is at rung L1
    And it offers exactly "garden:harvest"

  Scenario Outline: A state visited again costs no second read of a shared layer, at <rung>
    Given the garden is at its seed state
    And a client at rung <rung> with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And it reads the resource again
    Then no shared layer was read twice
    And it offers exactly "garden:water-cell"

    Examples:
      | rung |
      | L1   |
      | L2   |

  Scenario: L1 and L2 read different affordance sets for one state
    Given the garden is at its seed state
    And a client at rung L1 with the garden's credential
    And another client at rung L2 with the garden's credential
    When it enters at the bookmark
    And it submits "garden:get-bed" with {"id": 2}
    And the other client reads bed 2
    Then the two clients read different affordance sets

  Scenario Outline: Each rung asks for what it needs: <rung>
    Given a service offering "note" with the schema {"type": "object"}
    And a client at rung <rung> with no credential
    When it enters that service
    Then the last read asked for "<prefer>"

    Examples:
      | rung | prefer                  |
      | L1   | forms=link              |
      | L2   | forms=link, schema=link |

  Scenario: A client at L0 asks for no layering
    Given a service offering "note" with the schema {"type": "object"}
    And a client at rung L0 with no credential
    When it enters that service
    Then the last read asked for no layering

  Scenario Outline: The answer says the rung, whatever was asked: <applied>
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "<applied>", with a true affordance set
    And a client at rung L2 with no credential
    When it enters that service
    Then the representation is at rung <rung>
    And it offers exactly "from-the-set"

    Examples:
      | applied                 | rung |
      | forms=link              | L1   |
      | forms=link, schema=link | L2   |
      | schema=link, forms=link | L2   |

  Scenario: A server that ignores Prefer leaves a client at L0
    Given a service offering "note" with the schema {"type": "object"}
    And a client at rung L1 with no credential
    When it enters that service
    Then the representation is at rung L0
    And it offers exactly "note"

  Scenario: A token that only resembles forms=link applies nothing
    Given a service offering "note" with the schema {"type": "object"}
    And that service says it applied "forms=linked"
    And a client at rung L1 with no credential
    When it enters that service
    Then the representation is at rung L0

  Scenario Outline: The affordance set is found by its relation: <case>
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set linked as "<rel>"
    And that service declares the curie "<curie>" for "https://w3id.org/heart-rise/rels/{rel}"
    And a client at rung L1 with no credential
    When it enters that service
    Then it offers exactly "from-the-set"

    Examples:
      | case                | rel                                   | curie  |
      | the relation itself | https://w3id.org/heart-rise/rels/forms | hr    |
      | the usual prefix    | hr:forms                              | hr     |
      | another prefix      | garden-hr:forms                       | garden-hr |

  Scenario: The prefix hr alone means the relation, if no curie says otherwise
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set linked as "hr:forms"
    And a client at rung L1 with no credential
    When it enters that service
    Then it offers exactly "from-the-set"

  Scenario: A link that shares the name but not the relation is no affordance set
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set linked as "hr:forms"
    And that service declares the curie "hr" for "https://example.com/rels/{rel}"
    And a client at rung L1 with no credential
    When it tries to enter that service
    Then it failed, saying "applied L1 but links no affordance set"

  Scenario: A set that fails its integrity is refused at L1
    Given a service whose affordance set does not match the integrity of its link
    And a client at rung L1 with no credential
    When it tries to enter that service
    Then it refused the affordance set for its integrity

  Scenario: Clients given one cache read a shared layer once between them
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set
    And two clients at rung L1 sharing one cache
    When it enters that service
    And the other client enters that service
    Then the affordance set was read 1 times

  Scenario: Clients given no cache each keep their own
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set
    And two clients at rung L1 with their own caches
    When it enters that service
    And the other client enters that service
    Then the affordance set was read 2 times

  Scenario: A new integrity at the same address is read as new
    Given a service offering "note" with the schema {"type": "object"}
    And that service applies "forms=link", with a true affordance set
    And a client at rung L1 with no credential
    When it enters that service
    And that service's affordance set now offers "changed"
    And it reads the resource again
    Then it offers exactly "changed"
    And the affordance set was read 2 times

  Scenario: Fields of a DELETE stay out of its address, unless its template takes them
    Given a service offering "note" with the schema {"type": "object"}
    And that service offers "note" by DELETE
    And a client at rung L0 with no credential
    When it enters that service
    And it submits "note" with {"a": 1}
    Then the last request was for "/actions/note"

  Scenario: Fields of a HEAD go in the query in HAL-FORMS, with no body
    Given a HAL-FORMS service whose template "note" is {"method": "HEAD", "target": "/find"}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it tries to submit "note" with {"a": "b"}
    Then the last request was HEAD with the body ""
    And the last request was for "/find?a=b"

  Scenario: The HAL-FORMS media type may carry a parameter
    Given a HAL-FORMS service offering "note" with the properties []
    And that service names no profile
    And that service's media type is "application/prs.hal-forms+json;charset=utf-8"
    And a HAL-FORMS client with no credential
    When it enters that service
    Then the representation is at rung L0, in HAL-FORMS

  Scenario: Options that are not an object are no options
    Given a HAL-FORMS service offering "note" with the properties [{"name": "s", "options": "text"}]
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {"s": "a"}
    Then the act "note" was sent 1 times

  Scenario: A HAL-FORMS target is never a template
    Given a HAL-FORMS service whose template "note" is {"method": "POST", "target": "/a{b}"}
    And a HAL-FORMS client with no credential
    When it enters that service
    And it submits "note" with {"b": "c"}
    Then the last request was for "/a%7Bb%7D"
