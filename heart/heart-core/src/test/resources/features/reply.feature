Feature: Ana's reply takes the library's answer once, and a repeat of that answer is replayed
  RISE's reply resource (spec/ideas/rise-deep-dive.md). While it awaits, it takes progress and one answer: a result or
  a failure. A repeat of the result it has is replayed (SPEC §6.2, decision 8). Any other act on an answered reply is
  refused as not offered, because the reply takes no more. Every act is a PATCH of the reply itself: a JSON Merge
  Patch, or a problem document for a failure, whose state names the act.

  Scenario Outline: A reply awaiting takes each of the library's acts, and moves to the state it names
    Given reply r-7f3 is awaiting
    When a client acts on reply r-7f3 with "<act>", validator current, and body <body>
    Then the reply act was performed
    And reply r-7f3 is now <state>

    Examples:
      | act       | body                                                                                            | state     |
      | progress  | {"state": "awaiting", "percent": 40, "note": "one ahead in the hold queue"}                      | awaiting  |
      | completed | {"state": "completed", "result": {"loan": 3310}}                                                  | completed |
      | failed    | {"state": "failed", "type": "https://library.example/problems/no-hold", "title": "No hold record"} | failed    |

  Scenario Outline: A PATCH at the reply is the act its state names: <state>
    Given reply r-7f3 is awaiting
    When a PATCH of <type> with the state "<state>" reaches reply r-7f3
    Then the reply takes it as <taken>

    Examples:
      | type                         | state     | taken                              |
      | application/merge-patch+json | awaiting  | the act "progress"                 |
      | application/merge-patch+json | completed | the act "completed"                |
      | application/problem+json     | failed    | the act "failed"                   |
      | application/merge-patch+json | lost      | invalid at "/state"                |
      | application/merge-patch+json | failed    | needing the type "application/problem+json" |

  Scenario Outline: Progress is merged into the reply: a note left out stays, a null note is cleared
    Given reply r-7f3 is awaiting
    And reply r-7f3 took the progress {"state": "awaiting", "percent": 40, "note": "one ahead in the hold queue"}
    When a client acts on reply r-7f3 with "progress", validator current, and body <body>
    Then the reply act was performed
    And reply r-7f3 shows the percent <percent> and the note <note>

    Examples:
      | body                                               | percent | note                          |
      | {"state": "awaiting", "percent": 50}               | 50      | "one ahead in the hold queue" |
      | {"state": "awaiting", "percent": 50, "note": null} | 50      | null                          |

  Scenario: A completed reply keeps no stale progress
    Given reply r-7f3 is awaiting
    And reply r-7f3 took the progress {"state": "awaiting", "percent": 40, "note": "one ahead in the hold queue"}
    When a client acts on reply r-7f3 with "completed", validator current, and body {"state": "completed", "result": {"loan": 3310}}
    Then reply r-7f3 shows the percent 100 and the note null

  Scenario: A failed reply keeps its problem, embedded, so its owner can see why
    Given reply r-7f3 is awaiting
    When a client acts on reply r-7f3 with "failed", validator current, and body {"state": "failed", "type": "https://library.example/problems/no-hold", "title": "No hold record"}
    Then reply r-7f3 embeds the problem {"type": "https://library.example/problems/no-hold", "title": "No hold record"}

  Scenario: The reply keeps the request its first answer names, in every state after
    Given reply r-7f3 is awaiting
    And reply r-7f3 took the progress {"state": "awaiting", "percent": 0, "_links": {"hr:in-reply-to": {"href": "https://riverside-library.example/watches/1"}}}
    When a client acts on reply r-7f3 with "completed", validator current, and body {"state": "completed", "result": {"loan": 3310}}
    Then the reply act was performed
    And reply r-7f3 links "hr:in-reply-to" to "https://riverside-library.example/watches/1"

  Scenario: An answer that names another request than the one the reply keeps is refused, and changes nothing
    Given reply r-7f3 is awaiting
    And reply r-7f3 took the progress {"state": "awaiting", "percent": 0, "_links": {"hr:in-reply-to": {"href": "https://riverside-library.example/watches/1"}}}
    When a client acts on reply r-7f3 with "progress", validator current, and body {"state": "awaiting", "percent": 50, "_links": {"hr:in-reply-to": {"href": "https://riverside-library.example/watches/2"}}}
    Then the reply act is refused as a conflict over "hr:in-reply-to"
    And reply r-7f3 shows the percent 0 and the note null
    And reply r-7f3 links "hr:in-reply-to" to "https://riverside-library.example/watches/1"

  Scenario: A reply whose owner names the result's shape refuses a result without its fields
    Given replies take a result shaped as {"type": "object", "required": ["loan", "renewableFrom"], "properties": {"loan": {"type": "integer"}, "renewableFrom": {"type": "string", "format": "date"}}}
    And reply r-7f3 is awaiting
    When a client acts on reply r-7f3 with "completed", validator current, and body {"state": "completed", "result": {"loan": 3310}}
    Then the reply act is refused as invalid at "/result/renewableFrom"

  Scenario: The reply's forms target the reply itself, by PATCH, and say what each takes
    Given reply r-7f3 is awaiting
    Then reply r-7f3 offers these forms
      | form         | target         | method | content type                 |
      | hr:progress  | /replies/r-7f3 | PATCH  | application/merge-patch+json |
      | hr:completed | /replies/r-7f3 | PATCH  | application/merge-patch+json |
      | hr:failed    | /replies/r-7f3 | PATCH  | application/problem+json     |

  Scenario: The reply names its relations by the one hr vocabulary, at its absolute address
    Given reply r-7f3 is awaiting
    Then reply r-7f3 declares exactly one CURIE "hr", at "https://w3id.org/heart-rise/rels/{rel}"

  Scenario: A refusal names the relation it refused by its absolute address
    Given reply r-7f3 is awaiting
    And reply r-7f3 was completed with the body {"state": "completed", "result": {"loan": 3310}}
    When a client acts on reply r-7f3 with "failed", validator current, and body {"state": "failed", "type": "https://library.example/problems/no-hold", "title": "No hold record"}
    Then the reply act is refused as not offered at "https://w3id.org/heart-rise/rels/failed"

  Scenario: A repeat of the result the reply has is replayed, not refused
    Given reply r-7f3 is awaiting
    And reply r-7f3 was completed with the body {"state": "completed", "result": {"loan": 3310, "renewableFrom": "2026-11-18"}}
    When a client acts on reply r-7f3 with "completed", validator current, and body {"state": "completed", "result": {"loan": 3310, "renewableFrom": "2026-11-18"}}
    Then the reply act was performed

  Scenario Outline: An answered reply takes no more acts, and a different result is refused as not offered
    Given reply r-7f3 is awaiting
    And reply r-7f3 was completed with the body {"state": "completed", "result": {"loan": 3310}}
    When a client acts on reply r-7f3 with "<act>", validator current, and body <body>
    Then the reply act is refused as not offered "hr:<act>"

    Examples:
      | act       | body                                                                                            |
      | progress  | {"state": "awaiting", "percent": 50}                                                              |
      | failed    | {"state": "failed", "type": "https://library.example/problems/no-hold", "title": "No hold record"} |
      | completed | {"state": "completed", "result": {"loan": 4400}}                                                  |
