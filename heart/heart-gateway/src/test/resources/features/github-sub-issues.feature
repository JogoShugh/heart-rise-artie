Feature: An issue's sub-issues, linked from it, added and removed
  spec/PLAN-GITHUB-ISSUES.md, step I6. GitHub lists an issue's sub-issues
  at /repos/{owner}/{repo}/issues/{issue_number}/sub_issues. They are
  issues, so the gateway serves the list itself, at that path, as a page of
  issues, and links it from the issue. Adding and removing a sub-issue are
  acts the issue offers, naming the sub-issue by its id, as GitHub does.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |
      | 11     | Safari 17 cookies     | open  |              | false  | ada    |
      | 12     | Safari 16 redirects   | open  |              | false  | bob    |

  Scenario: An issue links its sub-issues
    Given issue 11 of "acme/rockets" is a sub-issue of issue 10 upstream
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the answer links "github:issues-list-sub-issues" to "/repos/acme/rockets/issues/10/sub_issues"
    When "ada" gets "/repos/acme/rockets/issues/10/sub_issues" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists
      | href                          | title             |
      | /repos/acme/rockets/issues/11 | Safari 17 cookies |

  Scenario: An issue with no sub-issues lists none
    When "ada" gets "/repos/acme/rockets/issues/10/sub_issues" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists
      | href | title |

  Scenario: The forms ask for what GitHub's sub-issue operations take
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the forms ask for these fields
      | act                     | field          |
      | github:add-sub-issue    | sub_issue_id   |
      | github:add-sub-issue    | replace_parent |
      | github:remove-sub-issue | sub_issue_id   |

  Scenario: Adding a sub-issue
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:add-sub-issue" with {"sub_issue_id":2000012} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to POST "/repos/acme/rockets/issues/10/sub_issues" with {"sub_issue_id":2000012}
    When "ada" gets "/repos/acme/rockets/issues/10/sub_issues" through the GitHub gateway
    Then the answer lists
      | href                          | title               |
      | /repos/acme/rockets/issues/12 | Safari 16 redirects |

  Scenario: Removing a sub-issue
    Given issue 11 of "acme/rockets" is a sub-issue of issue 10 upstream
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:remove-sub-issue" with {"sub_issue_id":2000011} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to DELETE "/repos/acme/rockets/issues/10/sub_issue" with {"sub_issue_id":2000011}
    When "ada" gets "/repos/acme/rockets/issues/10/sub_issues" through the GitHub gateway
    Then the answer lists
      | href | title |

  Scenario: Who may add and remove sub-issues
    Then issue 10 of "acme/rockets" offers each caller
      | caller | access | add-sub-issue | remove-sub-issue |
      | ada    | read   | no            | no               |
      | bob    | triage | yes           | yes              |
      | bob    | write  | yes           | yes              |
    And issue 10 of "acme/rockets" says why, to each caller
      | caller | access | act           | reason                                     |
      | ada    | read   | add-sub-issue | You need triage access to this repository. |
