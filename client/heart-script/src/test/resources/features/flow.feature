Feature: Waiting for a server to settle, and walking a list's members
  spec/PLAN-ARTIE-SCRIPTS.md, "Also". A server may take a moment to settle
  after an act: `until` reads the held resource again until a condition
  holds, or stops the script when it never does. `members` walks the
  members a list links under `item`, page after page by `next`, visiting
  each and coming back; the script never builds a page's address.

  Background:
    Given a service holds these resources
      | address    | title                 | state  |
      | /          | Repositories          |        |
      | /issues/10 | Login fails on Safari | open   |
      | /list/1    | Issues, page 1        |        |
      | /list/2    | Issues, page 2        |        |
      | /issues/11 | Safari 17 cookies     | open   |
      | /issues/12 | Safari 16 redirects   | open   |
      | /issues/13 | Chrome is fine        | closed |
    And these forms
      | at | relation   | leads to   | outcome |
      | /  | issues-get | /issues/10 | done    |
      | /  | issues     | /list/1    | done    |
    And these links
      | at      | relation | title               | to         |
      | /list/1 | item     | Safari 17 cookies   | /issues/11 |
      | /list/1 | item     | Safari 16 redirects | /issues/12 |
      | /list/1 | next     |                     | /list/2    |
      | /list/2 | item     | Chrome is fine      | /issues/13 |

  Scenario: until reads the resource again until the condition holds
    Given "/issues/10" becomes "closed" after 2 more reads
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          until(tries = 5) { state["/state"] == "closed" }
          note("now ${state["/state"]}")
      }
      """
    Then the script finished
    And the script noted "now closed"
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | return | /issues/10               |
      | return | /issues/10               |

  Scenario: until stops the script when the condition never holds
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          until(tries = 3) { state["/state"] == "closed" }
      }
      """
    Then the script stopped at step 2, "until": "the condition did not hold after 3 tries"

  Scenario: members visits each member of every page, coming back each time
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues")
          members { note("${state["/title"]}: ${state["/state"]}") }
      }
      """
    Then the script finished
    And the script noted, in order
      | note                      |
      | Safari 17 cookies: open   |
      | Safari 16 redirects: open |
      | Chrome is fine: closed    |
    And the client was asked, in order
      | call   | what                         |
      | enter  | scripted://service/          |
      | act    | issues                       |
      | follow | item "Safari 17 cookies"     |
      | return | /list/1                      |
      | follow | item "Safari 16 redirects"   |
      | return | /list/1                      |
      | follow | next                         |
      | follow | item "Chrome is fine"        |
      | return | /list/2                      |
      | return | /list/1                      |
