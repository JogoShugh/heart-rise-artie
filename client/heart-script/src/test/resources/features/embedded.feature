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

  Scenario Outline: Who made a resource is read under the registered author relation alone: <relation>
    Fielding review, Rule 6, a thing to watch since 25 October: madeBy
    looked for its maker under author, user or creator, in any profile,
    so it assumed a structure no media type defines. author is registered
    with IANA (RFC 8288); a server that knows a resource's maker embeds it
    there, as the GitHub gateway does for GitHub's user and creator.
    Given these links
      | at | relation | title                  | to         |
      | /  | item     | Refuse blank bed names | /pulls/482 |
    And these embedded resources
      | at         | relation   | resource   |
      | /pulls/482 | <relation> | /users/lin |
    When this script runs
      """kotlin
      heart("scripted://service/") {
          whenever { madeBy("Lin"); note("made by Lin") }
          note("looked")
      }
      """
    Then the script's notes, joined, are "<notes>"

    Examples:
      | relation    | notes               |
      | author      | made by Lin; looked |
      | github:user | looked              |
      | user        | looked              |
      | creator     | looked              |

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
