Feature: The OpenAPI 3.1 description, served and linked from the bookmark
  heart-core renders the description (its openapi.feature); the HTTP
  binding serves it at /openapi.json and links it from the bookmark under
  the service-desc relation (RFC 8631), so a client that knows only / can
  find it. It is the same bytes for every caller, so like the profiles and
  the content-addressed layers it needs no credential.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario Outline: The index links the description as service-desc, in <dialect>
    When a client gets "/" as <dialect>
    Then the response links "service-desc" to "/openapi.json"
    And the response has a Link to "/openapi.json" with rel "service-desc"

    Examples:
      | dialect          |
      | hal-schema-forms |
      | hal-forms        |

  Scenario: The home document names the description too
    When a client gets "/" with Accept "application/json-home"
    Then the home document's api links "service-desc" to "/openapi.json"

  Scenario: Served without a credential
    When a client with no credential sends GET "/openapi.json"
    Then the response status is 200
    And the response has Content-Type "application/vnd.oai.openapi+json;version=3.1"
    And the response has Cache-Control "no-cache"
    And the response has an ETag

  Scenario: A person can read the description in Swagger UI at /docs, with no credential
    Swagger UI is served from the server itself, pinned, with nothing
    fetched from elsewhere. It reads /openapi.json. Its Authorize button
    takes a credential from the person reading, for trying acts out.
    When a client with no credential sends GET "/docs"
    Then the response status is 200
    And the response has Content-Type "text/html;charset=UTF-8"
    And the page loads Swagger UI from this server and points it at "/openapi.json"

  Scenario: Swagger UI's script and styles are served with no credential
    When a client with no credential gets each Swagger UI file the page loads
    Then each answers 200

  Scenario: Only Swagger UI's own files are open, not every path under /webjars
    When a client with no credential sends GET "/webjars/swagger-ui/../../lamps/7"
    Then the response status is 401

  Scenario: An unchanged description is revalidated with 304
    When a client gets "/openapi.json" with Accept "application/json"
    And a client gets "/openapi.json" again with the ETag it was given
    Then the response status is 304

  Scenario: Every route the server serves is in the description
    When a client gets "/openapi.json" with Accept "application/json"
    Then the description's operations are
      | method | path                                                |
      | GET    | /lamps                                              |
      | GET    | /lamps/{id}                                         |
      | GET    | /lamps/{id}/history                                 |
      | POST   | /lamps/{id}/actions/switch-on                       |
      | POST   | /lamps/{id}/actions/switch-off                      |
      | PUT    | /lamps/{id}/actions/dim                             |
      | PUT    | /lamps/{id}/actions/brighten                        |
      | POST   | /actions/install-lamp                               |
      | GET    | /repos/{owner}/{repo}/pulls                         |
      | GET    | /repos/{owner}/{repo}/pulls/{number}                |
      | PUT    | /repos/{owner}/{repo}/pulls/{number}/actions/close  |
      | PUT    | /repos/{owner}/{repo}/pulls/{number}/actions/reopen |
      | POST   | /repos/{owner}/{repo}/pulls/{number}/actions/split  |

  Scenario: The description says what a plain client gets, and the server answers just that
    When a client gets "/openapi.json" with Accept "application/json"
    And a client gets "/lamps/7" as plain-json
    Then the plain response satisfies the description's schema "Lamp"
