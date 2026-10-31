Feature: Embedded resources: read in place, followed to act
  SPEC §6.1, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md). A representation
  may embed other resources under `_embedded`, each with its own self link.
  ARTIE takes exactly `_links`, `_embedded`, `_forms` and `_templates` out of
  the state, so an embedded resource is never mistaken for the resource's own
  data. It reads an embedded resource without a request, and follows its
  relation like a link: following fetches it, with its ETag and its forms.
  The reasoner sees an embedded resource as a link it may follow, with its
  state, and never its URI.

  Background:
    Given the service answers GET "http://garden.test/pulls/482" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      number: 482
      state: open
      _links:
        self: {href: /pulls/482}
      _embedded:
        pulls:author:
          login: ada
          _links:
            self: {href: /users/ada, title: Ada}
        pulls:reviewers:
          - login: lin
            _links:
              self: {href: /users/lin, title: Lin}
          - login: kay
            _links:
              self: {href: /users/kay, title: Kay}
      _forms: {}
      """
    And the service answers GET "http://garden.test/users/ada" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-3.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      login: ada
      name: Ada Lovelace
      _links:
        self: {href: /users/ada}
      _forms: {}
      """

  Scenario: The embedded resources are not in the state
    When the client enters "http://garden.test/pulls/482"
    Then the resource state contains:
      | key    | value |
      | number | 482   |
      | state  | open  |
    And the resource state does not contain the key "_embedded"

  Scenario: Each embedded resource is read in place, without a request
    When the client enters "http://garden.test/pulls/482"
    Then the resource embeds these resources
      | relation        | href                        | title | login |
      | pulls:author    | http://garden.test/users/ada | Ada   | ada   |
      | pulls:reviewers | http://garden.test/users/lin | Lin   | lin   |
      | pulls:reviewers | http://garden.test/users/kay | Kay   | kay   |
    And the client sent no request after entering

  Scenario: Following an embedded relation fetches the resource
    When the client enters "http://garden.test/pulls/482"
    And the client follows the "pulls:author" link titled "Ada"
    Then the client sent these requests after entering:
      | GET http://garden.test/users/ada |
    And the client holds the ETag "seq-3.hsf.L0"

  Scenario: A script on ARTIE reads an embedded resource in place, then follows it
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/pulls/482") {
          check(embedded("pulls:author").single().read["/login"] == "ada") { "the author should be ada" }
          follow("pulls:author", title = "Ada")
          check(state["/name"] == "Ada Lovelace") { "the author should be Ada Lovelace" }
      }
      """
    Then the script finished

  Scenario Outline: A followed resource the server won't give stops the script with a refusal, not a crash: <status>
    Live, 2026-10-30: a reasoner followed a branch whose name holds a slash,
    the gateway answered 400, and ARTIE threw, ending the run with a stack
    trace and no trace written. A read the server refuses is a refusal, said
    the way an act's is, so the script stops at its step and the run is kept.
    Given the service answers GET "http://garden.test/users/lin" with:
      """
      <status>
      Content-Type: application/problem+json

      title: no
      """
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/pulls/482") {
          follow("pulls:reviewers", title = "Lin")
      }
      """
    Then the script stopped at step 1, "pulls:reviewers": <reason>

    Examples:
      | status                    | reason                                                                                                       |
      | 404 Not Found             | not found, refused by the server: "http://garden.test/users/lin could not be read: the server answered 404"  |
      | 400 Bad Request           | unreadable, refused by the server: "http://garden.test/users/lin could not be read: the server answered 400" |
      | 500 Internal Server Error | unreadable, refused by the server: "http://garden.test/users/lin could not be read: the server answered 500" |

  Scenario: The reasoner sees each embedded resource as a link, with its state and no URI
    Given the reasoner will follow the "pulls:author" link titled "Ada"
    When the agent handles "who wrote this?" at "http://garden.test/pulls/482"
    Then the reasoner was shown these links and no URI
      | rel             | title |
      | pulls:author    | Ada   |
      | pulls:reviewers | Lin   |
      | pulls:reviewers | Kay   |
    And the reasoner was shown the "pulls:author" link titled "Ada" with the state member "login" as "ada"
    And the agent followed the link to "http://garden.test/users/ada"
