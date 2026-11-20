Feature: A search page embeds a summary of each match
  SPEC §6.11, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md, phase 5). A page
  lists its matches under the IANA `item` relation, each titled. When the
  server found a match with a summary of its state, the page also embeds it,
  under `_embedded.item`, with its own self link and title, so a client reads
  every match without one request each. The `item` links stay: a client that
  follows them still works. An embedded item carries no forms; a client
  follows it to act. The plain JSON page is unchanged.

  Background:
    Given a lamp resource type declared with the profile "home"

  Scenario: Each match with a summary is embedded beside its item link
    Given these lamps are found, with summaries
      | id | title  | status |
      | 1  | Lamp 1 | on     |
      | 2  | Lamp 2 | off    |
    When page 1 of the matches is rendered, 20 to a page
    Then the page embeds these items
      | self     | title  | status |
      | /lamps/1 | Lamp 1 | on     |
      | /lamps/2 | Lamp 2 | off    |
    And the page links these items
      | href     | title  |
      | /lamps/1 | Lamp 1 |
      | /lamps/2 | Lamp 2 |
    And no embedded item carries forms

  Scenario: Only the page's own matches are embedded
    Given these lamps are found, with summaries
      | id | title  | status |
      | 1  | Lamp 1 | on     |
      | 2  | Lamp 2 | off    |
      | 3  | Lamp 3 | on     |
    When page 2 of the matches is rendered, 2 to a page
    Then the page embeds these items
      | self     | title  | status |
      | /lamps/3 | Lamp 3 | on     |

  Scenario: Matches found without a summary are only linked
    Given these lamps are found, with no summary
      | id | title  |
      | 1  | Lamp 1 |
    When page 1 of the matches is rendered, 20 to a page
    Then the page embeds nothing
    And the page links these items
      | href     | title  |
      | /lamps/1 | Lamp 1 |

  Scenario: The plain JSON page is unchanged
    Given these lamps are found, with summaries
      | id | title  | status |
      | 1  | Lamp 1 | on     |
    When page 1 of the matches is rendered in plain JSON, 20 to a page
    Then the plain page is exactly {"count":1,"items":[{"id":1,"title":"Lamp 1"}]}
