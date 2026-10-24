Feature: A script reads embedded resources in place, and follows them to act
  SPEC §6.1, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md). A representation
  may embed other resources, each by a relation, with a title and a state.
  A script reads them where they are, with no request: `embedded(rel)` gives
  each one's title and state. It follows an embedded relation like a link,
  by relation and title, which fetches the resource so it can act on it.
  The trace records what each step held embedded.

  Background:
    Given a service holds these resources
      | address    | title                  | state |
      | /          | Pull requests          |       |
      | /pulls/482 | Refuse blank bed names | open  |
      | /users/ada | Ada                    |       |
      | /users/lin | Lin                    |       |
      | /users/kay | Kay                    |       |
    And these forms
      | at         | relation  | leads to   | outcome |
      | /          | pulls-get | /pulls/482 | done    |
    And these embedded resources
      | at         | relation  | resource   |
      | /pulls/482 | author    | /users/ada |
      | /pulls/482 | reviewers | /users/lin |
      | /pulls/482 | reviewers | /users/kay |

  Scenario: Embedded resources are read in place, with no request
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("pulls-get", "number" to 482) {
              note("written by ${embedded("author").single().title}")
              note("reviewed by ${embedded("reviewers").map { it.title }}")
          }
      }
      """
    Then the script finished
    And the script noted, in order
      | note                     |
      | written by Ada           |
      | reviewed by [Lin, Kay]   |
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | pulls-get {"number":482} |
      | return | /                        |

  Scenario: An embedded relation is followed like a link
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("pulls-get", "number" to 482)
          follow("reviewers", title = "Kay")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | pulls-get {"number":482} |
      | follow | reviewers "Kay"          |

  Scenario: An embedded resource the representation doesn't hold is refused by the client
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("pulls-get", "number" to 482)
          follow("reviewers", title = "Ada")
      }
      """
    Then the script stopped at step 2, "reviewers": not offered, refused by the client: "no "reviewers" link titled "Ada""

  Scenario: The trace records what each step held embedded
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("pulls-get", "number" to 482)
      }
      """
    Then trace step 2 embeds these resources
      | relation  | title |
      | author    | Ada   |
      | reviewers | Lin   |
      | reviewers | Kay   |
