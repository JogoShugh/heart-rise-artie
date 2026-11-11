Feature: Ana's reply takes the library's answer once, and a repeat of that answer is replayed
  RISE's reply resource (spec/ideas/rise-deep-dive.md). While it awaits, it takes progress and one answer: a result or
  a failure. A repeat of the result it has is replayed (SPEC §6.2, decision 8). Any other act on an answered reply is
  refused as not offered, because the reply takes no more.

  Scenario Outline: A reply awaiting takes each of the library's acts, and moves to the state it names
    Given reply r-7f3 is awaiting
    When a client acts on reply r-7f3 with "<act>", validator current, and body <body>
    Then the reply act was performed
    And reply r-7f3 is now <state>

    Examples:
      | act       | body                                                  | state     |
      | progress  | {"percent": 40, "note": "one ahead in the hold queue"} | awaiting  |
      | completed | {"result": {"loan": 3310}}                            | completed |
      | failed    | {"title": "No hold on loan 3310"}                     | failed    |

  Scenario: Progress without its note is refused as invalid at the note, so each progress is the whole of it
    Given reply r-7f3 is awaiting
    When a client acts on reply r-7f3 with "progress", validator current, and body {"percent": 40}
    Then the reply act is refused as invalid at "/note"
    And reply r-7f3 is now awaiting

  Scenario: A repeat of the result the reply has is replayed, not refused
    Given reply r-7f3 is awaiting
    And reply r-7f3 was completed with the body {"result": {"loan": 3310, "renewableFrom": "2026-11-18"}}
    When a client acts on reply r-7f3 with "completed", validator current, and body {"result": {"loan": 3310, "renewableFrom": "2026-11-18"}}
    Then the reply act was performed

  Scenario Outline: An answered reply takes no more acts, and a different result is refused as not offered
    Given reply r-7f3 is awaiting
    And reply r-7f3 was completed with the body {"result": {"loan": 3310}}
    When a client acts on reply r-7f3 with "<act>", validator current, and body <body>
    Then the reply act is refused as not offered "hr:<act>"

    Examples:
      | act       | body                              |
      | progress  | {"percent": 50}                   |
      | failed    | {"title": "No hold on loan 3310"} |
      | completed | {"result": {"loan": 4400}}        |
