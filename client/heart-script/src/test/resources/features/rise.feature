Feature: A script asks with a reply, and waits on its answer (RISE)
  A party that does long work answers later, on a reply the caller made before asking. ARTIE runs in
  a host, a service of its own (hosted.feature): the host keeps the replies, and ARTIE never serves.
  `ask` has the host open a reply, then sends the act with the reply in its envelope: `hr:reply-to`,
  the reply, and `hr:keys`, where the host's keys are, so the reply can check who answers. The host
  is told what the request brought into being, which answers it. `once-answered` waits until the
  host says the reply changed, reads it, and goes on by its state: completed, its steps; failed, its
  `failed` steps; no answer in time, its `otherwise` steps, with why. The reply is kept by name, so
  later steps read its result as `${watch.result…}`.

  Background:
    Given a service holds these resources
      | address    | title         | state    |
      | /          | Offer watch   |          |
      | /watches/1 | Watch 1       | watching |
      | /replies/1 | ARTIE's reply | awaiting |
    And these forms
      | at | relation      | leads to   | outcome      |
      | /  | watcher:watch | /watches/1 | new resource |
    And the host keeps replies, opening "/replies/1" with its keys at "/keys"

  Scenario: The act carries the reply, and the steps run once the reply is completed
    Given "/replies/1" becomes "completed" after 2 more reads
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - ask: watcher:watch
          fields: {resource: /pulls/482}
          reply: watch
        - once-answered:
            reply: watch
            steps:
              - note: the watch said ${watch.state}
      """
    Then the script finished
    And the script noted "the watch said completed"
    And the client was asked, in order
      | call   | what                                                                                                      |
      | enter  | scripted://service/                                                                                       |
      | act    | watcher:watch {"resource":"/pulls/482","_links":{"hr:reply-to":{"href":"/replies/1"},"hr:keys":{"href":"/keys"}}} |
      | return | /replies/1                                                                                                |
      | return | /replies/1                                                                                                |
      | return | /watches/1                                                                                                |
    And the host was told, in order
      | told   | reply      | what       |
      | opened | /replies/1 |            |
      | asked  | /replies/1 | /watches/1 |
      | waited | /replies/1 |            |

  Scenario Outline: The reply's state chooses the steps that run
    Given "/replies/1" becomes "<state>" after 1 more reads
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - ask: watcher:watch
          fields: {resource: /pulls/482}
          reply: watch
        - once-answered:
            reply: watch
            give-up: 1m
            steps:
              - note: completed
            failed:
              - note: failed
            otherwise:
              - note: no answer, ${why}
      """
    Then the script finished
    And the script noted "<noted>"

    Examples:
      | state     | noted                               |
      | completed | completed                           |
      | failed    | failed                              |
      | awaiting  | no answer, no answer in 1m          |

  Scenario: Asking with no replies to keep stops before anything is sent
    Given the host keeps no replies
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - ask: watcher:watch
          fields: {resource: /pulls/482}
          reply: watch
      """
    Then the script stopped at step 1, "watcher:watch": "this run keeps no replies: a host gives it them"
