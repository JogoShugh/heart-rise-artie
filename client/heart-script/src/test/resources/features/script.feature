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

  Scenario: An act the list it names already shows is not made
    A watch says a thing once. With actOnce, before the act ARTIE
    follows that relation from where it is and looks for an item that
    already says what the act would send. Where one does, nothing is sent.
    The look is made with the act's own values, so it cannot drift from them.
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          actOnce("comment", "body" to "Fixed in 1.2.", listedIn = "comments")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | follow | comments                 |
      | return | /issues/10               |

  Scenario: An act the list it names doesn't show yet is made
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          actOnce("comment", "body" to "Fixed in 1.3.", listedIn = "comments")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                             |
      | enter  | scripted://service/              |
      | act    | issues-get {"number":10}         |
      | follow | comments                         |
      | return | /issues/10                       |
      | act    | comment {"body":"Fixed in 1.3."} |

  Scenario: A branch on facts, as a block, rather than Kotlin's if
    spec/ideas/dsl-conditions-as-facts.md. A facts block says what must hold
    on the resource held: "state" eq "open". It is ordinary Kotlin, and that
    takes any condition the script works out itself. given runs its then
    block where the facts hold, and its otherwise block where they don't.
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10) {
              given { "state" eq "open" } then { act("close") }
              given { "state" eq "closed" } then { act("comment", "body" to "Closed.") } otherwise { note("not closed") }
              given { that(1 + 1 == 2) } then { note("worked out by the script") }
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | act    | close                    |
      | return | /                        |
    And the script noted, in order
      | note                     |
      | not closed               |
      | worked out by the script |

  Scenario: A fact about a resource never kept stops the script, saying so
    A name kept nowhere is a mistake in the script, not a fact that doesn't
    hold: a watch's Until that read it so would never end.
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10) { keepAs("issue") }
          given { at("isue") { "state" eq "open" } } then { act("close") }
      }
      """
    Then the script failed: "nothing is kept as \"isue\"; keepAs(\"isue\") where it is reached"

  Scenario: A value read from a resource kept by name, with no variable to fill
    A script declares once what it reads from a resource it keeps, as it
    declares an input, and reads it after the act that reached it: no var,
    and no assignment in the act's block. The value is the resource's as it
    was when it was kept.
    When this script runs
      """kotlin
      heart("scripted://service/") {
          val title by kept("issue") { text("title") }
          act("issues-get", "number" to 10) { keepAs("issue") }
          note("kept: $title")
          act("issues-get", "number" to 10) {
              act("comment", "body" to "About: $title")
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                                         |
      | enter  | scripted://service/                          |
      | act    | issues-get {"number":10}                     |
      | return | /                                            |
      | act    | issues-get {"number":10}                     |
      | act    | comment {"body":"About: Login fails on Safari"} |
      | return | /                                            |
    And the script noted, in order
      | note                         |
      | kept: Login fails on Safari  |

  Scenario: A value read from a resource not kept yet stops the script, saying so
    When this script runs
      """kotlin
      heart("scripted://service/") {
          val title by kept("issue") { text("title") }
          note(title)
          act("issues-get", "number" to 10) { keepAs("issue") }
      }
      """
    Then the script failed: "nothing is kept as \"issue\" yet; keepAs(\"issue\") before reading it"

  Scenario: A long act's values in a block, one a line, which may be left out; the block after still visits
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          val draft = false
          act("comment", intent = "say it's fixed", fields = {
              "body" to "Fixed in 1.2."   // as the release notes say
              if (draft) "draft" to true
          }) {
              act("edit", "body" to "Fixed in 1.2. (edited)")
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                                   |
      | enter  | scripted://service/                    |
      | act    | issues-get {"number":10}               |
      | act    | comment {"body":"Fixed in 1.2."}       |
      | act    | edit {"body":"Fixed in 1.2. (edited)"} |
      | return | /issues/10                             |

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
