Feature: Acts on GitHub pull requests, derived from GitHub's operations
  spec/PLAN-GITHUB-PULLS.md, phase 5. GitHub has no "close" operation: a
  pull request is closed by pulls/update with {"state": "closed"}, and
  reopened by the same operation with {"state": "open"}. A resource file
  declares each act as a projection of an operation: the fixed part of its
  body, the fields a client fills, and the fields bound from the state the
  client's validator matched. Merge binds GitHub's own sha check to the
  pull request's head, so a merge after the head moved is refused by GitHub
  itself, and the gateway says the client's view is stale. GitHub's own
  refusals stay the final word.

  Background:
    Given the GitHub gateway reads GitHub's description with the connector "github"
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"
    And pull request 7 of "acme/rockets" is "closed" upstream, titled "Bump the engines"

  Scenario: An open pull request offers close and merge; a closed one, reopen
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers "github:close, github:merge"
    When a client gets "/repos/acme/rockets/pulls/7" through the GitHub gateway
    Then the GitHub gateway offers "github:reopen"

  Scenario: Close is pulls/update with the state closed
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:close" with {} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PATCH "/repos/acme/rockets/pulls/482" with {"state":"closed"}
    And the GitHub gateway offers "github:reopen"

  Scenario: Merge's form asks for the merge method and the commit's words, never the sha
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the form "github:merge" has the fields "merge_method, commit_title, commit_message"
    And the form "github:merge" field "merge_method" is one of "merge, squash, rebase"

  Scenario: Merge sends GitHub the head sha the client's view had
    Given pull request 482's head is at "a1b2c3"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:merge" with {"merge_method":"squash"} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PUT "/repos/acme/rockets/pulls/482/merge" with {"merge_method":"squash","sha":"a1b2c3"}

  Scenario: A merge after the head moved is refused by GitHub's sha check, as stale
    Given pull request 482's head is at "a1b2c3"
    And pull request 482's head moves to "d4e5f6" just before GitHub merges it
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:merge" with {} and the ETag it holds
    Then the GitHub gateway answers 412

  Scenario: A merge GitHub can't perform is refused with GitHub's own words
    Given pull request 482 can't be merged, GitHub says "Pull Request is not mergeable"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:merge" with {} and the ETag it holds
    Then the GitHub gateway answers 409
    And the problem's detail is "Pull Request is not mergeable"
