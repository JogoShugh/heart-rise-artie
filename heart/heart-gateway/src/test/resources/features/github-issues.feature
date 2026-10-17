Feature: GitHub issues through the gateway: reading them, and closing, reopening, editing and locking them
  spec/PLAN-GITHUB-ISSUES.md, steps I1 and I2. An issue lives at GitHub's
  own path, /repos/{owner}/{repo}/issues/{issue_number}, and a repository's
  issues are listed at /repos/{owner}/{repo}/issues. GitHub lists pull
  requests there too, since every pull request is an issue; the issue
  search leaves them out. GitHub closes an issue in three ways, each the
  same operation with its own state_reason; reopening, editing, locking and
  unlocking are each declared the same way, in the connector's issue file.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state  | state_reason | locked | author |
      | 10     | Login fails on Safari | open   |              | false  | ada    |
      | 11     | Rewrite it in Rust    | closed | not_planned  | false  | bob    |
      | 12     | Crash on start        | closed | completed    | false  | ada    |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: An issue is read at GitHub's own path
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/issues/10"

  Scenario: The issue search lists issues, and leaves out pull requests
    When "ada" gets "/repos/acme/rockets/issues?state=all" through the GitHub gateway
    Then the answer lists
      | href                           | title                 |
      | /repos/acme/rockets/issues/10  | Login fails on Safari |
      | /repos/acme/rockets/issues/11  | Rewrite it in Rust    |
      | /repos/acme/rockets/issues/12  | Crash on start        |

  Scenario: The bookmark offers the issue search with GitHub's own filters
    When "ada" gets "/" through the GitHub gateway
    Then the index form "github:issues-list-for-repo" targets "/repos/{owner}/{repo}/issues{?state,labels,assignee,creator,mentioned,milestone,sort,direction,since}"
    And the index form "github:issues-get" targets "/repos/{owner}/{repo}/issues/{issue_number}"

  Scenario: What an issue offers in each state
    Then each issue state offers
      | state                 | close | close-not-planned | close-duplicate | reopen | edit | lock | unlock | label | unlabel | assign | unassign | set-milestone | comment | add-sub-issue | remove-sub-issue |
      | open                  | yes   | yes               | yes             | no     | yes  | yes  | no     | yes   | yes     | yes    | yes      | yes           | yes     | yes           | yes              |
      | open, locked          | yes   | yes               | yes             | no     | yes  | no   | yes    | yes   | yes     | yes    | yes      | yes           | yes     | yes           | yes              |
      | closed as completed   | no    | no                | no              | yes    | yes  | yes  | no     | yes   | yes     | yes    | yes      | yes           | yes     | yes           | yes              |
      | closed as not planned | no    | no                | no              | yes    | yes  | yes  | no     | yes   | yes     | yes    | yes      | yes           | yes     | yes           | yes              |
      | closed, locked        | no    | no                | no              | yes    | yes  | no   | yes    | yes   | yes     | yes    | yes      | yes           | yes     | yes           | yes              |

  Scenario: Why an issue leaves an act out, in each state
    Then each issue state says why an act is absent
      | state          | act               | reason                             |
      | open           | reopen            | The issue is already open.         |
      | open           | unlock            | The conversation isn't locked.     |
      | open, locked   | lock              | The conversation is already locked. |
      | closed, locked | close             | The issue is already closed.       |
      | closed, locked | close-not-planned | The issue is already closed.       |
      | closed, locked | close-duplicate   | The issue is already closed.       |

  Scenario Outline: Each act is GitHub's operation with its own body
    When "ada" gets "/repos/acme/rockets/issues/<issue>" through the GitHub gateway
    And the client sends "github:<act>" with <sent> and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to <method> "<path>" with <github gets>

    Examples:
      | issue | act               | sent                                 | method | path                                 | github gets                                                    |
      | 10    | close             | {}                                   | PATCH  | /repos/acme/rockets/issues/10        | {"state":"closed","state_reason":"completed"}                  |
      | 10    | close-not-planned | {}                                   | PATCH  | /repos/acme/rockets/issues/10        | {"state":"closed","state_reason":"not_planned"}                |
      | 10    | close-duplicate   | {"duplicate_issue_id":12}            | PATCH  | /repos/acme/rockets/issues/10        | {"state":"closed","state_reason":"duplicate","duplicate_issue_id":12} |
      | 11    | reopen            | {}                                   | PATCH  | /repos/acme/rockets/issues/11        | {"state":"open","state_reason":"reopened"}                     |
      | 10    | edit              | {"title":"Login fails on Safari 18"} | PATCH  | /repos/acme/rockets/issues/10        | {"title":"Login fails on Safari 18"}                           |
      | 10    | lock              | {"lock_reason":"too heated"}         | PUT    | /repos/acme/rockets/issues/10/lock   | {"lock_reason":"too heated"}                                   |

  Scenario: Unlocking is GitHub's DELETE of the lock, with no body
    Given issue 10 of "acme/rockets" is locked upstream
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:unlock" with {} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to DELETE "/repos/acme/rockets/issues/10/lock"
    And the GitHub gateway offers
      | act                      |
      | github:close             |
      | github:close-not-planned |
      | github:close-duplicate   |
      | github:edit              |
      | github:lock              |
      | github:label             |
      | github:unlabel           |
      | github:assign            |
      | github:unassign          |
      | github:set-milestone     |
      | github:comment           |
      | github:add-sub-issue     |
      | github:remove-sub-issue  |
