Feature: A goal that keeps watching runs: each step decided once, then replayed with no model
  spec/PLAN-WATCH-GOALS.md, slice 4a. A watch goal runs as any goal in steps
  until its Whenever. From there each step is decided by the planner, and an
  act's form and values by the reasoner, the first time the run needs it,
  live, on a real resource; every later time, for every other resource and
  every later look, the decision is replayed with no model. The Whenever's
  list is reached as the planner reached it; which members match is the
  check the planner made at the first; a Once waits for the act the planner
  named; Otherwise is decided the first time a wait gives up; the Until is
  the check the planner made on the resource it names. A value the reasoner
  chose is read again, where it came from, for each resource. The gateway
  and a small GitHub run in process; the planner and the reasoner are
  scripted, and count as the model.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title            | state | author |
      | 10     | Auto-merge watch | open  | ada    |
    And "acme/rockets" has pull request 1 on the small GitHub, open, titled "Bump left-pad to 1.0.1"
    And "acme/rockets" has pull request 2 on the small GitHub, open, titled "Bump left-pad to 1.0.2"
    And "acme/rockets" has pull request 3 on the small GitHub, open, titled "Bump left-pad to 1.0.3"
    And "acme/rockets" has pull request 4 on the small GitHub, open, titled "Refuse a bed with no name"
    And pull request 1 is labelled "dependabot" on the small GitHub
    And pull request 2 is labelled "dependabot" on the small GitHub
    And pull request 3 is labelled "dependabot" on the small GitHub
    And pull request 1 is a draft on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub once pull request 1 is closed
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the run's clock moves only when the run pauses
    And the file "merge.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Merge the pull requests labelled {label}.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever a pull request labelled {label} changes `as pr`
        - Once it can be merged, merge it by squashing.
          Or give up after 15 seconds.
          Otherwise, close it.
      - Until {watch} is closed
      """

  Scenario: Each step is decided once, the first time it is needed, and replayed for every other pull request
    Pull request 4 is the first member read, so the match is decided there,
    and it doesn't match. Pull request 3 decides the wait, and, being ready,
    the merge; pull request 2 is merged by the replay, with no model.
    Pull request 1 is a draft: its wait gives up at the second look, which
    decides Otherwise. Closing it closes the watch's issue, and the Until,
    decided after the first look, ends the watch.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And pull request 1 of "acme/rockets" is closed and not merged on the small GitHub
    And pull request 4 of "acme/rockets" is open on the small GitHub
    And the planner was asked 10 times
    And ARTIE's reasoner was asked 4 times
    And at move 3 the planner was told the step now is "whenever: a pull request labelled dependabot changes: from here, reach the list to watch, by a form or a link, or go back to the start if it isn't reached from here; when a form can narrow the list to what the Whenever says (a filter), narrow it, so each look reads only those; it is read again at every look"

  Scenario: If is checked as decided once; a pull request it holds for is left alone, and said so once
    Pull request 2 is labelled hold too. The If's check is decided at the
    first member that matches, pull request 3, where it doesn't hold; at
    pull request 2 it holds, so its other steps are left, and a note says
    so. The planner is shown where it is among the steps, inside the
    Whenever.
    Given pull request 2 is labelled "hold" on the small GitHub
    And the file "hold.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Merge the pull requests labelled {label}, unless someone holds them.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever a pull request labelled {label} changes `as pr`
        - If it's labelled hold, leave it, and say so once.
        - Once it can be merged, merge it by squashing.
          Or give up after 15 seconds.
          Otherwise, close it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | check |                                              |       |              | {"github:labels": ["hold"]}       |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "hold.goal.md" runs with ARTIE, captured in "hold.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
    And pull request 2 of "acme/rockets" is open on the small GitHub
    And the run noted one note beginning "Left https://github.gateway.test/repos/acme/rockets/pulls/2:"
    And the planner was asked 11 times
    And the run's capture keeps each check as decided, with where it was decided
      | move                                                                                                                                         |
      | whenever: a pull request labelled dependabot changes: decided the check {"github:labels":["dependabot"]}, at https://github.gateway.test/repos/acme/rockets/pulls/4 |
      | if: it's labelled hold: decided the check {"github:labels":["hold"]}, at https://github.gateway.test/repos/acme/rockets/pulls/3                                   |
      | until: https://github.gateway.test/repos/acme/rockets/issues/10 is closed: decided the check {"state":"closed"}, at https://github.gateway.test/repos/acme/rockets/issues/10 |
    And at move 5 the planner was told the step now is "if: it's labelled hold: the check that holds exactly when that is true of the member, and fails on a member it isn't true of; made on this member, then on every member after"
    And at move 5 the planner was shown the steps
      | line                                                             |
      | [done] Open issue 10 of acme/rockets (as watch)                  |
      | [in] Whenever a pull request labelled {label} changes (as pr)    |
      | [now] If it's labelled hold, leave it, and say so once.          |

  Scenario Outline: Otherwise is decided as a chain of acts, a check in it leaving the rest undone where it holds: <case>
    "Say why, unless it is said already" is a read, a check and a write
    (spec/PLAN-LISTENERS.md, slice 0). Pull requests 2 and 1 are drafts, so
    both waits give up at the second look, pull request 2 first. There the
    planner reads its comments, checks for the server's reason among them,
    opens its issue, and comments with the reason. Where the check holds,
    the rest is left undone, for that pull request; a chain cut short there
    is decided further at the next pull request that needs it. Every act is
    kept, and the whole chain is replayed, the reason read again each time.
    The reasoner is told the server's reason, unless the planner's words
    already say it: found by the live seed bench run b14, where a comment
    said the reason twice.
    Given pull request 2 is a draft on the small GitHub
    And "acme/rockets" has these issues on the small GitHub
      | number | title                  | state | author |
      | 1      | Bump left-pad to 1.0.1 | open  | ada    |
      | 2      | Bump left-pad to 1.0.2 | open  | ada    |
    And issue 2 of "acme/rockets" has these comments on the small GitHub
      | id | author | body        |
      | 1  | ada    | <said on 2> |
    And issue 10 of "acme/rockets" is closed on the small GitHub once issue 1 has a new comment
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its pull requests are listed 30 times
    And the file "say.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Merge the pull requests labelled {label}, or say why not.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever a pull request labelled {label} changes `as pr`
        - Once it can be merged, merge it by squashing.
          Or give up after 15 seconds.
          Otherwise, say on it why it wasn't merged, unless that is said there already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                       | opens | relation     | facts                                                                |
      | act   | open issue 10 of acme/rockets               | yes   |              |                                                                      |
      | done  |                                             |       |              |                                                                      |
      | act   | list the open pull requests of acme/rockets | yes   |              |                                                                      |
      | check |                                             |       |              | {"github:labels": ["dependabot"]}                                    |
      | wait  |                                             |       | github:merge |                                                                      |
      | act   | merge it by squashing                       |       |              |                                                                      |
      | done  |                                             |       |              |                                                                      |
      | check |                                             |       |              | {"state": "closed"}                                                  |
      | act   | read its comments                           |       |              |                                                                      |
      | check |                                             |       |              | {"item": "A draft can't be merged; mark it ready for review first."} |
      | act   | open its issue                              | yes   |              |                                                                      |
      | act   | <says>                                      |       |              |                                                                      |
      | done  |                                             |       |              |                                                                      |
    And ARTIE's reasoner will choose, in order
      | form              | follow                      | fields                                                                        |
      | github:issues-get |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                      |
      | github:pulls-list |                             | {"owner": "acme", "repo": "rockets", "state": "open"}                         |
      | github:merge      |                             | {"merge_method": "squash"}                                                    |
      |                   | github:issues-list-comments | {}                                                                            |
      |                   | github:issues-get           | {}                                                                            |
      | github:comment    |                             | {"body": "Not merged: A draft can't be merged; mark it ready for review first."} |
    When the goal "say.goal.md" runs with ARTIE, captured in "say.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
    And issue 1 of "acme/rockets" now has these comments on the small GitHub
      | author | body                                                                   |
      | ada    | Not merged: A draft can't be merged; mark it ready for review first. |
    And issue 2 of "acme/rockets" now has these comments on the small GitHub
      | author | body        |
      | ada    | <said on 2> |
      | <new>  | <new on 2>  |
    And the planner was asked 13 times
    And ARTIE's reasoner was asked 6 times
    And at move 9 the planner was told the step now includes "the wait gave up, and the server says why: A draft can't be merged; mark it ready for review first. Say that reason in the server's words, and claim nothing the watch won't do"
    And ARTIE's reasoner was last asked to "<asked>"

    Examples:
      | case                                   | said on 2                                                             | new | new on 2                                                             | says                                                                                     | asked                                                                                                                                                                                                  |
      | said on neither, both told             | Looks fine.                                                            | ada | Not merged: A draft can't be merged; mark it ready for review first. | say why it wasn't merged                                                                 | say why it wasn't merged. The wait gave up, and the server says why: A draft can't be merged; mark it ready for review first. Say that reason in the server's words, and claim nothing the watch won't do. |
      | said on 2 already, 1 told              | Not merged: A draft can't be merged; mark it ready for review first. |     |                                                                      | say why it wasn't merged                                                                 | say why it wasn't merged. The wait gave up, and the server says why: A draft can't be merged; mark it ready for review first. Say that reason in the server's words, and claim nothing the watch won't do. |
      | the planner's words hold the reason    | Looks fine.                                                            | ada | Not merged: A draft can't be merged; mark it ready for review first. | say on it: Not merged: A draft can't be merged; mark it ready for review first.         | say on it: Not merged: A draft can't be merged; mark it ready for review first.                                                                                                                       |

  Scenario: A planner that stops while a watch decides ends the run, saying why, rather than looking again
    A failed look is tried again at the next; a decision the planner won't
    make would only be asked again, and again.
    Given the planner will move
      | move  | words                                        | opens | relation | facts |
      | act   | open issue 10 of acme/rockets                | yes   |          |       |
      | done  |                                              |       |          |       |
      | act   | list the open pull requests of acme/rockets  | yes   |          |       |
      | stop  | I can't tell which pull requests match       |       |          |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the goal run stopped, saying "I can't tell which pull requests match"
    And the planner was asked 4 times

  Scenario: A move the part being decided doesn't take is refused, and the planner told which it takes
    While the match is decided, the planner tries an act; it is told only a
    check or a stop is taken there, and makes the check.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | act   | see its labels                               |       |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the planner was told "act: refused, here only check, stop"
    And ARTIE's reasoner was asked 4 times

  Scenario: An act refused while a step is decided is told to the planner, which decides again
    The reasoner first chooses a form the entry doesn't offer, so the
    client refuses it before anything is sent; the planner is told, and its
    next act reaches the list.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | act   | search the pull requests of acme/rockets     | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And the planner was asked 11 times
    And ARTIE's reasoner was asked 5 times

  Scenario: A Whenever whose list isn't reached from where it stands goes back to the start, and watches from there
    The Whenever is under the issue, which leads to no list of pull
    requests; the planner goes back to the start, where the list's form is.
    Given line 10 of "merge.goal.md" reads '  - Whenever a pull request labelled {label} changes `as pr`', indented 0
    And line 11 of "merge.goal.md" reads '    - Once it can be merged, merge it by squashing.', indented 0
    And line 12 of "merge.goal.md" reads '      Or give up after 15 seconds.', indented 0
    And line 13 of "merge.goal.md" reads '      Otherwise, close it.', indented 0
    And the planner will move
      | move  | words                                        | opens | relation     | facts                             | to        |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
      | back  |                                              |       |              |                                   | the start |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |           |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |           |
      | wait  |                                              |       | github:merge |                                   |           |
      | act   | merge it by squashing                        |       |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
      | check |                                              |       |              | {"state": "closed"}               |           |
      | act   | close it                                     |       |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And the planner was asked 11 times

  Scenario: Two Whenevers side by side watch at once, each a listener of its own: a slow read under one holds up only it
    spec/PLAN-LISTENERS.md, slice 1. Whenevers side by side in a goal's
    steps are one watch, each Whenever a listener on a thread of its own,
    with a client of its own. Each Whenever's first look is made in turn,
    as the goal lists them, so its parts are decided in the goal's order;
    after it, each looks on its own, replaying what was decided. The first
    look closes issue 11, merges 3 and 2, and leaves 1, a draft, waiting.
    The first list of issues read after pull request 3 is merged, the issue
    Whenever's first on its own, is held until pull request 1 is merged: in
    one loop, the issues first, the merge would come only after the read
    gave up waiting. Once it is answered, the watch's issue is closed.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                | state | author |
      | 11     | Veracode scan failed | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "scan" on the small GitHub
    And pull request 1 is a draft on the small GitHub until it is next read
    And the small GitHub holds its first answer to "/repos/acme/rockets/issues" after pull request 3 is merged, until pull request 1 is merged, at most 5 seconds
    And issue 10 of "acme/rockets" is closed on the small GitHub once that held answer is given
    And the file "both.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Close the failed scans, and merge the pull requests labelled {label}.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled scan changes `as scan`
        - Close it.
      - Whenever a pull request labelled {label} changes `as pr`
        - Once it can be merged, merge it by squashing.
          Or give up after 1 hour.
          Otherwise, close it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                                | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                        | yes   |              |                                   |
      | done  |                                                      |       |              |                                   |
      | act   | list the open issues of acme/rockets labelled scan   | yes   |              |                                   |
      | check |                                                      |       |              | {"state": "open"}                 |
      | act   | close it                                             |       |              |                                   |
      | done  |                                                      |       |              |                                   |
      | act   | list the open pull requests of acme/rockets          | yes   |              |                                   |
      | check |                                                      |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                                      |       | github:merge |                                   |
      | act   | merge it by squashing                                |       |              |                                   |
      | done  |                                                      |       |              |                                   |
      | check |                                                      |       |              | {"state": "closed"}               |
    And ARTIE's reasoner will choose, in order
      | form                        | fields                                                                     |
      | github:issues-get           | {"owner": "acme", "repo": "rockets", "issue_number": 10}                   |
      | github:issues-list-for-repo | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "scan"}    |
      | github:close                | {}                                                                         |
      | github:pulls-list           | {"owner": "acme", "repo": "rockets", "state": "open"}                      |
      | github:merge                | {"merge_method": "squash"}                                                 |
    When the goal "both.goal.md" runs with ARTIE, captured in "both.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And issue 11 of "acme/rockets" is now "closed" on the small GitHub
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
      | 1      |
    And the small GitHub gave that held answer only after pull request 1 was merged
    And the planner was asked 12 times
    And ARTIE's reasoner was asked 5 times

  Scenario: Whenevers side by side whose lists aren't reached from where they stand each go back to the start, and listen from there
    Found by the live run g12: the Whenevers are under the issue, which
    leads to no list; the planner goes back to the start for each list, and
    each listener looks from where its list is reached.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                | state | author |
      | 11     | Veracode scan failed | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "scan" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its pull requests are listed 4 times
    And the file "under.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Close the failed scans, and merge the pull requests labelled {label}.

      - Open issue 10 of acme/rockets `as watch`
        - Whenever an issue labelled scan changes `as scan`
          - Close it.
        - Whenever a pull request labelled {label} changes `as pr`
          - Once it can be merged, merge it by squashing.
            Or give up after 1 hour.
            Otherwise, close it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                              | opens | relation     | facts                             | to        |
      | act   | open issue 10 of acme/rockets                      | yes   |              |                                   |           |
      | done  |                                                    |       |              |                                   |           |
      | back  |                                                    |       |              |                                   | the start |
      | act   | list the open issues of acme/rockets labelled scan | yes   |              |                                   |           |
      | check |                                                    |       |              | {"state": "open"}                 |           |
      | act   | close it                                           |       |              |                                   |           |
      | done  |                                                    |       |              |                                   |           |
      | back  |                                                    |       |              |                                   | the start |
      | act   | list the open pull requests of acme/rockets        | yes   |              |                                   |           |
      | check |                                                    |       |              | {"github:labels": ["dependabot"]} |           |
      | wait  |                                                    |       | github:merge |                                   |           |
      | act   | merge it by squashing                              |       |              |                                   |           |
      | done  |                                                    |       |              |                                   |           |
      | check |                                                    |       |              | {"state": "closed"}               |           |
    And ARTIE's reasoner will choose, in order
      | form                        | fields                                                                  |
      | github:issues-get           | {"owner": "acme", "repo": "rockets", "issue_number": 10}                |
      | github:issues-list-for-repo | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "scan"} |
      | github:close                | {}                                                                      |
      | github:pulls-list           | {"owner": "acme", "repo": "rockets", "state": "open"}                   |
      | github:merge                | {"merge_method": "squash"}                                              |
    When the goal "under.goal.md" runs with ARTIE, captured in "under.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And issue 11 of "acme/rockets" is now "closed" on the small GitHub
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And the planner was asked 14 times

  Scenario: What a step does to a member is the watch's own change, not one it answers again
    Found by the live run g13: a comment changes the issue it is on, and the
    next look took the issue as changed and commented again, look after
    look. What the member is once the steps under the Whenever are done is
    what the watch remembers it as; only a change someone else makes is
    answered.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                | state | author |
      | 11     | Veracode scan failed | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "scan" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 8 times
    And the file "seen.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer the failed scans.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled scan changes `as scan`
        - Say on it that the watch has seen it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                              | opens | relation | facts               |
      | act   | open issue 10 of acme/rockets                      | yes   |          |                     |
      | done  |                                                    |       |          |                     |
      | act   | list the open issues of acme/rockets labelled scan | yes   |          |                     |
      | check |                                                    |       |          | {"state": "open"}   |
      | act   | say on it that the watch has seen it               |       |          |                     |
      | done  |                                                    |       |          |                     |
      | check |                                                    |       |          | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form                        | fields                                                                  |
      | github:issues-get           | {"owner": "acme", "repo": "rockets", "issue_number": 10}                |
      | github:issues-list-for-repo | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "scan"} |
      | github:comment              | {"body": "Seen by the watch."}                                          |
    When the goal "seen.goal.md" runs with ARTIE, captured in "seen.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body               |
      | ada    | Seen by the watch. |
    And at move 6 the planner was shown the resource's "comments" as "1"
    And at move 5 the planner was told the step now is "act: Say on it that the watch has seen it.: one act or more, in order, then done; an act the server answered is done, even where a read just after it doesn't show it yet; the acts after an act that opens are done on what it reached; a check is made on what the act before it reached, and where it holds, the acts after it are left undone: so a check that is to keep an act from being done, such as whether it was said already, comes before that act; whether something was said is checked on the words said, among the titles of what is listed, not on how many there are; a fact names a member of the state, or a relation it links to, its value words a link's title says"

  Scenario: Issues are matched by their labels, and an If that acts closes the one it holds for
    spec/PLAN-SEED-BENCH.md: a tray is an issue labelled tray, and its stage
    a label. The match is decided on the labels an issue embeds, as on a
    pull request's. The If's check holds on tray 14, transplanted, which is
    closed; tray 15, still sown, is left open.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                       | state | author |
      | 11     | Tray 14: Red Sails lettuce  | open  | ada    |
      | 12     | Tray 15: Bright Lights chard | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "tray" on the small GitHub
    And issue 11 of "acme/rockets" is labelled "transplanted" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "tray" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "sown" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 6 times
    And the file "trays.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Close each tray once it is transplanted.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled tray changes `as tray`
        - If it's labelled transplanted, close it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                              | opens | relation | facts                               |
      | act   | open issue 10 of acme/rockets                      | yes   |          |                                     |
      | done  |                                                    |       |          |                                     |
      | act   | list the open issues of acme/rockets labelled tray | yes   |          |                                     |
      | check |                                                    |       |          | {"github:labels": ["tray"]}         |
      | check |                                                    |       |          | {"github:labels": ["transplanted"]} |
      | act   | close it                                           |       |          |                                     |
      | done  |                                                    |       |          |                                     |
      | check |                                                    |       |          | {"state": "closed"}                 |
    And ARTIE's reasoner will choose, in order
      | form                        | fields                                                                  |
      | github:issues-get           | {"owner": "acme", "repo": "rockets", "issue_number": 10}                |
      | github:issues-list-for-repo | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "tray"} |
      | github:close                | {}                                                                      |
    When the goal "trays.goal.md" runs with ARTIE, captured in "trays.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" is now "closed" on the small GitHub
    And issue 12 of "acme/rockets" is now "open" on the small GitHub
    And the planner was asked 8 times
    And ARTIE's reasoner was asked 3 times

  Scenario: A list a step sent is sent again as a list, and a look that fails still keeps what the watch did
    Found by the live seed bench run b1: an alert's chain commented, then
    assigned the grower on duty, an input, as the list ["ada"]. Replayed on the
    next alert, the list was sent as the text of one, and refused; that look
    failed before the watch kept the alert as its comment left it, so each
    look after commented again. A list is replayed as a list of what each item
    came from, and what a member is once its steps are done is kept even when
    they fail.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 12 times
    And the file "alerts.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        grower: ada
      ---

      Answer each alert once, and hand it to the grower on duty.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it "Seen by the bench watch.", and assign it to {grower}.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts               |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                     |
      | done  |                                                     |       |          |                     |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                     |
      | check |                                                     |       |          | {"state": "open"}   |
      | act   | say on it "Seen by the bench watch."                |       |          |                     |
      | act   | assign it to ada                                    |       |          |                     |
      | done  |                                                     |       |          |                     |
      | check |                                                     |       |          | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form                        | fields                                                                   |
      | github:issues-get           | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:comment              | {"body": "Seen by the bench watch."}                                     |
      | github:assign               | {"assignees": ["ada"]}                                                   |
    When the goal "alerts.goal.md" runs with ARTIE, captured in "alerts.trace.yaml", given
      | name   | value |
      | grower | ada   |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 11 of "acme/rockets" is assigned to "ada" on the small GitHub
    And issue 12 of "acme/rockets" is assigned to "ada" on the small GitHub

  Scenario: A check the planner makes after an act that writes is kept before it, so a replay says nothing twice
    Found by the live runs g13, g14 and b1: told "say it, unless it's said
    already", the planner commented first, then read the comments and
    checked them. A check after an act that writes can keep nothing from
    being done, so the runner keeps the check, with the reads it is made on,
    before that act: the replay reads, checks, and writes only where the
    check fails. A person's comment on the first alert, after the watch's,
    changes it; the replay finds the watch's words there, and says nothing.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And "bob" comments "Checked: the heater is back on." on issue 11 of "acme/rockets" on the small GitHub once its issues are listed 5 times
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "said.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | act   | read its comments                                   |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
      |                             | github:issues-list-comments | {}                                                                       |
    When the goal "said.goal.md" runs with ARTIE, captured in "said.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And the planner was asked 9 times
    And the run's capture keeps each check as decided, with where it was decided
      | move                                                                                                                                                                   |
      | check: kept {"item":"Seen by the bench watch."}, at https://github.gateway.test/repos/acme/rockets/issues/11/comments; it holds here, so the acts after it are left undone |

  Scenario: A check after two acts that write is kept before the first of them
    Found by the live seed bench run b4: the planner commented, read the
    comments, assigned, and only then checked. Kept before the assign alone,
    the check still left the comment first, and the replay said it again.
    The check, and the reads it is made on, are kept before the first act
    that writes since the last check: a replay reads, checks, and does none
    of those acts where the check holds.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And "bob" comments "Checked: the heater is back on." on issue 11 of "acme/rockets" on the small GitHub once its issues are listed 5 times
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "both.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once, and hand it to ada.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, and assign it to ada, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | act   | read its comments                                   |       |          |                                      |
      | act   | assign it to ada                                    |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:assign               |                             | {"assignees": ["ada"]}                                                   |
    When the goal "both.goal.md" runs with ARTIE, captured in "both.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" is assigned to "ada" on the small GitHub

  Scenario: A step that says unless is done only once its check is made, and an act the server answered is done
    Found by the live seed bench run b2: told "say it, unless it's said
    already", the planner commented and said done, with no check, so nothing
    would keep a replay from saying it again; and it assigned an alert three
    times, a read just after each not showing it yet, as GitHub's copies
    sometimes lag. Done is refused while a step that says unless has no
    check; the check the planner then makes, after the comment, is kept
    before it. The planner is told an act the server answered is done.
    The capture keeps the watch as the script its decisions amount to, with
    no model in it, and the step it began at (spec/PLAN-LISTENERS.md, slice
    8): its Until reads again the issue the watch opened, by the address the
    run will keep as watchAt. artie generate writes it into the script made
    from the run, which then answers a new alert with no model at all.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And "bob" comments "Checked: the heater is back on." on issue 11 of "acme/rockets" on the small GitHub once its issues are listed 5 times
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "unless.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | read its comments                                   |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
      |                             | github:issues-list-comments | {}                                                                       |
    When the goal "unless.goal.md" runs with ARTIE, captured in "unless.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And the planner was told "done: refused, the step says unless"
    And at move 6 the planner was told the step now includes "an act the server answered is done, even where a read just after it doesn't show it yet"
    And the planner was asked 10 times
    And the capture "unless.trace.yaml" keeps the watch as this script, from step 4
      """
      watch(until = { var over = false; at(watchAt) { over = holds(mapOf("state" to "closed")) }; over }) {
          act("github:issues-list-for-repo", "owner" to "acme", "repo" to "rockets", "state" to "open", "labels" to "alert") {
              whenever {
                  if (holds(mapOf("state" to "open"))) {
                      var undone1 = false
                      follow("github:issues-list-comments") { undone1 = holds(mapOf("item" to "Seen by the bench watch.")) }
                      if (!undone1) act("github:comment", "body" to "Seen by the bench watch.") {}
                  }
              }
          }
      }
      """
    When artie generated "unless-gen.kts" from "unless.trace.yaml" and "unless.goal.md"
    And "acme/rockets" has these issues on the small GitHub
      | number | title                         | state | author |
      | 13     | Thrips on the bench 1 peppers | open  | ada    |
    And issue 13 of "acme/rockets" is labelled "alert" on the small GitHub
    And "unless-gen.kts" runs with ARTIE, captured in "unless-gen.trace.yaml", given
      | name | value |
    Then the run finished
    And the script "unless-gen.kts" has no line with "act(intent"
    And issue 13 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |

  Scenario: A check's values are the planner's own, and an act asked again just after it is made is refused
    Found by the live seed bench run b5: the first alert already had the
    watch's comment, and the planner checked that its comments numbered 1.
    The alert's own comment count was 1 too, so the check was kept as "as many
    as the alert says it has", which holds on every alert: a new alert was
    never told. A check's values come from the run's inputs, or are kept as
    the planner said them, never read again from the member. The planner
    also assigned the alert four times, and in run b13 three times; an act
    asked again in the words of the one just made is refused, not made
    again. A count is a poor check of what was said, as
    an alert with other comments shows: the planner is told to check the
    words said.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 11 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "count.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once, and hand it to ada.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
        - Assign it to ada.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts               |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                     |
      | done  |                                                     |       |          |                     |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                     |
      | check |                                                     |       |          | {"state": "open"}   |
      | act   | read its comments                                   |       |          |                     |
      | check |                                                     |       |          | {"count": 1}        |
      | act   | assign it to ada                                    |       |          |                     |
      | act   | assign it to ada                                    |       |          |                     |
      | done  |                                                     |       |          |                     |
      | act   | say on it that the watch has seen it                |       |          |                     |
      | done  |                                                     |       |          |                     |
      | check |                                                     |       |          | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:assign               |                             | {"assignees": ["ada"]}                                                   |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
    When the goal "count.goal.md" runs with ARTIE, captured in "count.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 11 of "acme/rockets" is assigned to "ada" on the small GitHub
    And issue 12 of "acme/rockets" is assigned to "ada" on the small GitHub
    And the planner was told "assign it to ada: refused, it was just made, and the server answered it"
    And at move 5 the planner was told the step now includes "whether something was said is checked on the words said, among the titles of what is listed, not on how many there are; a fact names a member of the state, or a relation it links to, its value words a link's title says"

  Scenario: A link followed in a chain is a read, though the planner says it opens: the acts after it are done on the member
    Found by the live seed bench run b7: the planner read the first alert's
    comments as an act that opens, and checked there. On a new alert the
    check failed, and the planner, asked inside the comments, found no form
    to say the words with, and stopped. A link followed is kept as a read:
    the check after it is made on what it reached, and the acts after that
    are done on the member.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 11 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "read.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | read its comments                                   | yes   |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
    When the goal "read.goal.md" runs with ARTIE, captured in "read.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |

  Scenario: A check after the acts it is to keep from being done again is refused where it fails right after them
    Found by the live seed bench runs b6 and b8: after it commented, the
    planner checked that the comments list's "comments" was 1. The list has
    no such member, so the check failed even there, after the comment, and
    every replay commented again. Here the planner checks a count the list
    doesn't have, under a name it does hold. A check kept before the acts that write
    must hold where it is made, right after them: one that fails there is
    refused, and the planner told why, where the check is made, what is
    there, and how a fact says a link's title (run b11). Found by run b9: a read, kept on the
    member, showed the planner nothing, and it read again and again; the
    planner is told what a read reached.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And "bob" comments "Checked: the heater is back on." on issue 11 of "acme/rockets" on the small GitHub once its issues are listed 5 times
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "fails.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | act   | read its comments                                   |       |          |                                      |
      | check |                                                     |       |          | {"count": 2}                         |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
      |                             | github:issues-list-comments | {}                                                                       |
    When the goal "fails.goal.md" runs with ARTIE, captured in "fails.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And the planner was told "check: refused, it fails here, right after the acts it is to keep from being done again, so a replay would do them again: it is made on https://github.gateway.test/repos/acme/rockets/issues/11/comments, which holds {\"count\":1}, and links item: Seen by the bench watch.; a fact names a member of that state, or a relation it links to, its value words a link's title says"
    And the planner was told "read its comments: read https://github.gateway.test/repos/acme/rockets/issues/11/comments, which holds"

  Scenario: A step is not done where its last check fails and nothing was done after it
    Found by the live seed bench run b10: the planner read the first alert's
    comments, saw the words there, checked a relation the comments don't
    have, and said done. The check failed, so nothing kept the step from
    acting, yet it had decided no act: a new alert was never told. Done is
    refused where the last check fails here and no act was made after it.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 11 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "undone.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                                      |
      | done  |                                                     |       |          |                                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                                      |
      | check |                                                     |       |          | {"state": "open"}                                    |
      | act   | read its comments                                   |       |          |                                                      |
      | check |                                                     |       |          | {"count": 2}                                         |
      | done  |                                                     |       |          |                                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."}                 |
      | act   | say on it that the watch has seen it                |       |          |                                                      |
      | done  |                                                     |       |          |                                                      |
      | check |                                                     |       |          | {"state": "closed"}                                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
    When the goal "undone.goal.md" runs with ARTIE, captured in "undone.trace.yaml", given
      | name | value |
    Then the planner was told "done: refused, the last check fails here, and nothing was done after it"
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |

  Scenario: A check naming what the resource it is made on doesn't hold is refused, and the planner told what it holds
    Found by the live seed bench runs b10 and b12: the planner read the
    comments, saw the words there, and checked "github:comments", which the
    comments list neither holds nor links to. The check could never hold,
    so the planner said the words again. A fact names a member of the state,
    or a relation it embeds or links to; one naming none of them is refused.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 11 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "named.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                             |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                                   |
      | done  |                                                     |       |          |                                                   |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                                   |
      | check |                                                     |       |          | {"state": "open"}                                 |
      | act   | read its comments                                   |       |          |                                                   |
      | check |                                                     |       |          | {"github:comments": ["Seen by the bench watch."]} |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."}              |
      | act   | say on it that the watch has seen it                |       |          |                                                   |
      | done  |                                                     |       |          |                                                   |
      | check |                                                     |       |          | {"state": "closed"}                               |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
    When the goal "named.goal.md" runs with ARTIE, captured in "named.trace.yaml", given
      | name | value |
    Then the planner was told "check: refused, github:comments is neither a member of https://github.gateway.test/repos/acme/rockets/issues/11/comments nor a relation it embeds or links to: it holds {\"count\":1}, and links item: Seen by the bench watch."
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |

  Scenario: On an alert with no comments, a check of the words fails, the words are said, and checked on a read after
    An empty list still names the relation of its items, with no titles, so
    a check of what they say is kept, and fails. The planner says the words,
    reads again, and checks: that check, with the read, is kept before the
    act, and a replay on an alert already told says nothing.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    And "bob" comments "Checked: the heater is back on." on issue 11 of "acme/rockets" on the small GitHub once its issues are listed 5 times
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "empty.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | read its comments                                   |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | act   | read its comments again                             |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
      |                             | github:issues-list-comments | {}                                                                       |
    When the goal "empty.goal.md" runs with ARTIE, captured in "empty.trace.yaml", given
      | name | value |
    Then the run finished
    And the planner was told "check: kept {\"item\":\"Seen by the bench watch.\"}, at https://github.gateway.test/repos/acme/rockets/issues/11/comments; it fails"
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                            |
      | ada    | Seen by the bench watch.        |
      | bob    | Checked: the heater is back on. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |

  Scenario: A value two inputs hold is read again from the input the step's words name
    Found by the live seed bench run b15: the owner and the grower were the
    same person, so the grower's name in a comment, and the assignee, were
    kept as the owner's, the first input that held it. A value more than one
    source holds is bound to an input the step's own words name, before any
    other: the words say which one the person meant.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "who.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        lead:
        grower:
      ---

      Hand each alert to the grower.

      - Open issue 10 of acme/rockets for {lead} `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Assign it to {grower}.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts               |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                     |
      | done  |                                                     |       |          |                     |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                     |
      | check |                                                     |       |          | {"state": "open"}   |
      | act   | assign it to ada                                    |       |          |                     |
      | done  |                                                     |       |          |                     |
      | check |                                                     |       |          | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form                        | follow | fields                                                                   |
      | github:issues-get           |        | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |        | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:assign               |        | {"assignees": ["ada"]}                                                   |
    When the goal "who.goal.md" runs with ARTIE, captured in "who.trace.yaml", given
      | name   | value |
      | lead   | ada   |
      | grower | ada   |
    Then the run finished
    And the capture "who.trace.yaml" keeps a watch script with a line with "act(\"github:assign\", \"assignees\" to listOf(grower)) {}"

  Scenario: The checks a watch decides read inputs again, so its script checks what it is given
    Found by the script made from the live seed bench run b15: its If kept
    the hold label as the text it was, so the script, given another label,
    would check the old one. Every check a watch decides, its match, its
    Ifs and its Until, binds each value to the input that holds it, as a
    chain's checks do, the inputs the step's words name first.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "hold.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label:
        hold:
      ---

      Answer each alert, unless someone objects.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled {label} changes `as alert`
        - If it's labelled {hold}, leave it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                           |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                 |
      | done  |                                                     |       |          |                                 |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                 |
      | check |                                                     |       |          | {"github:labels": ["alert"]}    |
      | check |                                                     |       |          | {"github:labels": ["on-hold"]}  |
      | check |                                                     |       |          | {"state": "closed"}             |
    And ARTIE's reasoner will choose, in order
      | form                        | follow | fields                                                                   |
      | github:issues-get           |        | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |        | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
    When the goal "hold.goal.md" runs with ARTIE, captured in "hold.trace.yaml", given
      | name  | value   |
      | label | alert   |
      | hold  | on-hold |
    Then the run finished
    And the capture "hold.trace.yaml" keeps a watch script with a line with "act(\"github:issues-list-for-repo\", \"owner\" to \"acme\", \"repo\" to \"rockets\", \"state\" to \"open\", \"labels\" to label)"
    And the capture "hold.trace.yaml" keeps a watch script with a line with "if (holds(mapOf(\"github:labels\" to listOf(label))))"
    And the capture "hold.trace.yaml" keeps a watch script with a line with "if (holds(mapOf(\"github:labels\" to listOf(hold)))) note("

  Scenario: An act refused before it was sent may be asked again in the same words
    Found by the live seed bench run b17: the reasoner's first choice for
    "read the comments" named a link the issue doesn't have, so the client
    refused it; asked again in the same words, it was refused as an act
    just made, though nothing was made, and the run ran out of moves. Only
    an act the server answered is one just made.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its issues are listed 14 times
    And the file "again.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Answer each alert once.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever an issue labelled alert changes `as alert`
        - Say on it that the watch has seen it, unless it's said already.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                               | opens | relation | facts                                |
      | act   | open issue 10 of acme/rockets                       | yes   |          |                                      |
      | done  |                                                     |       |          |                                      |
      | act   | list the open issues of acme/rockets labelled alert | yes   |          |                                      |
      | check |                                                     |       |          | {"state": "open"}                    |
      | act   | read its comments                                   |       |          |                                      |
      | act   | read its comments                                   |       |          |                                      |
      | check |                                                     |       |          | {"item": "Seen by the bench watch."} |
      | act   | say on it that the watch has seen it                |       |          |                                      |
      | done  |                                                     |       |          |                                      |
      | check |                                                     |       |          | {"state": "closed"}                  |
    And ARTIE's reasoner will choose, in order
      | form                        | follow                      | fields                                                                   |
      | github:issues-get           |                             | {"owner": "acme", "repo": "rockets", "issue_number": 10}                 |
      | github:issues-list-for-repo |                             | {"owner": "acme", "repo": "rockets", "state": "open", "labels": "alert"} |
      | github:merge                |                             | {"merge_method": "squash"}                                               |
      |                             | github:issues-list-comments | {}                                                                       |
      | github:comment              |                             | {"body": "Seen by the bench watch."}                                     |
    When the goal "again.goal.md" runs with ARTIE, captured in "again.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |

  Scenario: A fact of Done when the planner can't check as facts is left unchecked, and noted
    "Every pull request was merged or says why" is no member of one
    resource: the planner says so with done, and the run notes it.
    Given the file "close.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Close the watch issue.

      ## The work

      - Open issue 10 of acme/rockets `as watch`
        - Close it

      ## Done when

      - Every pull request was merged, or says why not
      """
    And the planner will move
      | move  | words                         | opens |
      | act   | open issue 10 of acme/rockets | yes   |
      | done  |                               |       |
      | act   | close it                      |       |
      | done  |                               |       |
      | done  |                               |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "close.goal.md" runs with ARTIE, captured in "close.trace.yaml", given
      | name | value |
    Then the run finished
    And the run noted one note beginning "Not checked: Every pull request was merged, or says why not"
    And the planner was last told the step now is "done when: Every pull request was merged, or says why not: check it only when members of the resource held say it; otherwise done, saying why"

  Scenario Outline: A goal in sections tells the planner its manners, and ends with the checks Done when names: <case>
    Given the file "close.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Close the watch issue.

      ## Working alongside others

      Leave what others changed.

      - Do not reopen what someone closed.

      ## The work

      - Open issue 10 of acme/rockets `as watch`
        - Close it

      ## Done when

      - {watch} is closed
      """
    And the planner will move
      | move  | words                         | opens | relation | facts   |
      | act   | open issue 10 of acme/rockets | yes   |          |         |
      | done  |                               |       |          |         |
      | act   | close it                      |       |          |         |
      | done  |                               |       |          |         |
      | check |                               |       |          | <facts> |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "close.goal.md" runs with ARTIE, captured in "close.trace.yaml", given
      | name | value |
    Then <ended>
    And the planner was shown the goal with "Do not reopen what someone closed."
    And the planner was last shown "watch" named, at an address ending "/issues/10"

    Examples:
      | case                         | facts               | ended                                                          |
      | the check holds              | {"state": "closed"} | the run finished                                               |
      | the check fails, and says so | {"state": "open"}   | the goal run stopped, saying "state: expected open, was closed" |
