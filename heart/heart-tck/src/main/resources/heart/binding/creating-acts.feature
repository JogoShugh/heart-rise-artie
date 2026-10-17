Feature: An act that brings another resource into being
  spec/PLAN-GITHUB-ISSUES.md, step I5. Some acts make a new resource
  somewhere else: a repository's "open an issue" makes an issue. The
  resource acted on is answered as ever, its new state; the answer is 201
  Created, and Location names the new resource (RFC 9110, section 15.3.2),
  while Content-Location still names the one acted on. A client follows
  Location to the new resource.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server

  Scenario: Splitting a pull request makes a new one, named in Location
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    And the client posts {} to "/repos/acme/rockets/pulls/482/actions/split" with the ETag and headers it synced with
    Then the response status is 201
    And the response has Location "/repos/acme/rockets/pulls/1482"
    And the response has Content-Location "/repos/acme/rockets/pulls/482"
    And the response links "self" to "/repos/acme/rockets/pulls/482"
    When a client gets "/repos/acme/rockets/pulls/1482" as hal-schema-forms
    Then the response status is 200

  Scenario: In plain JSON too, the answer is 201 with Location
    When a client posts {} to "/repos/acme/rockets/pulls/482/actions/split" as plain-json with If-Match ""
    Then the response status is 201
    And the response has Location "/repos/acme/rockets/pulls/1482"
