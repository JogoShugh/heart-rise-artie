Feature: A script decides again on what is there now, after its act was refused as stale
  exhibits/native-vs-heart, the meddler. While a script runs, a teammate
  changes what it is working on: adds a note to the pull request's
  description it is about to edit. The script's act is refused as stale,
  and ARTIE won't send it again over what the teammate wrote. A script
  that computes its values from the state it holds can do better than
  stop: `decide { }` runs its block, and when an act in it is refused as
  stale, reads the held resource again and runs the block again, its
  values now worked out from what the teammate left. Any other refusal
  stops the script at once, as before; after its tries, so does a stale
  one.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
    And these forms
      | at         | relation   | leads to   | outcome |
      | /          | issues-get | /issues/10 | done    |
      | /issues/10 | close      | /issues/10 | done    |

  Scenario Outline: decide runs its block again after a stale refusal: <case>
    Given the server will refuse "close" at "/issues/10" as stale <times> times: "The issue changed since it was read."
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          decide(tries = 2) {
              act("close", "reason" to "seen ${state["/state"]}")
          }
      }
      """
    Then <ended>
    And the client was asked, in order
      | call   | what                                 |
      | enter  | scripted://service/                  |
      | act    | issues-get {"number":10}             |
      | act    | close {"reason":"seen open"}         |
      | return | /issues/10                           |
      | act    | close {"reason":"seen open"}         |

    Examples:
      | case                         | times | ended                                                                                                  |
      | refused once: decided again  | 1     | the script finished                                                                                    |
      | refused on every try: stops  | 2     | the script stopped at step 4, "close": stale, refused by the server: "The issue changed since it was read." |

  Scenario: Any other refusal in decide stops the script at once, reading nothing again
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          decide { act("reopen") }
      }
      """
    Then the script stopped at step 2, "reopen": not offered, refused by the client: "The issue is already open."
    And the client was asked, in order
      | call  | what                     |
      | enter | scripted://service/      |
      | act   | issues-get {"number":10} |
