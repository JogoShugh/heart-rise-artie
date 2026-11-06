Feature: A binding serves the resources a state embeds
  SPEC §6.1, decided 2026-10-18 (spec/PLAN-HAL-EMBEDDED.md). A pull request's
  author is another resource: the binding serves it under `_embedded`, with
  its own self link, and not in the state, in both dialects. The plain JSON
  twin keeps the state as it is. The embedded author is part of the bytes, so
  a change to it changes the ETag, and an act sent with the old one is stale.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server, written by "ada"

  Scenario Outline: The author is embedded with its self link, and is not in the state
    When a client gets "/repos/acme/rockets/pulls/482" as <dialect>
    Then the response status is 200
    And the response embeds "pulls:author" at "/users/ada"
    And the response has no member "author"

    Examples:
      | dialect          |
      | hal-schema-forms |
      | hal-forms        |

  Scenario: The plain JSON twin keeps the author in the state
    When a client gets "/repos/acme/rockets/pulls/482" as plain-json
    Then the response status is 200
    And the response member "author.login" is "ada"
    And the response has no member "_embedded"

  Scenario: A change to the embedded author makes the held ETag stale
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    And the author of pull request 482 of "acme/rockets" becomes "bo" in the pull request server
    And the client puts {} to "/repos/acme/rockets/pulls/482/actions/close" with the ETag and headers it synced with
    Then the response status is 412
