Feature: HTML for people: a resource read in a browser
  spec/PLAN-HTML-DIALECT.md, decided 2026-10-30. A browser names text/html in
  Accept, so it gets HTML, which heart-core renders (heart-core's
  html.feature says what it shows). HTML is an on-ramp outside the profile,
  as plain JSON is: chosen only when Accept names it above both dialects,
  validated by its own ETag, and held to offering exactly the acts the JSON
  offers. Its scripts and stylesheet are the server's own: no CDN.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario Outline: HTML is chosen only when Accept names it above both dialects: <who>
    When a client gets "/lamps/7" with Accept "<accept>"
    Then the response status is 200
    And the response has Content-Type "<type>"

    Examples:
      | who                         | accept                                                          | type                           |
      | a browser                   | text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8 | text/html;charset=UTF-8        |
      | a client that names nothing | */*                                                             | application/prs.hal-forms+json |
      | an agent that prefers HEART | application/hal+json, text/html;q=0.5                           | application/hal+json           |

  Scenario: The HTML has its own validator, and is revalidated like any representation
    When a client gets "/lamps/7" as html
    Then the response has an ETag
    And the response has Cache-Control "no-cache"
    And the response has Vary "Accept"
    And the HTML's forms send the response's ETag as If-Match
    When a client gets "/lamps/7" again with the ETag it was given
    Then the response status is 304

  Scenario Outline: The HTML offers exactly the acts the JSON offers: lamp <status>
    Given lamp 7 is "<status>" at version 3 in the lamp server
    Then the HTML of "/lamps/7" offers exactly the forms of its hal-schema-forms representation

    Examples:
      | status |
      | ON     |
      | OFF    |

  Scenario: The scripts and stylesheet the HTML names are the server's own, immutable
    When a client gets "/lamps/7" as html
    Then each asset the HTML names is served by the server with Cache-Control "public, max-age=31536000, immutable"
