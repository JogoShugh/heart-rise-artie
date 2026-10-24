Feature: Who may do what to an issue, and why not
  spec/PLAN-GITHUB-ISSUES.md, step I4. On GitHub an issue's author may
  close, reopen and edit it; anyone with triage access may close, reopen,
  label, assign and set its milestone; push access is needed to edit
  someone else's issue and to lock or unlock its conversation. The gateway
  reads two of GitHub's answers as facts: the repository's permissions for
  the caller, and the caller themselves (GET /user). Its rules compare the
  issue's author with the caller, and read the permissions, each with a
  reason.

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

  Scenario: What each caller may do to ada's open issue
    Then issue 10 of "acme/rockets" offers each caller
      | caller | access | close | close-not-planned | edit | label | unlabel | assign | unassign | set-milestone | lock |
      | ada    | read   | yes   | yes               | yes  | no    | no      | no     | no       | no            | no   |
      | bob    | read   | no    | no                | no   | no    | no      | no     | no       | no            | no   |
      | bob    | triage | yes   | yes               | no   | yes   | yes     | yes    | yes      | yes           | no   |
      | bob    | write  | yes   | yes               | yes  | yes   | yes     | yes    | yes      | yes           | yes  |

  Scenario: Why each caller may not
    Then issue 10 of "acme/rockets" says why, to each caller
      | caller | access | act   | reason                                                          |
      | bob    | read   | close | Only the issue's author or someone with triage access can close or reopen it. |
      | bob    | read   | edit  | Only the issue's author or someone with push access can edit it. |
      | bob    | read   | label | You need triage access to this repository.                      |
      | bob    | triage | edit  | Only the issue's author or someone with push access can edit it. |
      | bob    | triage | lock  | You need push access to lock or unlock the conversation.        |
      | ada    | read   | lock  | You need push access to lock or unlock the conversation.        |
