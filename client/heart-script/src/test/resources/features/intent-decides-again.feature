Feature: An intent step decides again on what is there now, after its act was refused as stale
  exhibits/mcp-over-openapi, the concurrent edit. An intent step's act is
  refused as stale: someone else changed the resource since the reasoner
  chose from it. SPEC §6.2: the stale act is never sent again; the intent
  is derived again against the new state. A script's own act has only its
  values, so it stops (decide { } is how a script works its values out
  again). An intent step still has the person's words, and a reasoner: the
  client reads the resource again, and asks the reasoner again, told what
  it chose, that it was refused, and what changed since. Words that build
  on what is there ("add to its state") are decided again on the new
  state; words that would write over the other person's change are asked
  about, not sent. At most three decisions in all; then the stale refusal
  stops the script, as before.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
    And these forms
      | at         | relation   | leads to   | outcome |
      | /          | issues-get | /issues/10 | done    |
      | /issues/10 | edit       | /issues/10 | done    |
    And the reasoner chooses, by the state it holds
      | intent                   | holding       | selection | relation | fields                              |
      | add triaged to its state | open          | act       | edit     | {"state": "open, triaged"}          |
      | add triaged to its state | open, on hold | act       | edit     | {"state": "open, on hold, triaged"} |
      | set its state to triaged | open          | act       | edit     | {"state": "triaged"}                |
    And the reasoner asks, by the state it holds
      | intent                   | holding       | question                                                         |
      | set its state to triaged | open, on hold | Someone else set its state to "open, on hold"; replace it anyway? |
    And "/issues/10" becomes "open, on hold" after 1 more reads

  Scenario: Refused as stale, the intent is read again and decided again, the reasoner told what changed
    Given the server will refuse "edit" at "/issues/10" as stale 1 times: "The issue changed since it was read."
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act(intent = "add triaged to its state")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                                    |
      | enter  | scripted://service/                     |
      | act    | issues-get {"number":10}                |
      | reason | add triaged to its state                |
      | act    | edit {"state":"open, triaged"}          |
      | return | /issues/10                              |
      | reason | add triaged to its state                |
      | act    | edit {"state":"open, on hold, triaged"} |
    And the reasoner was told, besides the words, in order
      | told                                                                                                                                                                                                                                                               |
      | Your choice here, edit with {"state":"open, triaged"}, was refused: The issue changed since it was read. Since then: state was "open", now "open, on hold". Decide again from what is there now. If these words would undo or write over that change, do not act: ask, naming it. |

  Scenario Outline: How an intent step refused as stale ends: <case>
    Given the server will refuse "edit" at "/issues/10" as stale <times> times: "The issue changed since it was read."
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act(intent = "<intent>")
      }
      """
    Then <ended>

    Examples:
      | case                                         | intent                   | times | ended                                                                                                                                                       |
      | words that build on the state: decided again | add triaged to its state | 1     | the script finished                                                                                                                                         |
      | words that would write over theirs: it asks  | set its state to triaged | 1     | the script stopped at step 2, "intent": unclear to the reasoner, refused by the client: "Someone else set its state to "open, on hold"; replace it anyway?" |
      | refused on all three decisions: it stops     | add triaged to its state | 3     | the script stopped at step 2, "intent": stale, refused by the server: "The issue changed since it was read."                                                |
