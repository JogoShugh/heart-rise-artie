Feature: A goal file in sections: the brief, Working alongside others, The work and Done when
  spec/ideas/goals-that-work-alongside-others.md. Below its brief, a goal may
  be written in sections, each a ## heading: Working alongside others, the
  manners in words, a paragraph then a list; The work, the one list of steps;
  Done when, the outcomes, a list of facts. A goal with no heading is read as
  before. --plan-only prints every section; a run doesn't use the manners in
  words or check Done when yet, and says so before its first request.

  Background:
    Given the file "task.goal.md" is
      """
      ---
      start: scripted://service/
      manners: alongside-others
      inputs:
        owner:
        repo: rockets
      ---

      Close the crash issue.

      ## Working alongside others

      Other people work here too.

      - Do not undo what someone else changed.
      - Say why when you leave a step.

      ## The work

      - In {owner}/{repo}
        - Open an issue titled "Crash" `as issue`
        - Close {issue}

      ## Done when

      - {issue} is closed
      - Nothing anyone else decided was undone
      """

  Scenario: --plan-only prints each section, the facts of Done when each at its line
    When artie is run with
      | argument     |
      | run          |
      | task.goal.md |
      | --plan-only  |
    Then artie exits 0
    And artie prints
      | line                                             |
      | start scripted://service/                        |
      | manners alongside-others                         |
      | inputs owner, repo                               |
      | context: Close the crash issue.                  |
      | alongside: Other people work here too.           |
      | alongside: Do not undo what someone else changed. |
      | alongside: Say why when you leave a step.        |
      | line 20: In {owner}/{repo}                       |
      | line 21:   Open an issue titled "Crash" (as issue) |
      | line 22:   Close {issue}                         |
      | line 26: done when: {issue} is closed            |
      | line 27: done when: Nothing anyone else decided was undone |
      | a goal in steps: 3 steps, nothing sent           |

  Scenario: A run doesn't use the sections yet, and says so before its first request
    When artie is run with
      | argument     |
      | run          |
      | task.goal.md |
    Then artie exits 2
    And artie's last line is: stopped before the first request: task.goal.md:11: Working alongside others isn't run yet; --plan-only shows how the goal reads

  Scenario Outline: A goal in sections that breaks a rule stops before its first request: <case>
    Given line <line> of "task.goal.md" reads '<reads>', indented <indent>
    When artie is run with
      | argument     |
      | run          |
      | task.goal.md |
      | --plan-only  |
    Then artie exits 2
    And artie's last line is: stopped before the first request: task.goal.md:<at>: <said>

    Examples:
      | case                                  | line | indent | reads                          | at | said                                                                            |
      | a heading of another level            | 11   | 0      | # Working alongside others     | 11 | a section is a ## heading                                                       |
      | a section it doesn't know             | 24   | 0      | ## Finally                     | 24 | no section Finally: a goal has Working alongside others, The work and Done when |
      | a section given twice                 | 24   | 0      | ## The work                    | 24 | The work is given twice                                                         |
      | a paragraph after the manners' list   | 18   | 0      | Then more.                     | 18 | Working alongside others is words, then one list                                |
      | a paragraph under The work            | 19   | 0      | Then these.                    | 19 | The work is one list                                                            |
      | a list above the sections             | 10   | 0      | - Open it                      | 10 | a list goes under The work                                                      |
      | a manner with sub-items               | 16   | 0      | - Say why.\n  - always         | 16 | a manner has no sub-items                                                       |
      | a fact of Done when with sub-items    | 27   | 0      | - Nothing undone\n  - really   | 27 | a fact has no sub-items                                                         |
      | backticks in Done when                | 27   | 0      | - Nothing undone `as x`        | 27 | a fact takes no backticks                                                       |
      | a brace in Done when naming nothing   | 27   | 0      | - {nothing} is closed          | 27 | {nothing} is no input and no name given above                                   |
