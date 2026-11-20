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

  Scenario: A browser's act is its form's fields, under the HTML's validator, and answers with the new page
    When a browser sends PUT "/lamps/7/actions/dim" with "brightness=20&label=" and the HTML's ETag
    Then the response status is 200
    And the response has Content-Type "text/html;charset=UTF-8"
    And the response has HX-Trigger "{\"heart:done\":\"Dim the lamp: done.\"}"
    And the page shows "brightness" as "20"
    And lamp 7 has brightness 20 at version 4

  Scenario: A browser's act under the JSON's validator is stale: the HTML is another representation
    When a browser sends PUT "/lamps/7/actions/dim" with "brightness=20" and the ETag of its hal-schema-forms representation
    Then the response status is 412
    And the response is an HTML problem reading "The act is stale: the state has changed"
    And lamp 7 is still at version 3

  Scenario: A browser's input its schema refuses is refused as HTML, at its field
    When a browser sends PUT "/lamps/7/actions/dim" with "brightness=500" and the HTML's ETag
    Then the response status is 422
    And the response is an HTML problem reading "The body does not match the form's schema"
    And the HTML problem names the field "/brightness"
    And lamp 7 is still at version 3

  Scenario: An act the lamp does not offer is refused as HTML, with why
    When a browser sends POST "/lamps/7/actions/switch-on" with "" and the HTML's ETag
    Then the response status is 409
    And the response is an HTML problem reading "The act is not offered in the resource's current state"
    And lamp 7 is still at version 3

  Scenario: The bookmark is a page too, and its act takes the browser to the lamp it installs
    When a client gets "/" as html
    Then the response status is 200
    And the response has Content-Type "text/html;charset=UTF-8"
    When a browser sends POST "/actions/install-lamp" with "brightness=30" and the bookmark's ETag
    Then the response status is 201
    And the response has Location "/lamps/8"
    And the response has HX-Redirect "/lamps/8"

  Scenario: A search is a grid of its matches, each a row linking to its lamp
    Given lamps 1 to 2 are "ON" in the lamp server
    When a client gets "/lamps?name=lamp" as html
    Then the response status is 200
    And the grid lists
      | href     | Title  |
      | /lamps/1 | Lamp 1 |
      | /lamps/2 | Lamp 2 |
      | /lamps/7 | Lamp 7 |

  Scenario: The scripts and stylesheet the HTML names are the server's own, immutable
    When a client gets "/lamps/7" as html
    Then each asset the HTML names is served by the server with Cache-Control "public, max-age=31536000, immutable"
