@heart-tck
Feature: HEART conformance over HTTP, for any declared resource type
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, step A5.9. Written once and run by
  every domain server against itself. A domain supplies a fixture: a
  resource in a state where one act is offered and another is absent, a
  valid and an invalid body for the offered act, and a search term that
  finds the resource. Nothing here names a domain.

  Background:
    Given the fixture's resource is ready

  Scenario: L0 collapses state, forms and full schemas into one revalidated response
    When a client gets the resource as hal-schema-forms
    Then the response status is 200
    And the response has Cache-Control "no-cache"
    And the response varies on "Accept, Prefer, Accept-Profile"
    And the response links its profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the response offers the offered act, with its full schema and a concrete target
    And the response does not offer the absent act

  Scenario Outline: L<n> links to a shared affordance set whose bytes match the link's integrity
    When a client gets the resource as hal-schema-forms with Prefer "<prefer>"
    Then the response status is 200
    And Preference-Applied is "<prefer>"
    And the response has no inline forms
    When the client follows the "hr:forms" link
    Then the response status is 200
    And the response has Cache-Control "public, max-age=31536000, immutable"
    And the body matches the integrity of the link that led to it
    And the offered act's target is a template, with the pointer "id" at "/id"

    Examples:
      | n | prefer                  |
      | 1 | forms=link              |
      | 2 | forms=link, schema=link |

  Scenario: An unchanged resource is revalidated with 304
    When a client gets the resource as hal-schema-forms
    And the client gets it again with the ETag it was given
    Then the response status is 304

  Scenario: Why the absent act is absent is said, with the link's integrity
    When a client gets the resource as hal-schema-forms
    And the client follows the "hr:unavailable" link
    Then the body matches the integrity of the link that led to it
    And the explanation gives the absent act's reason

  Scenario: The offered act succeeds, and answers with the resource it changed
    When a client gets the resource as hal-schema-forms
    And the client sends the offered act with its valid body and the ETag it holds
    Then the response status is 200
    And the response names the resource in Content-Location
    And the response has a new ETag

  Scenario Outline: A refused act is a problem of type <type>, and changes nothing
    When a client gets the resource as hal-schema-forms
    And the client sends <act> with <body> and <validator>
    Then the response status is <status>
    And the response is a problem of type "<type>"
    And the resource is unchanged

    Examples:
      | act             | body             | validator                 | status | type                  |
      | the offered act | its valid body   | no If-Match               | 428    | precondition-required |
      | the offered act | its valid body   | an If-Match that is stale | 412    | stale                 |
      | the absent act  | the absent body  | the ETag it holds         | 409    | not-offered           |
      | the offered act | its invalid body | the ETag it holds         | 422    | invalid-input         |

  Scenario: HAL-FORMS offers the same acts, by the same targets
    Then a hal-schema-forms client and a hal-forms client see the same act targets on the resource

  Scenario: The bookmark finds the resource, in either dialect, and as JSON Home
    When a client gets "/" as hal-schema-forms
    Then the index offers the type's search and lookup forms
    When a client follows the search form with the fixture's search term
    Then the results list the resource
    When a client gets "/" with Accept "application/json-home"
    Then the response status is 200
    And the home document names the resource's collection

  Scenario: The profile describes every relation the resource's forms use
    When a client gets the resource as hal-schema-forms
    Then every relation its forms use is described in the server's ALPS profile

  # A type whose bookmark offers an act: its fixture names one, with a valid
  # and an invalid body. A domain whose bookmark offers no act leaves these
  # out with the tag filter "not @bookmark-act".
  @bookmark-act
  Scenario: The bookmark's act brings a resource into being, answered 201 and named in Location
    When a client gets "/" as hal-schema-forms
    Then the index offers the fixture's bookmark act as a form on its own actions
    When the client sends the bookmark act with its valid body and the index's ETag
    Then the response status is 201
    And the response names the new resource in Location and in Content-Location
    And the new resource can be read where Location says

  @bookmark-act
  Scenario Outline: A refused act on the bookmark is a problem of type <type>, and adds no resource
    When the client sends the bookmark act with <body> and <validator>
    Then the response status is <status>
    And the response is a problem of type "<type>"
    And the collection holds as many resources as before

    Examples:
      | body             | validator                 | status | type                  |
      | its valid body   | no If-Match               | 428    | precondition-required |
      | its valid body   | an If-Match that is stale | 412    | stale                 |
      | its invalid body | the index's ETag          | 422    | invalid-input         |
