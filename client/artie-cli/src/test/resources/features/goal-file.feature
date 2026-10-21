Feature: A goal file in Markdown: front matter, then words or steps, every rule checked before the first request
  spec/PLAN-GOAL-MARKDOWN.md. A goal file, NAME.goal.md, begins with a YAML
  front matter: where to start, the inputs, the limits. Its body is
  paragraphs, a goal in words; or paragraphs, then one bulleted list, a goal
  in steps. A name in braces is an input or a resource a step named. The
  file's own keywords ignore case; names keep it. --plan-only reads the file,
  checks it, and prints what it read, sending nothing. A file that breaks a
  rule stops the run before its first request, naming the line and what to
  write instead.

  Background:
    Given the file "task.goal.md" is
      """
      ---
      start: scripted://service/
      Inputs:
        owner:
        repo: rockets
      ---

      Close the crash issue.

      - In {owner}/{repo}
        - Open an issue titled "Crash on start" `As issue`
        - On {issue}
          - Comment "Fixed in #{issue.number}." `moves 4`
          - Close it `reasoned`
          - wait: it can be reopened
          - VERIFY:
            - it is closed
      """

  Scenario: --plan-only prints the steps it read, each with its line, and sends nothing
    When artie is run with
      | argument     |
      | run          |
      | task.goal.md |
      | --plan-only  |
    Then artie exits 0
    And artie prints
      | line                                                         |
      | start scripted://service/                                    |
      | inputs owner, repo                                           |
      | context: Close the crash issue.                              |
      | line 10: In {owner}/{repo}                                   |
      | line 11:   Open an issue titled "Crash on start" (as issue)  |
      | line 12:   On {issue}                                        |
      | line 13:     Comment "Fixed in #{issue.number}." (moves 4)   |
      | line 14:     Close it (reasoned)                             |
      | line 15:     wait: it can be reopened                        |
      | line 16:     verify: it is closed                            |
      | a goal in steps: 7 steps, nothing sent                       |

  Scenario: A goal in words is paragraphs only, read sentence by sentence
    Given the file "words.goal.md" is
      """
      ---
      start: scripted://service/
      inputs:
        number:
      ---

      Open issue {number}. Close it.
      """
    When artie is run with
      | argument      |
      | run           |
      | words.goal.md |
      | --plan-only   |
    Then artie exits 0
    And artie prints
      | line                                    |
      | start scripted://service/               |
      | inputs number                           |
      | sentence 1: Open issue {number}.        |
      | sentence 2: Close it.                   |
      | a goal in words: 2 sentences, nothing sent |

  Scenario Outline: A goal file that breaks a rule stops before its first request: <case>
    Given line <line> of "task.goal.md" reads '<reads>', indented <indent>
    When artie is run with
      | argument     |
      | run          |
      | task.goal.md |
      | --plan-only  |
    Then artie exits 2
    And artie's last line is: stopped before the first request: task.goal.md:<at>: <said>

    Examples:
      | case                           | line | indent | reads                                              | at | said                                                                       |
      | no front matter                | 1    | 0      |                                                    | 1  | a goal file begins with its front matter, between --- lines                |
      | no start                       | 2    | 0      |                                                    | 1  | start names the bookmark                                                   |
      | a key it doesn't know          | 2    | 0      | start: scripted://service/\nrung: L2               | 3  | no key rung: the front matter has start, inputs and limits                 |
      | a key given twice              | 2    | 0      | start: scripted://service/\nStart: x               | 3  | Start is start again                                                       |
      | an input form it doesn't know  | 5    | 2      | repo: { default: rockets, ask: which }             | 5  | input repo has no key ask                                                  |
      | names differing only in case   | 5    | 2      | Owner: x                                           | 5  | Owner differs from owner only in case                                      |
      | a limit that is no limit       | 2    | 0      | start: scripted://service/\nlimits: { moves: 0 }   | 3  | limits: moves is a whole number above 0                                    |
      | a heading                      | 8    | 0      | # Close the crash issue                            | 8  | a heading has no place in a goal: write paragraphs, then one list          |
      | a quote                        | 8    | 0      | > Close the crash issue.                           | 8  | a quote has no place in a goal: write paragraphs, then one list            |
      | a numbered list                | 10   | 0      | 1. In {owner}/{repo}                               | 10 | a numbered list: steps are bullets, and their order is the order           |
      | a paragraph after the list     | 17   | 6      | - it is closed\n\nThen rest.                       | 19 | a paragraph after the list: the list ends the goal                         |
      | a second list                  | 17   | 6      | - it is closed\n\n+ Again                          | 19 | a second list: a goal has one, and siblings in it use the same bullet      |
      | siblings with two bullets      | 12   | 2      | * On {issue}                                       | 12 | * after -: siblings use the same bullet                                    |
      | an indent of 3                 | 11   | 3      | - Open an issue titled "Crash on start" `as issue` | 11 | indent 2 spaces a level                                                    |
      | a marker it doesn't know       | 15   | 4      | - Wiat: it can be reopened                         | 15 | "Wiat:" is no marker: write Wait: or Verify:, or put the words another way |
      | a Wait: with sub-items         | 15   | 4      | - Wait: it can be reopened\n      - soon           | 15 | Wait: has no sub-items                                                     |
      | a Verify: with fact and facts  | 16   | 4      | - Verify: it is closed                             | 16 | Verify: has a fact after it, or facts under it, not both                   |
      | a Verify: with no fact         | 17   | 4      | - Close it again                                   | 16 | Verify: has a fact after it, or facts under it, not both                   |
      | a fact with sub-items          | 17   | 6      | - it is closed\n        - really                   | 17 | a fact has no sub-items                                                    |
      | markup in a step               | 13   | 4      | - *Comment* "Fixed."                               | 13 | emphasis, links and markup have no place in a step: write plain words      |
      | backticks inside the words     | 11   | 2      | - Open `as issue` an issue                         | 11 | backticks go at an item's end                                              |
      | backticks it doesn't know      | 11   | 2      | - Open an issue titled "Crash on start" `keep`     | 11 | `keep` is not as NAME, moves N or reasoned                                 |
      | a step limit that is no limit  | 13   | 4      | - Comment "Fixed." `moves 0`                       | 13 | moves is a whole number above 0                                            |
      | a name given twice             | 12   | 2      | - On {issue} `as issue`                            | 12 | issue is named twice                                                       |
      | a name an input has            | 11   | 2      | - Open an issue titled "Crash on start" `as repo`  | 11 | repo is an input already                                                   |
      | reasoned on a step with steps  | 12   | 2      | - On {issue} `reasoned`                            | 12 | reasoned marks a step with no sub-items                                    |
      | backticks on a Wait:           | 15   | 4      | - Wait: it can be reopened `reasoned`              | 15 | Wait: and Verify: take no backticks                                        |
      | a brace naming nothing         | 13   | 4      | - Comment "Fixed in #{issu.number}."               | 13 | {issu} is no input and no name given above                                 |
      | a brace near a name            | 13   | 4      | - Comment "Fixed in #{Issue.number}."              | 13 | {Issue}: did you mean {issue}?                                             |
      | a name used before it is given | 10   | 0      | - In {owner}/{issue}                               | 10 | {issue} is named below, at line 11                                         |
      | a member of an input           | 13   | 4      | - Comment "Fixed in #{owner.id}."                  | 13 | {owner} is an input: it has no members                                     |
      | a brace left open              | 13   | 4      | - Comment "Fixed in #{issue.number."               | 13 | a { with no }: write {{ for a brace                                        |
