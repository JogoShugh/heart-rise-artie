Feature: for-each goes through a list's members, each kept by a name the steps inside read, however deep
  A list's members, page after page, each visited once, as whenever visits
  them outside a watch; whenever is for a watch, over time. Each member is
  kept by the loop's name while the steps inside it run, at any depth: a
  script held as data reads it as ${member.title}, a Kotlin script as
  kept("member"). A loop inside that reuses the name hides the outer member
  only while it runs; after it, the name is the outer member again, as a
  call stack's would be.

  Background:
    Given a service holds these resources
      | address | title        | state     |
      | /       | Harbor Point |           |
      | /m/1    | Maria Garcia | member    |
      | /m/2    | James Wilson | member    |
      | /l/1    | Peter Pan    | renewable |
      | /l/2    | Heidi        | renewable |
      | /l/3    | Holes        | on hold   |
    And these links
      | at   | relation | title        | to   |
      | /    | item     | Maria Garcia | /m/1 |
      | /    | item     | James Wilson | /m/2 |
      | /m/1 | item     | Peter Pan    | /l/1 |
      | /m/1 | item     | Heidi        | /l/2 |
      | /m/2 | item     | Holes        | /l/3 |
    And these forms
      | at   | relation | leads to | outcome |
      | /l/1 | renew    | /l/1     | done    |
      | /l/2 | renew    | /l/2     | done    |

  Scenario: A script held as data reads each loop's member by its name, two loops deep
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - for-each: member
          then:
            - for-each: loan
              then:
                - when-offered:
                    - act: renew
                  otherwise:
                    - note: ${loan.title}, for ${member.title}, offers no renew
            - note: done with ${member.title}
      """
    Then the script finished
    And the script noted, in order
      | note                                     |
      | done with Maria Garcia                   |
      | Holes, for James Wilson, offers no renew |
      | done with James Wilson                   |
    And the client was asked, in order
      | call   | what                |
      | enter  | scripted://service/ |
      | follow | item "Maria Garcia" |
      | follow | item "Peter Pan"    |
      | act    | renew               |
      | return | /m/1                |
      | follow | item "Heidi"        |
      | act    | renew               |
      | return | /m/1                |
      | return | /                   |
      | follow | item "James Wilson" |
      | follow | item "Holes"        |
      | return | /m/2                |
      | return | /                   |

  Scenario: The same in Kotlin, with each
    When this script runs
      """kotlin
      heart("scripted://service/") {
          each("member") {
              val member by kept("member") { text("title") }
              each("loan") {
                  val loan by kept("loan") { text("title") }
                  whenOffered(otherwise = { note("$loan, for $member, offers no renew") }) {
                      case("renew") { act("renew") }
                  }
              }
              note("done with $member")
          }
      }
      """
    Then the script finished
    And the script noted, in order
      | note                                     |
      | done with Maria Garcia                   |
      | Holes, for James Wilson, offers no renew |
      | done with James Wilson                   |

  Scenario: A loop inside that reuses the name hides the outer member only while it runs
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - for-each: it
          then:
            - for-each: it
              then:
                - note: inner ${it.title}
            - note: outer ${it.title}
      """
    Then the script finished
    And the script noted, in order
      | note                 |
      | inner Peter Pan      |
      | inner Heidi          |
      | outer Maria Garcia   |
      | inner Holes          |
      | outer James Wilson   |
