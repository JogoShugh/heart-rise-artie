Feature: Searches and lookups by the fields a type declares
  spec/PLAN-GITHUB-PULLS.md, phase 2. A type that can be found was searched
  by part of a name at /{collection} and looked up by one id. Real APIs list
  things under other things, filtered their own way: GitHub lists a
  repository's pull requests at /repos/{owner}/{repo}/pulls, filtered by
  state. A type may declare its collection path, a URI template, and the
  fields of its forms: one for each variable of its collection path and its
  item path, and any filters, which go in the query. The service index's
  search form then takes the collection path's variables and the filters,
  and its lookup form the item path's variables; each search page lists its
  matches at their item paths. A type that declares none is found as before.

  Background:
    Given a pull request type found at "/repos/{owner}/{repo}/pulls", filtered by "state"

  Scenario: The search form fills the collection path, and puts the filters in the query
    When the pull requests' service index is rendered
    Then the index form "pulls:list" targets the template "/repos/{owner}/{repo}/pulls{?state}"
    And its fields are "owner, repo, state", of which "owner, repo" are required

  Scenario: The lookup form fills the item path
    When the pull requests' service index is rendered
    Then the index form "pulls:get" targets the template "/repos/{owner}/{repo}/pulls/{number}"
    And its fields are "owner, repo, number", of which "owner, repo, number" are required

  Scenario: A field keeps the schema it was declared with
    When the pull requests' service index is rendered
    Then the index form "pulls:get" field "number" is an integer from 1
    And the index form "pulls:list" field "state" is one of "open, closed"

  Scenario: A search page lists each match at its item path, titled
    Given the search of "acme/rockets" for "open" finds "482 Fix the login bug" and "7 Bump the engines"
    When page 1 of the search is rendered, 10 to a page
    Then the page's self link is "/repos/acme/rockets/pulls?state=open"
    And the page's items are "/repos/acme/rockets/pulls/482 Fix the login bug, /repos/acme/rockets/pulls/7 Bump the engines"

  Scenario: Paging keeps the collection path and the filters
    Given the search of "acme/rockets" for "open" finds "482 Fix the login bug" and "7 Bump the engines"
    When page 1 of the search is rendered, 1 to a page
    Then the page's "next" link is "/repos/acme/rockets/pulls?state=open&page=2"

  Scenario: The home document gives the templates and a variable for each field
    When the pull requests' home document is rendered
    Then the resource "item" has the template "/repos/{owner}/{repo}/pulls/{number}" with variables "owner, repo, number"
    And the resource "collection" has the template "/repos/{owner}/{repo}/pulls{?state}" with variables "owner, repo, state"

  Scenario: The OpenAPI description serves the search at the collection path
    When the OpenAPI description of the found pull request type is rendered
    Then its operation "listPulls" is "GET /repos/{owner}/{repo}/pulls" with the parameters "owner, repo, state, page"

  Scenario: HAL-FORMS cannot template a target, so its index leaves these forms out
    When the pull requests' service index is rendered in HAL-FORMS
    Then it has no template "listPulls" or "getPull"

  Scenario: A collection path variable with no field is refused when the type is declared
    When a pull request type is found at "/repos/{owner}/{repo}/pulls" with no field "repo"
    Then the found type is refused, saying "pulls: no field for repo"
