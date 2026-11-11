Feature: The library answers Ana on her reply, and Ana checks each answer by the watch she asked for
  Ana has asked the library to tell her when loan 3310 can be renewed, and the library has
  brought a watch into being. The library answers later, by acting on her reply: progress
  while the hold is in line, then one result, completed or failed. Each answer names the
  watch it answers for, as hr:in-reply-to, and Ana's reply keeps that link. Ana checks
  every answer's signature with the keys of the watch her request created, never with a
  link the answer itself carries.

  Background:
    Given Riverside Public Library lends Ana Torres these books
      | loan | title      | author           | due        | renewals | hold waiting |
      | 3310 | The Hobbit | J. R. R. Tolkien | 2026-11-12 | 0        | yes          |
    And Ana's reply r-7f3 is awaiting
    And Ana has asked to be told when loan 3310 can be renewed, with reply r-7f3

  Scenario: Ana's reply takes the library's acts at its own address, by PATCH, as its forms say
    When the library reads Ana's reply r-7f3
    Then each of the reply's forms targets the reply's own address
      | form         | method | content type                 |
      | hr:progress  | PATCH  | application/merge-patch+json |
      | hr:completed | PATCH  | application/merge-patch+json |
      | hr:failed    | PATCH  | application/problem+json     |
    And the reply accepts patches as "application/merge-patch+json, application/problem+json"

  Scenario: An act at the reply's old act address finds nothing
    When the library sends progress of 10 percent to "/replies/r-7f3/actions/progress"
    Then the library is refused with 404
    And reply r-7f3 is still awaiting

  Scenario Outline: The library reports progress while the hold is in line
    When the library reports progress of <percent> percent on reply r-7f3, with the note "<note>"
    Then reply r-7f3 is awaiting at <percent> percent
    And reply r-7f3 offers progress, completed and failed
    And reply r-7f3 links "hr:in-reply-to" to Ana's watch

    Examples:
      | percent | note                             |
      | 0       | one ahead in the hold queue      |
      | 50      | the hold is with the next reader |

  Scenario: The library acts on the ETag its last answer gave, and reads the reply only to find its forms
    When the library reports progress of 0 percent on reply r-7f3, with the note "one ahead in the hold queue"
    And the library reports progress of 50 percent on reply r-7f3, with the note "the hold is with the next reader"
    Then the library read Ana's reply once, for both answers
    And reply r-7f3 is awaiting at 50 percent

  Scenario: The reply keeps its history: each change, when, the ETag it replaced, and the signature it came with
    When the library reports progress of 0 percent on reply r-7f3, with the note "one ahead in the hold queue"
    And the library reports progress of 50 percent on reply r-7f3, with the note "the hold is with the next reader"
    Then reply r-7f3 links "version-history" to its history
    And its history lists 2 changes, each with its time, the ETag it replaced and the signature it came with
    And each change after the first links the one before as "predecessor-version"

  Scenario: An answer that names another watch than Ana's is refused, and changes nothing
    When the library reports progress of 50 percent on reply r-7f3, for the watch "https://riverside-library.example/watches/99"
    Then the library is refused with 409
    And reply r-7f3 is still awaiting

  Scenario: The hold clears, and the library sends the result, signed
    Given the hold on loan 3310 has been returned
    When the library sends the result on reply r-7f3: loan 3310 can be renewed from 2026-12-03
    Then reply r-7f3 is answered as completed
    And the result is signed with the library's key
    And reply r-7f3 offers nothing more
    And reply r-7f3 links "hr:in-reply-to" to Ana's watch

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

  Scenario Outline: A result the library's key did not sign is refused, saying why and how to sign, and the reply stays awaiting
    Given the hold on loan 3310 has been returned
    When the library sends the result on reply r-7f3 <signature>: loan 3310 can be renewed from 2026-12-03
    Then the library is refused with 401, as the problem "<problem>"
    And the refusal asks for a Signature, over "@method" "@target-uri" "content-digest" "if-match", by the key "library-2026-11"
    And reply r-7f3 is still awaiting

    Examples:
      | signature               | problem                                                 |
      | signed with another key | https://w3id.org/heart-rise/problems/invalid-signature  |
      | unsigned                | https://w3id.org/heart-rise/problems/signature-required |

  Scenario: While the library has not answered, Ana can cancel her request
    When Ana reads her watch
    Then the watch offers "hr:cancel", a PATCH of the watch itself
    When Ana cancels her watch
    Then Ana's watch is cancelled

  Scenario: Once the library has answered, the request can't be cancelled, and the watch says why
    Given the hold on loan 3310 has been returned
    And the library sends the result on reply r-7f3: loan 3310 can be renewed from 2026-12-03
    When Ana reads her watch
    Then the watch offers no "hr:cancel", and says why: "The library has answered this request, so there is nothing to cancel."
    When Ana cancels her watch
    Then the library is refused with 409

  Scenario: The library cannot find the hold, and says so as a failure
    Given the library has no record of the hold on loan 3310
    When the library sends the failure on reply r-7f3: "No hold record for loan 3310"
    Then reply r-7f3 is answered as failed
    And the failure is a problem document, application/problem+json
    And reply r-7f3 shows the failure's problem, "No hold record for loan 3310"
