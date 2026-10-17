Feature: Where a resource lives: its item path, filled from its own state
  spec/PLAN-GITHUB-PULLS.md, phase 1. A resource of a declared type has been
  found at /{collection}/{id}, its id a number at /id. Real APIs identify
  things by more than one number: a pull request is its owner, its
  repository and its number. A type may declare its item path as a URI
  template, with a JSON Pointer into the state for each variable. Every
  address HEART renders for the type follows it: the self link, each form's
  target and its template pointers, and the OpenAPI description. Clients
  build no addresses, so nothing changes for them. A type that declares no
  item path keeps /{collection}/{id}, byte for byte.

  Background:
    Given a pull request type declared with the item path "/repos/{owner}/{repo}/pulls/{number}"
    And pull request 482 of "acme/rockets" is open

  Scenario: The self link is the item path, filled from the state
    When the pull request is rendered at L0
    Then its self link is "/repos/acme/rockets/pulls/482"

  Scenario: At L0 a form's target is concrete, under the item path
    When the pull request is rendered at L0
    Then the form "pulls:close" targets "/repos/acme/rockets/pulls/482/actions/close"

  Scenario: In a shared affordance set a target is the item path as a template, with a pointer for each variable
    When the pull request is rendered at L1
    And its affordance set is read
    Then the form "pulls:close" targets the template "/repos/{owner}/{repo}/pulls/{number}/actions/close"
    And its template pointers are
      | variable | pointer          |
      | owner    | /base/owner      |
      | repo     | /base/repo       |
      | number   | /number          |

  Scenario: Two pull requests in one state share one affordance set
    Given pull request 7 of "acme/engines" is open
    When both pull requests are rendered at L1
    Then they link the same affordance set

  Scenario: HAL-FORMS targets the same address
    When the pull request is rendered in HAL-FORMS
    Then its template "close" targets "/repos/acme/rockets/pulls/482/actions/close"

  Scenario: The OpenAPI description serves the item path, with a parameter for each variable
    When the OpenAPI description of the pull request type is rendered
    Then it has the path "/repos/{owner}/{repo}/pulls/{number}" with the parameters "owner, repo, number"
    And it has the path "/repos/{owner}/{repo}/pulls/{number}/actions/close"

  Scenario: An item path variable with no pointer is refused when the type is declared
    When a pull request type is declared with the item path "/repos/{owner}/{repo}/pulls/{number}" and no pointer for "repo"
    Then the declaration is refused, saying "item path variable repo has no pointer"

  Scenario: A type that declares no item path keeps its address, byte for byte
    Given a lamp resource type declared with the profile "home"
    Then the lamp type's item path is "/lamps/{id}" with the pointer "id" at "/id"
