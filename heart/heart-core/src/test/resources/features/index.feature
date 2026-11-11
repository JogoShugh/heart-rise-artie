Feature: The service index, the home document and search pages, from the declarations
  SPEC §6.11 and §6.11.1. Fielding: a REST API is entered with no prior
  knowledge beyond the initial URI. heart-core builds the bookmark's
  representations from the declared resource types: a search form and a
  lookup form for each, the JSON Home document that says what the server
  serves, and the pages of a search. Every binding serves these same bodies.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamps 1 to 5 exist

  Scenario: The index declares the hr CURIE it names its members and links by, once, at its absolute address
    When the service index is rendered in HAL Schema Forms
    Then the index declares these CURIEs
      | name | href                                    |
      | home | /profiles/home#{rel}                    |
      | hr   | https://w3id.org/heart-rise/rels/{rel} |

  Scenario: A server that signs links the keys it signs with from its index
    Given the server publishes the keys it signs with
    When the service index is rendered in HAL Schema Forms
    Then the index links "hr:keys" to "/keys", as "application/jwk-set+json"

  Scenario: At L0 every form the index offers names its schema with $id, and the schema is served there
    HAL-SCHEMA-FORMS-HR Rule 3: a schema in place names itself with $id, the address it is served at apart.
    When the service index is rendered in HAL Schema Forms
    Then every form it holds names its schema with $id, and the schema is served at that address

  Scenario Outline: Asked for forms by link, the index holds no forms and links its affordance set, as a resource does
    SPEC §6.10. The entry is a resource like any other: at L1 and L2 its forms are in a shared, content-addressed
    set linked by hr:forms with that set's integrity. At L2 each form there links its schema by $ref, with
    hr:integrity beside it (Rule 3); at L1 each holds its schema in place, named by $id.
    When the service index is rendered in HAL Schema Forms at <rung>
    Then it holds no forms of its own
    And it links "hr:forms" to its affordance set, served at that address, with the set's integrity
    And every form in that set <schemas>

    Examples:
      | rung | schemas                                                                 |
      | L1   | names its schema with $id, and the schema is served at that address    |
      | L2   | links its schema by $ref, with the integrity of the schema served there |

  Scenario: In HAL Schema Forms, the index offers a search form and a lookup form per type
    When the service index is rendered in HAL Schema Forms
    Then the index is titled "Lamps" and links "self" to "/"
    And it offers these GET forms
      | form              | target        | required field |
      | home:search-lamps | /lamps{?name} | name           |
      | home:get-lamp     | /lamps/{id}   | id             |

  Scenario: Every form the index offers carries its own integrity, as a resource's forms do
    HAL-SCHEMA-FORMS-HR Rule 10: a replaying client pins the forms it uses, the
    bookmark's lookups and searches among them. A form's hr:integrity is the
    integrity, in canonical form, of the members it has of these: its target,
    its template pointers, its method, its content type, and its schema's
    integrity. Not its title or description.
    When the service index is rendered in HAL Schema Forms
    Then every form it offers carries the integrity of what it does
      | form              |
      | home:search-lamps |
      | home:get-lamp     |

  Scenario: The index lists every form the server declares, with its integrity, and a digest of them all
    spec/ideas/off-the-rails.md, look then leap. A client's one look at the
    entry should see whether any form it will use has changed, not only the
    entry's own. Forms are declared, not made from a resource's state: each
    one's integrity is known when the server starts, with nothing read. The
    index lists, by relation, the integrity of every form the server can
    offer, the index's own and every type's acts, and a digest over that
    list: one value that changes when any form does.
    When the service index is rendered in HAL Schema Forms
    Then its manifest lists, for each relation, the integrity of the form served for it
      | relation          | served by                    |
      | home:search-lamps | the index                    |
      | home:get-lamp     | the index                    |
      | home:install-lamp | the index                    |
      | home:switch-on    | a lamp's set of forms        |
      | home:switch-off   | a lamp's set of forms        |
      | home:dim          | a lamp's set of forms        |
    And the manifest's digest is the integrity of its forms, in canonical form

  Scenario: In HAL-FORMS, the same two forms are GET templates whose fields go in the query
    When the service index is rendered in HAL-FORMS
    Then it offers these GET templates
      | template    | target | required property |
      | searchLamps | /lamps | name              |
      | getLamp     | /lamps | id                |

  Scenario: The index's validator names its bytes, so it changes only when they do
    Then the index's validator in each dialect is "index-" with the dialect's tag and the first 16 hex digits of its body's digest

  Scenario Outline: The home document says what a server capped at <max> serves
    When the home document is rendered for a server capped at <max>
    Then it names these resources at their templates
      | resource   | template         |
      | item       | /lamps/{id}      |
      | collection | /lamps{?name,id} |
    And the "item" hints offer both dialects with their profiles, and need an ETag to act
    And the "item" hint "accept-prefer" is <declared>

    Examples:
      | max | declared                  |
      | L2  | "forms=link, schema=link" |
      | L0  | absent                    |

  Scenario: Each search page lists its items, titled, and pages by IANA relations
    Then each page of the lamps matching "", 2 to a page, counts 5 and lists
      | page | href     | title  |
      | 1    | /lamps/1 | Lamp 1 |
      | 1    | /lamps/2 | Lamp 2 |
      | 2    | /lamps/3 | Lamp 3 |
      | 2    | /lamps/4 | Lamp 4 |
      | 3    | /lamps/5 | Lamp 5 |
    And each page of the lamps matching "", 2 to a page, links
      | page | rel   | href          |
      | 1    | first | /lamps        |
      | 1    | last  | /lamps?page=3 |
      | 1    | next  | /lamps?page=2 |
      | 2    | first | /lamps        |
      | 2    | last  | /lamps?page=3 |
      | 2    | next  | /lamps?page=3 |
      | 2    | prev  | /lamps        |
      | 3    | first | /lamps        |
      | 3    | last  | /lamps?page=3 |
      | 3    | prev  | /lamps?page=2 |
