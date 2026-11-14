Feature: A binding serves each resource at its item path
  spec/PLAN-GITHUB-PULLS.md, phase 1. A type may declare where its resources
  live as a URI template filled from their state, such as a pull request's
  /repos/{owner}/{repo}/pulls/{number}. The binding routes by that template:
  a resource is read, and acted on, at the address its self link and its
  forms name, and loaded by the template's variables. The lamp, which
  declares none, stays at /lamps/{id}.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server

  Scenario: A pull request is read at its item path
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    Then the response status is 200
    And the response links "self" to "/repos/acme/rockets/pulls/482"

  Scenario: The same number under another repository is not found
    When a client gets "/repos/acme/engines/pulls/482" as hal-schema-forms
    Then the response status is 404

  Scenario: An act at the L0 form's target closes the pull request
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    And the client puts {} to "/repos/acme/rockets/pulls/482/actions/close" with the ETag and headers it synced with
    Then the response status is 200
    And the response has Content-Location "/repos/acme/rockets/pulls/482"
    And pull request 482 of "acme/rockets" is now "closed" in the pull request server

  Scenario: A client fills a shared form's target from the state, by its template pointers
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms with Prefer "forms=link"
    And the client follows the "hr:forms" link
    And the client expands "pulls:close" by its template pointers and sends {} with the ETag it holds
    Then the response status is 200
    And pull request 482 of "acme/rockets" is now "closed" in the pull request server

  Scenario: HAL-FORMS targets the item path too
    When a client gets "/repos/acme/rockets/pulls/482" as hal-forms
    Then the response has templates targeting
      | target                                      |
      | /repos/acme/rockets/pulls/482/actions/close |
      | /repos/acme/rockets/pulls/482/actions/split |
