Feature: Scoring a youth basketball game
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, B2. A game is one resource, /games/{id}.
  Its state says the score, the period, who has possession, each player's
  points and fouls, and each team's fouls this half. Its forms say what the
  scorer may record now, and nothing else.

  Youth rules: four periods; a player fouls out at 5 personal fouls; a team
  is in the bonus once the other team has 7 fouls in the half.

  Only the game's own rules are here. A play they refuse is a 422 of type
  rule-refused, naming the field. What every HEART server does (428, 412,
  409, 422 for a body its schema refuses, the layers, search) is tested
  once in the HEART library, and RunHeartTckTest runs the conformance
  features against this server.

  Background:
    Given game 1, the Eagles at home to the Marauders

  Scenario: Before tip-off, only the tip-off is offered, and the rest say why not
    When the scorer opens game 1
    Then the game offers exactly "basketball:tip-off"
    And "basketball:score-basket" is absent because "The game has not started; tip off first."

  Scenario: The tip-off starts period 1 with the team that won it in possession
    When the scorer records "tip-off" on game 1 with {"possession": "away"}
    Then game 1 is "LIVE" in period 1 with the "away" team in possession
    And the game offers exactly "basketball:score-basket, basketball:free-throw, basketball:record-foul, basketball:turnover, basketball:call-timeout, basketball:end-period, basketball:correct-score"

  Scenario Outline: A <points>-point basket counts for the team and the player, and possession changes
    Given game 1 is live in period 1 with the "home" team in possession
    When the scorer records "score-basket" on game 1 with {"team": "home", "playerNumber": 23, "points": <points>}
    Then the score is home <points>, away 0
    And home player 23 has <points> points
    And the "away" team has possession

    Examples:
      | points |
      | 2      |
      | 3      |

  Scenario Outline: A free throw counts 1 when made, 0 when missed: <made>
    Given game 1 is live in period 1 with the "home" team in possession
    When the scorer records "free-throw" on game 1 with {"team": "away", "playerNumber": 7, "made": <made>}
    Then the score is home 0, away <score>

    Examples:
      | made  | score |
      | true  | 1     |
      | false | 0     |

  Scenario: A basket by a player not on the team is refused, and counts for nothing
    The form's schema allows any shirt number; only the game knows its roster.
    Given game 1 is live in period 1 with the "home" team in possession
    When the scorer records "score-basket" on game 1 with {"team": "home", "playerNumber": 99, "points": 2}
    Then the game refuses it by a game rule at "/playerNumber"
    And the score is home 0, away 0

  Scenario: A player's fifth personal foul fouls them out, and they can no longer score
    Given game 1 is live in period 1 with the "home" team in possession
    And home player 23 has 4 personal fouls
    When the scorer records "record-foul" on game 1 with {"team": "home", "playerNumber": 23, "kind": "personal"}
    Then home player 23 has fouled out
    When the scorer records "score-basket" on game 1 with {"team": "home", "playerNumber": 23, "points": 2}
    Then the game refuses it by a game rule at "/playerNumber"

  Scenario: The other team is in the bonus once a team has 7 fouls in the half
    Given game 1 is live in period 1 with the "home" team in possession
    And the "home" team committed 6 fouls this half
    When the scorer records "record-foul" on game 1 with {"team": "home", "playerNumber": 4, "kind": "personal"}
    Then the "away" team is in the bonus

  Scenario: A timeout stops play: only resuming is offered
    Given game 1 is live in period 1 with the "home" team in possession
    When the scorer records "call-timeout" on game 1 with {"team": "away"}
    Then game 1 is "TIMEOUT"
    And the game offers exactly "basketball:resume-play, basketball:correct-score"
    And "basketball:score-basket" is absent because "Play is stopped for a timeout; resume play first."
    When the scorer records "resume-play" on game 1 with {}
    Then game 1 is "LIVE" in period 1 with the "home" team in possession

  Scenario: Between periods only the next period can start, and team fouls reset at half time
    Given game 1 is live in period 2 with the "home" team in possession
    And the "home" team committed 5 fouls this half
    When the scorer records "end-period" on game 1 with {}
    Then game 1 is "BREAK"
    And the game offers exactly "basketball:start-period"
    When the scorer records "start-period" on game 1 with {}
    Then game 1 is "LIVE" in period 3 with the "home" team in possession
    And the "home" team has 0 fouls this half

  Scenario: After the fourth period only the final whistle is offered, and then nothing
    Given game 1 is live in period 4 with the "home" team in possession
    When the scorer records "end-period" on game 1 with {}
    Then game 1 is "FULL_TIME"
    And the game offers exactly "basketball:final-whistle"
    When the scorer records "final-whistle" on game 1 with {}
    Then game 1 is "FINAL"
    And the game offers nothing

  Scenario: A basket recorded by mistake is taken back, once
    Given game 1 is live in period 1 with the "home" team in possession
    And the scorer recorded "score-basket" on game 1 with {"team": "home", "playerNumber": 11, "points": 3}
    When the scorer takes back game 1's play number 2
    Then the score is home 0, away 0
    And home player 11 has 0 points
    When the scorer takes back game 1's play number 2
    Then the game refuses it by a game rule at "/sequenceNo"

  Scenario: Only a scoring play can be taken back
    Given game 1 is live in period 1 with the "home" team in possession
    When the scorer takes back game 1's play number 1
    Then the game refuses it by a game rule at "/sequenceNo"
