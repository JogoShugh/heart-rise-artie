Feature: Branches, commits and new pull requests, from acts a branch offers
  spec/ideas/github-branches-and-new-pulls.md, decided 2026-10-18. A branch is a
  resource of its own, at GitHub's /repos/{owner}/{repo}/branches/{branch},
  linked from its repository. It offers three acts, each a projection of one of
  GitHub's operations: branch from here (git/create-ref), commit a file
  (repos/create-or-update-file-contents) and open a pull request
  (pulls/create). Three mechanisms make them read as acts, not as GitHub's
  plumbing: an input the body is a template of (the client names the branch;
  the gateway sends refs/heads/<name>), a field the gateway encodes (the
  client writes the file's text; the gateway sends it base64), and an address
  template for a new resource whose answer isn't that resource (a new branch's
  Location). The branch's own name and head are bound from its state.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these branches upstream
      | name | sha                                      | protected |
      | main | 1111111111111111111111111111111111111111 | true      |
      | fix  | 2222222222222222222222222222222222222222 | false     |

  Scenario: A branch is read at GitHub's own path, linked from its repository, and offers its acts
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/branches/fix"
    And the GitHub gateway offers
      | act                      |
      | github:branch-from-here  |
      | github:commit-file       |
      | github:open-pull-request |
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    Then the answer links "github:repos-list-branches" to "/repos/acme/rockets/branches"

  Scenario: The forms ask for what a person would say, not GitHub's plumbing
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the forms ask for these fields
      | act                      | field   |
      | github:branch-from-here  | name    |
      | github:commit-file       | path    |
      | github:commit-file       | message |
      | github:commit-file       | content |
      | github:open-pull-request | title   |
      | github:open-pull-request | body    |
      | github:open-pull-request | base    |
      | github:open-pull-request | draft   |

  Scenario: Branching from here sends GitHub a ref, at this branch's head, and answers 201 naming the new branch
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:branch-from-here" with {"name":"fix-2"} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/branches/fix-2"
    And GitHub was asked to POST "/repos/acme/rockets/git/refs" with {"ref":"refs/heads/fix-2","sha":"2222222222222222222222222222222222222222"}

  Scenario: Committing a file sends its text base64-encoded, on this branch
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:commit-file" with {"path":"NOTES.md","message":"Refuse blank names","content":"Bed names may not be blank.\n"} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub holds the file "NOTES.md" on "fix" of "acme/rockets", saying "Bed names may not be blank.\n", committed as "Refuse blank names"

  Scenario: Opening a pull request from this branch answers 201 naming the new pull request
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:open-pull-request" with {"title":"Refuse blank names","base":"main"} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/pulls/1"
    And GitHub was asked to POST "/repos/acme/rockets/pulls" with {"title":"Refuse blank names","base":"main","head":"fix"}

  Scenario: A protected branch takes no commits, and says why
    When "ada" gets "/repos/acme/rockets/branches/main" through the GitHub gateway
    Then the GitHub gateway says these acts are absent, and why
      | act                | reason                                         |
      | github:commit-file | The branch is protected; commit to another one. |
