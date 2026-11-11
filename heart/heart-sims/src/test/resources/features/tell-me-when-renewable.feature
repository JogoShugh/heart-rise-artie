Feature: Riverside Public Library tells Ana when a held loan can be renewed
  Ana can't renew loan 3310 while a hold is waiting for The Hobbit. She asks the library
  to tell her when it can be renewed, rather than checking. Her request carries her reply
  address and her key as links, and she signs it with that key. The library brings a watch
  into being, which links the keys the library signs its answers with. The answers
  themselves are in the-library-answers-ana.feature.

  Background:
    Given Riverside Public Library lends Ana Torres these books
      | loan | title      | author           | due        | renewals | hold waiting |
      | 3310 | The Hobbit | J. R. R. Tolkien | 2026-11-12 | 0        | yes          |
    And Ana's reply r-7f3 is awaiting

  Scenario: Ana asks to be told, and the library accepts the request
    When Ana asks to be told when loan 3310 can be renewed, with reply r-7f3 and her key
    Then the library answers 201 with a watch that is awaiting
    And the library's entry offers the request, with its reply and key as links

  Scenario Outline: A request Ana did not sign with the key her request names is refused, saying how to sign it
    When Ana asks to be told when loan 3310 can be renewed, with reply r-7f3 and her key, <signature>
    Then the library is refused with 401, as the problem "<problem>"
    And the refusal asks for a Signature, over "@method" "@target-uri" "content-digest" "if-match", by the key "ana-2026-11"
    And the library holds no watch

    Examples:
      | signature               | problem                                                 |
      | signed with another key | https://w3id.org/heart-rise/problems/invalid-signature  |
      | unsigned                | https://w3id.org/heart-rise/problems/signature-required |

  Scenario: The library publishes the key it signs with, and links it from its entry and from each watch
    When Ana asks to be told when loan 3310 can be renewed, with reply r-7f3 and her key
    Then the library's entry and the watch both link "hr:keys" to the library's key set
    And the library's key set holds the key "library-2026-11", which verifies the library's signatures
