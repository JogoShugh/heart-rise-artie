Feature: A HEART service admits only callers with a credential
  Every state, every act and the bookmark need a Basic or a Bearer
  credential. The shared, content-addressed layers (affordance sets,
  schemas, explanations of absent acts) and the ALPS profile stay open:
  they are the same for every caller, and say what a service offers, not
  what any resource holds. A refusal is a problem details object that
  offers both schemes, and says nothing about which users or tokens exist.
  The lamp server admits the user "coach" with the password "s3cret", and
  the Bearer token "lamp-token".

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario Outline: Who gets in: <case>
    When a client <sends> <method> "<path>"
    Then the response status is <status>

    Examples:
      | case                  | sends                                   | method | path                     | status |
      | Basic                 | with Basic "coach:s3cret" sends         | GET    | /lamps/7                 | 200    |
      | Bearer                | with Bearer "lamp-token" sends          | GET    | /lamps/7                 | 200    |
      | no credential         | with no credential sends                | GET    | /lamps/7                 | 401    |
      | wrong password        | with Basic "coach:wrong" sends          | GET    | /lamps/7                 | 401    |
      | unknown user          | with Basic "ref:s3cret" sends           | GET    | /lamps/7                 | 401    |
      | unknown token         | with Bearer "other-token" sends         | GET    | /lamps/7                 | 401    |
      | not Base64            | with the Authorization "Basic %%%" sends | GET   | /lamps/7                 | 401    |
      | another scheme        | with the Authorization "Digest x" sends | GET    | /lamps/7                 | 401    |
      | an act                | with no credential sends                | PUT    | /lamps/7/actions/dim     | 401    |
      | the bookmark          | with no credential sends                | GET    | /                        | 401    |
      | a search              | with no credential sends                | GET    | /lamps?name=Lamp         | 401    |
      | a past state          | with no credential sends                | GET    | /lamps/7/history         | 401    |
      | the ALPS profile      | with no credential sends                | GET    | /profiles/home           | 200    |

  Scenario Outline: Every <kind> is open to a caller with no credential
    When a client with no credential gets every <kind> of the lamp by its address
    Then each answers 200 with Content-Type "<type>"

    Examples:
      | kind           | type                    |
      | affordance set | application/hal+json    |
      | schema         | application/schema+json |
      | explanation    | application/json        |

  Scenario: A refusal offers both schemes, as a problem
    When a client with no credential sends GET "/lamps/7"
    Then the response status is 401
    And the response has Content-Type "application/problem+json"
    And the response offers these challenges
      | challenge           |
      | Basic realm="home"  |
      | Bearer realm="home" |
    And the problem's "type" member is "https://w3id.org/heart-rise/problems/not-authenticated"

  Scenario: A wrong credential is answered exactly as a missing one
    When a client with no credential sends GET "/lamps/7"
    And a client with Basic "coach:wrong" sends GET "/lamps/7"
    And a client with Bearer "other-token" sends GET "/lamps/7"
    Then the last three responses have identical bytes and headers

  Scenario: A refusal names no user and no token
    When a client with Basic "coach:wrong" sends GET "/lamps/7"
    Then the response body does not contain "coach"
    And the response body does not contain "wrong"

  Scenario Outline: The configured credentials are read, or stop the service at the start: <case>
    When the service is configured with users "<users>" and tokens "<tokens>"
    Then the configuration <outcome>

    Examples:
      | case                 | users                                                                  | tokens                                                           | outcome                                                                                        |
      | a user               | coach:$2y$10$8aPKQ4NLZ03c0ql39PXrZuIWta4FhSekHlrDmcbAAxxnhLXovjO8C     |                                                                  | admits Basic "coach:s3cret"                                                                    |
      | a token              |                                                                        | a1011f8452b5270bb1b7dd59ea154bca26711ace2c21d4607c0fd4d5b26d14cb | admits Bearer "lamp-token"                                                                     |
      | nothing              |                                                                        |                                                                  | fails with "set HEART_AUTH_BASIC_USERS or HEART_AUTH_BEARER_TOKENS; a HEART service will not start open" |
      | a plain password     | coach:s3cret                                                           |                                                                  | fails with "the user coach has no bcrypt hash"                                                 |
      | a token, not a hash  |                                                                        | lamp-token                                                       | fails with "a Bearer token is configured as itself, not as its SHA-256 in hex"                 |
