Feature: A HEART script: the Reason step written in advance
  spec/PLAN-ARTIE-SCRIPTS.md, "The DSL". A script makes selections, as a
  reasoner would: a form with field values, a link by relation and title.
  It names no address but its entry, no method, no header, no validator.
  A block after a selection visits what it led to, then returns to where
  the script was, synced again. The scripts here run against a scripted
  service: its resources, forms and links in tables, and a record of what
  the client was asked to do.

  Background:
    Given a service holds these resources
      | address    | title                 | state  |
      | /          | Repositories          |        |
      | /issues/10 | Login fails on Safari | open   |
      | /comments  | Comments              |        |
      | /c/501     | It fails on Safari 17 |        |
      | /c/502     | Fixed in 1.2.         |        |
    And these forms
      | at         | relation   | leads to   | outcome      |
      | /          | issues-get | /issues/10 | done         |
      | /issues/10 | comment    | /c/502     | new resource |
      | /issues/10 | close      | /issues/10 | done         |
      | /c/502     | edit       | /c/502     | done         |
    And these links
      | at         | relation | title                 | to        |
      | /issues/10 | comments |                       | /comments |
      | /comments  | item     | It fails on Safari 17 | /c/501    |
      | /comments  | item     | Fixed in 1.2.         | /c/502    |

  Scenario: Forms and links, a block that visits and returns, and nothing but the entry named
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("comment", "body" to "Fixed in 1.2.") {
              act("edit", "body" to "Fixed in 1.2. (edited)")
          }
          follow("comments") {
              follow("item", title = "It fails on Safari 17")
          }
          act("close")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                                      |
      | enter  | scripted://service/                       |
      | act    | issues-get {"number":10}                  |
      | act    | comment {"body":"Fixed in 1.2."}          |
      | act    | edit {"body":"Fixed in 1.2. (edited)"}    |
      | return | /issues/10                                |
      | follow | comments                                  |
      | follow | item "It fails on Safari 17"              |
      | return | /issues/10                                |
      | act    | close                                     |

  Scenario: Reading the representation: state, offers, links, and why an act is absent
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          check(state["/title"] == "Login fails on Safari")
          check(state["/missing"] == null)
          check(offers("comment"))
          check(!offers("reopen"))
          check(links("comments").size == 1)
          note(why("reopen") ?: "no reason given")
      }
      """
    Then the script finished
    And the script noted "The issue is already open."

  Scenario: An act the representation doesn't offer stops the script, with the server's reason
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("reopen")
          act("close")
      }
      """
    Then the script stopped at step 2, "reopen": not offered, refused by the client: "The issue is already open."
    And the client was asked, in order
      | call  | what                     |
      | enter | scripted://service/      |
      | act   | issues-get {"number":10} |

  Scenario: A refusal from the server stops the script, saying the server refused
    Given the server will refuse "close" at "/issues/10" as stale: "The issue changed since it was read."
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("close")
      }
      """
    Then the script stopped at step 2, "close": stale, refused by the server: "The issue changed since it was read."

  Scenario: A failed check stops the script at that step
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          check(state["/state"] == "closed") { "the issue should be closed" }
      }
      """
    Then the script stopped at step 2, "check": "the issue should be closed"
