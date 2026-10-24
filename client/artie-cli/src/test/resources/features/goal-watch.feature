Feature: A goal that keeps watching: Whenever, Until, Once, Or give up and Otherwise, read from the goal file
  spec/ideas/goals-that-work-alongside-others.md, "Review, 2026-10-23". A goal
  in steps may watch: Whenever repeats the steps under it on each matching
  change, and Until, at the list's top level, says when the watch ends. A
  step that begins Once waits for its condition, then does the rest of its
  words; on the lines under it, Or give up after a time ends the wait, and
  Otherwise says what to do instead. The front matter may name the run's
  manners. --plan-only reads it all and prints it; a run runs the watch,
  each step decided once and replayed (spec/PLAN-WATCH-GOALS.md), and, as
  any goal, needs a reasoner.

  Background:
    Given the file "watch.goal.md" is
      """
      ---
      start: scripted://service/
      manners: alongside-others
      inputs:
        owner:
        repo: rockets
        label: dependabot
      ---

      Merge what is labelled {label}.

      - In {owner}/{repo}
        - Open an issue titled "Auto-merge watch" `as watch`
        - Whenever a pull request labelled {label} changes `as pr`
          - Once it can be merged, merge it by squashing.
            Or give up after 10 minutes.
            Otherwise, say on {pr} why it was not merged.
      - Until someone closes {watch}
      """

  Scenario: --plan-only prints the watch, each step with its line, and sends nothing
    When artie is run with
      | argument      |
      | run           |
      | watch.goal.md |
      | --plan-only   |
    Then artie exits 0
    And artie prints
      | line                                                                 |
      | start scripted://service/                                            |
      | manners alongside-others                                             |
      | inputs owner, repo, label                                            |
      | context: Merge what is labelled {label}.                             |
      | line 12: In {owner}/{repo}                                           |
      | line 13:   Open an issue titled "Auto-merge watch" (as watch)        |
      | line 14:   whenever: a pull request labelled {label} changes (as pr) |
      | line 15:     once: it can be merged, then: merge it by squashing.    |
      | line 16:       give up after 10 minutes                              |
      | line 17:       otherwise: say on {pr} why it was not merged.         |
      | line 18: until: someone closes {watch}                               |
      | a goal in steps: 5 steps, nothing sent                               |

  Scenario: A step's tail is printed in the file's order, Otherwise before Or give up when written so
    Given line 16 of "watch.goal.md" reads 'Otherwise, say on {pr} why it was not merged.', indented 6
    And line 17 of "watch.goal.md" reads 'Or give up after 2 hours.', indented 6
    When artie is run with
      | argument      |
      | run           |
      | watch.goal.md |
      | --plan-only   |
    Then artie exits 0
    And artie prints
      | line                                                                 |
      | start scripted://service/                                            |
      | manners alongside-others                                             |
      | inputs owner, repo, label                                            |
      | context: Merge what is labelled {label}.                             |
      | line 12: In {owner}/{repo}                                           |
      | line 13:   Open an issue titled "Auto-merge watch" (as watch)        |
      | line 14:   whenever: a pull request labelled {label} changes (as pr) |
      | line 15:     once: it can be merged, then: merge it by squashing.    |
      | line 16:       otherwise: say on {pr} why it was not merged.         |
      | line 17:       give up after 2 hours                                 |
      | line 18: until: someone closes {watch}                               |
      | a goal in steps: 5 steps, nothing sent                               |

  Scenario: A Whenever on one line says what to do each time after a comma, as a step under it
    Given the file "short.goal.md" is
      """
      ---
      start: scripted://service/
      ---

      - On the pull request `as pr`
        - Whenever someone requests changes, leave it, and say so.
      - Until {pr} is closed
      """
    When artie is run with
      | argument      |
      | run           |
      | short.goal.md |
      | --plan-only   |
    Then artie exits 0
    And artie prints
      | line                                         |
      | start scripted://service/                    |
      | line 5: On the pull request (as pr)          |
      | line 6:   whenever: someone requests changes |
      | line 6:     leave it, and say so.            |
      | line 7: until: {pr} is closed                |
      | a goal in steps: 4 steps, nothing sent       |

  Scenario: A watch runs as any goal does, so a run with no reasoner stops before its first request
    When artie is run with
      | argument      |
      | run           |
      | watch.goal.md |
    Then artie exits 2
    And artie's last line is: stopped before the first request: a goal needs --reasoner

  Scenario Outline: A watch that breaks a rule stops before its first request: <case>
    Given line <line> of "watch.goal.md" reads '<reads>', indented <indent>
    When artie is run with
      | argument      |
      | run           |
      | watch.goal.md |
      | --plan-only   |
    Then artie exits 2
    And artie's last line is: stopped before the first request: watch.goal.md:<at>: <said>

    Examples:
      | case                              | line | indent | reads                                            | at | said                                                          |
      | manners it doesn't know           | 3    | 0      | manners: polite                                  | 3  | manners: alongside-others is the one there is                 |
      | a Whenever with nothing under it  | 13   | 2      | - Whenever an issue opens                        | 13 | Whenever says what to do each time: steps under it, or a comma then the step |
      | a Whenever with a limit           | 14   | 2      | - Whenever a pull request changes `as pr` `moves 3` | 14 | Whenever takes only as NAME                                |
      | an Until inside the list          | 13   | 2      | - Until someone closes it                        | 13 | Until ends the watch: it goes at the top of the list          |
      | an Until with sub-items           | 18   | 0      | - Until someone closes {watch}\n  - soon         | 18 | Until has no sub-items                                        |
      | an Until with no Whenever         | 14   | 2      | - Open a pull request `as pr`                    | 18 | Until ends a watch: the goal has no Whenever                  |
      | an Until naming nothing           | 18   | 0      | - Until someone closes {nothing}                 | 18 | {nothing} is no input and no name given above                 |
      | a Once with no comma              | 15   | 4      | - Once it can be merged merge it                 | 15 | Once says what to wait for, a comma, then what to do          |
      | a give-up under no Once           | 15   | 4      | - Merge it by squashing.                         | 16 | Or give up ends a wait: it goes under a Once                  |
      | a give-up with no time            | 16   | 6      | Or give up soon.                                 | 16 | Or give up after N seconds, minutes or hours                  |
      | a give-up given twice             | 17   | 6      | Or give up after 5 minutes.                      | 17 | Or give up is given twice                                     |
      | an Otherwise with nothing after   | 17   | 6      | Otherwise,                                       | 17 | Otherwise, says what to do instead                            |
      | backticks on a give-up            | 16   | 6      | Or give up after 10 minutes. `as late`           | 16 | Or give up and Otherwise take no backticks                    |
      | an Otherwise as a bullet          | 17   | 6      | - Otherwise, say why.                            | 17 | Otherwise goes on the line under its step, not as a bullet    |
