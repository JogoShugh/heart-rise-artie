Feature: The gateway in front of GitHub, known by its own description, sliced to pull requests
  spec/PLAN-GITHUB-PULLS.md, phase 3. GitHub publishes one OpenAPI 3.0
  description of its whole REST API, 13.5 MB of it. A connector names the
  operations the gateway uses, and the gateway reads only those: the rest
  of the description is left out before it is read. GitHub's paths are not
  HEART's: a pull request is /repos/{owner}/{repo}/pulls/{pull_number}, and
  a repository's pull requests are listed at /repos/{owner}/{repo}/pulls as
  a bare JSON array. One resource file in the connector says which
  operation reads a resource and which lists them, and where in a pull
  request's state each part of its path is. The connector also names the
  headers every call to GitHub carries.

  The upstream here is a small GitHub of two pull requests, answering as
  GitHub does, in memory. The description is a slice of GitHub's own.

  Background:
    Given the GitHub gateway reads GitHub's description with the connector "github"
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And pull request 7 of "acme/rockets" is "closed" upstream, titled "Bump the engines"

  Scenario: The profile has a relation for each act, for each included operation no act is made of, and none for a read passed through
    Beside these, it describes the relations the gateway's links and embedded
    resources use (github-links.feature).
    When a client gets "/profiles/github" through the GitHub gateway
    Then the profile describes these relations, among others
      | relation                |
      | pulls-list              |
      | pulls-get               |
      | issues-list-for-repo    |
      | issues-get              |
      | repos-get               |
      | users-get-authenticated |
      | close                   |
      | reopen                  |
      | merge                   |
      | edit                    |
      | request-review          |
      | update-branch           |
      | close-not-planned       |
      | close-duplicate         |
      | lock                    |
      | unlock                  |
      | label                   |
      | unlabel                 |
      | assign                  |
      | unassign                |
      | set-milestone           |
      | open-issue              |
      | issues-list-comments    |
      | issues-get-comment      |
      | issues-list-sub-issues  |
      | comment                 |
      | add-sub-issue           |
      | remove-sub-issue        |
    And the profile describes none of these relations
      | relation                      |
      | users-get-by-username         |
      | users-list-followers-for-user |
      | pulls-list-commits            |

  Scenario: The bookmark offers the search and lookup the resource file declares
    When a client gets "/" through the GitHub gateway
    Then the index form "github:pulls-list" targets "/repos/{owner}/{repo}/pulls{?state,sort,direction,labels}"
    And the index form "github:pulls-get" targets "/repos/{owner}/{repo}/pulls/{pull_number}"

  Scenario: The forms' fields keep the description's words and schemas
    When a client gets "/" through the GitHub gateway
    Then the index form "github:pulls-get" field "pull_number" has the type "integer"
    And the index form "github:pulls-list" field "state" is one of
      | value  |
      | open   |
      | closed |
      | all    |
    And the index form "github:pulls-list" field "owner" is described as "The account owner of the repository. The name is not case sensitive."

  Scenario: A pull request is read from GitHub at its own path, with the connector's headers
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/pulls/482"
    And GitHub was asked for "/repos/acme/rockets/pulls/482" with "X-GitHub-Api-Version" "2022-11-28"

  Scenario: A search lists GitHub's bare array at the pull requests' own paths, titled
    When a client gets "/repos/acme/rockets/pulls?state=closed" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists "/repos/acme/rockets/pulls/7" titled "Bump the engines"
    And GitHub was last asked for "/repos/acme/rockets/pulls?state=closed"

  Scenario: A search for pull requests by label is served by GitHub's issue list, keeping only its pull requests
    GitHub lists pull requests by label only in its issue list, where every
    pull request is an issue too; its pull request list has no label filter.
    The gateway asks the issue list, keeps the pull requests, each at its
    own address as a pull request, and leaves the issues out.
    Given pull request 482 of "acme/rockets" is labelled "dependabot" upstream
    And issue 10 of "acme/rockets" is labelled "dependabot" upstream, titled "Track the bumps"
    When a client gets "/repos/acme/rockets/pulls?state=open&labels=dependabot" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists "/repos/acme/rockets/pulls/482" titled "Fix the login bug"
    And GitHub was last asked for "/repos/acme/rockets/issues?state=open&labels=dependabot"

  Scenario: A connector that includes an operation the description lacks stops the gateway
    Given the GitHub gateway reads GitHub's description with the connector "github-unknown-operation"
    When the GitHub gateway starts
    Then the GitHub gateway refuses to start, saying "github-unknown-operation/connector.yaml: the upstream's description has no operation pulls/explode"
