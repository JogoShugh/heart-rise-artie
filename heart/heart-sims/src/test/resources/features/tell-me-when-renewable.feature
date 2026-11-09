Feature: Riverside Public Library tells Ana when a held loan can be renewed
  Ana can't renew loan 3310 while a hold is waiting for The Hobbit. She asks the library
  to tell her when it can be renewed, rather than checking. Her request carries her reply
  address and her key as links. The library answers later, by acting on her reply:
  progress while the hold is in line, then one result, completed or failed.

  Background:
    Given Riverside Public Library lends Ana Torres these books
      | loan | title      | author           | due        | renewals | hold waiting |
      | 3310 | The Hobbit | J. R. R. Tolkien | 2026-11-12 | 0        | yes          |
    And Ana's reply r-7f3 is awaiting

  Scenario: Ana asks to be told, and the library accepts the request
    When Ana asks to be told when loan 3310 can be renewed, with reply r-7f3 and her key
    Then the library answers 201 with a watch that is awaiting
    And the library's entry offers the request, with its reply and key as links

  Scenario Outline: The library reports progress while the hold is in line
    When the library reports progress of <percent> percent on reply r-7f3, with the note "<note>"
    Then reply r-7f3 is awaiting at <percent> percent
    And reply r-7f3 offers progress, completed and failed

    Examples:
      | percent | note                             |
      | 0       | one ahead in the hold queue      |
      | 50      | the hold is with the next reader |

  Scenario: The hold clears, and the library sends the result, signed
    Given the hold on loan 3310 has been returned
    When the library sends the result on reply r-7f3: loan 3310 can be renewed from 2026-12-03
    Then reply r-7f3 is answered as completed
    And the result is signed with the library's key
    And reply r-7f3 offers nothing more

  Scenario: The same result sent again changes nothing
    Given reply r-7f3 is answered as completed, with loan 3310 renewable from 2026-12-03
    When the library sends the same result again on reply r-7f3
    Then the library is answered 200
    And reply r-7f3 is unchanged

  Scenario: A different result after the answer is refused
    Given reply r-7f3 is answered as completed, with loan 3310 renewable from 2026-12-03
    When the library sends a different result on reply r-7f3: loan 3310 renewable from 2026-12-10
    Then the library is refused with 409
    And the refusal says hr:completed is not offered

  Scenario: The library cannot find the hold, and says so as a failure
    Given the library has no record of the hold on loan 3310
    When the library sends the failure on reply r-7f3: "No hold record for loan 3310"
    Then reply r-7f3 is answered as failed
    And the failure is a problem document, application/problem+json
