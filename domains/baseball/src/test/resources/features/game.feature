Feature: Scoring an 8U coach-pitch baseball game
  A game is one resource, /games/{id}. Its state says the inning and which
  half, who is batting and who is up, the outs, the pitches to this batter,
  which bases have runners, the score by inning, and each player's hits and
  runs. Its forms say what the scorer may record now, and nothing else.

  League rules for 8U coach pitch: six innings; three outs a half; a half
  ends at five runs, except in the last inning; a coach pitches, so there are
  no balls and no walks, and a batter gets six pitches, a foul on the last one
  giving one more; the whole team bats, in order, the order carrying on from
  inning to inning; on a hit every runner moves up as many bases as the
  batter. The umpire may end a game early, for time.

  Only the game's own rules are here. What every HEART server does is tested
  once in the HEART library, and RunHeartTckTest runs the conformance
  features against this server.

  Background:
    Given game 1, the Cubs at home to the Bears

  Scenario: Before the first pitch, only play ball is offered, and the rest say why not
    When the scorer opens game 1
    Then the game offers exactly
      | act                |
      | baseball:play-ball |
    And "baseball:hit" is absent because "The game has not started; play ball first."

  Scenario: Play ball starts the top of the first, the visitors batting, their first batter up
    When the scorer records "play-ball" on game 1 with {}
    Then game 1 is "PLAY" in the "top" of inning 1 with the "away" team batting
    And the batter up is away player 2
    And the game offers exactly
      | act                 |
      | baseball:pitch      |
      | baseball:hit        |
      | baseball:runner-out |
      | baseball:end-game   |

  Scenario Outline: A <hit> puts the batter on <where>, and the next batter comes up
    Given game 1 is in the top of inning 1, nobody out
    When the scorer records "hit" on game 1 with {"result": "<hit>"}
    Then the bases are <bases>
    And the score is home 0, away <runs>
    And away player 2 has 1 hit
    And the batter up is away player 5

    Examples:
      | hit      | where        | bases  | runs |
      | single   | first        | first  | 0    |
      | double   | second       | second | 0    |
      | triple   | third        | third  | 0    |
      | home-run | home, scored | empty  | 1    |

  Scenario Outline: On a <hit> with runners on <before>, every runner moves up as many bases as the batter
    Given game 1 is in the top of inning 1, nobody out
    And runners are on <before>
    When the scorer records "hit" on game 1 with {"result": "<hit>"}
    Then the bases are <after>
    And the score is home 0, away <runs>

    Examples:
      | before               | hit      | after                | runs |
      | first                | single   | first, second        | 0    |
      | first                | double   | second, third        | 0    |
      | second               | single   | first, third         | 0    |
      | third                | single   | first                | 1    |
      | first, third         | double   | second, third        | 1    |
      | first, second, third | single   | first, second, third | 1    |
      | first, second, third | home-run | empty                | 4    |

  Scenario: A batter fielded out is out, and the runners stay where they are
    Given game 1 is in the top of inning 1, nobody out
    And runners are on second
    When the scorer records "hit" on game 1 with {"result": "fielded-out"}
    Then there is 1 out
    And the bases are second
    And away player 5 has 0 hits

  Scenario Outline: The sixth pitch <result> leaves <outs> out and away player <batter> up
    A coach pitches: no balls, no walks. Six pitches, and a foul on the last
    one gives the batter one more.
    Given game 1 is in the top of inning 1, nobody out
    And the batter has missed 5 pitches
    When the scorer records "pitch" on game 1 with {"result": "<result>"}
    Then there is <outs> out
    And the batter up is away player <batter>
    And the batter has had <pitches> pitches

    Examples:
      | result | outs | batter | pitches |
      | missed | 1    | 5      | 0       |
      | foul   | 0    | 2      | 5       |

  Scenario: A runner out on the bases is out, and leaves the base
    Given game 1 is in the top of inning 1, nobody out
    And runners are on first, second
    When the scorer records "runner-out" on game 1 with {"base": "first"}
    Then there is 1 out
    And the bases are second

  Scenario: There is no runner to put out on an empty base
    Given game 1 is in the top of inning 1, nobody out
    When the scorer records "runner-out" on game 1 with {"base": "second"}
    Then the game refuses it by a game rule at "/base"

  Scenario: The third out ends the half: only the next half is offered, then the home team bats
    Given game 1 is in the top of inning 1, 2 out
    And runners are on first
    When the scorer records "hit" on game 1 with {"result": "fielded-out"}
    Then game 1 is "HALF_OVER" in the "top" of inning 1 with the "away" team batting
    And the game offers exactly
      | act                |
      | baseball:next-half |
      | baseball:end-game  |
    And "baseball:hit" is absent because "The half-inning is over; start the next half first."
    When the scorer records "next-half" on game 1 with {}
    Then game 1 is "PLAY" in the "bottom" of inning 1 with the "home" team batting
    And there is 0 out
    And the bases are empty
    And the batter up is home player 1

  Scenario Outline: In inning <inning>, the fifth run of the half <ends> it
    Given game 1 is in the top of inning <inning>, nobody out
    And the away team has hit 4 home runs this half
    And runners are on first, second, third
    When the scorer records "hit" on game 1 with {"result": "single"}
    Then the away team has 5 runs this half
    And game 1 is "<phase>" in the "top" of inning <inning> with the "away" team batting

    Examples:
      | inning | ends         | phase     |
      | 1      | ends         | HALF_OVER |
      | 6      | does not end | PLAY      |

  Scenario: The whole team bats in order, and the order carries on into the next inning
    Given game 1 is in the top of inning 1, nobody out
    And the away team's last batter is up
    When the scorer records "hit" on game 1 with {"result": "single"}
    Then the batter up is away player 2
    When the top and bottom of inning 1 are played out
    Then the batter up is away player 5

  Scenario: After the bottom of the sixth only the end of the game is offered, then nothing
    Given game 1 is in the bottom of inning 6, 2 out
    When the scorer records "hit" on game 1 with {"result": "fielded-out"}
    Then game 1 is "GAME_OVER" in the "bottom" of inning 6 with the "home" team batting
    And the game offers exactly
      | act               |
      | baseball:end-game |
    And "baseball:next-half" is absent because "Every inning is played; end the game."
    When the scorer records "end-game" on game 1 with {}
    Then game 1 is "FINAL"
    And the game offers nothing

  Scenario: The umpire ends a game early, for time
    Given game 1 is in the top of inning 4, nobody out
    When the scorer records "end-game" on game 1 with {}
    Then game 1 is "FINAL"
    And the game offers nothing

  Scenario: The score is kept by inning
    Given game 1 is in the top of inning 1, nobody out
    And runners are on third
    When the scorer records "hit" on game 1 with {"result": "home-run"}
    Then the away team's line reads 2 in inning 1
