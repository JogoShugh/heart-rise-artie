Feature: The release desk: services that call others through ARTIE, and answer through it
  ARTIE never serves. The parties do, and each runs ARTIE inside it to call the others: the release
  desk, to release a pull request by its kept script; the offer watch and the CI wrapper, to answer
  the replies they were asked on. The desk keeps the replies its run asks with, and wakes the run
  when one is answered; the run then reads the reply, as any resource. Each party signs with its own
  key, so a reply can tell who answered it.

  Scenario: A party answers a reply through ARTIE, signed with its own key
    Given the desk keeps a reply, asked for at the watch "http://localhost:8099/watches/1"
    When the watch answers the reply through ARTIE with "completed":
      """yaml
      state: completed
      result: {resource: "http://localhost:8098/repos/acme/web/pulls/482", offers: "github:merge"}
      _links: {"hr:in-reply-to": {href: "http://localhost:8099/watches/1"}}
      """
    Then the reply reads:
      | member                  | value                                          |
      | /state                  | completed                                      |
      | /result/offers          | github:merge                                   |
      | /_links/hr:in-reply-to/href | http://localhost:8099/watches/1            |
    And the answer was signed by the key "watcher-2026-11"

  Scenario: A run waiting on its reply wakes when the reply is answered
    Given the desk keeps a reply, asked for at the watch "http://localhost:8099/watches/1"
    And a run waits on the reply for up to 10 seconds
    When the watch answers the reply through ARTIE with "completed":
      """yaml
      state: completed
      result: {resource: "http://localhost:8098/repos/acme/web/pulls/482"}
      _links: {"hr:in-reply-to": {href: "http://localhost:8099/watches/1"}}
      """
    Then the run woke within 2 seconds, told the reply changed

  Scenario Outline: The desk's ARTIE presents at each party that party's own test credential, and nowhere else
    When the desk's ARTIE is asked for its credential at "<origin>"
    Then it presents "<credential>"

    Examples:
      | origin                   | credential                |
      | http://192.168.1.18:8098 | Bearer github-token       |
      | http://192.168.1.18:8094 | Bearer gitlab-token       |
      | http://192.168.1.18:8093 | Bearer github-token       |
      | http://192.168.1.18:8099 | Bearer watcher-token      |
      | http://192.168.1.18:8160 | Bearer artie-reply-token  |
      | http://192.168.1.18:8170 | Bearer ci-token           |
      | http://192.168.1.18:8180 | Basic 21000451:1234       |
      | http://192.168.1.18:8190 | Bearer env-token          |
      | http://192.168.1.18:9999 | none                      |

  Scenario Outline: The desk's ARTIE reads every party at the rung the release is run at
    When the release is run with RELEASE_RUNG "<given>"
    Then the desk's ARTIE reads at rung "<rung>"

    Examples:
      | given | rung |
      |       | L0   |
      | L0    | L0   |
      | L2    | L2   |

  Scenario: A capture in the desk's process waits until the parties serve before its first request
    Given nothing serves yet at "http://localhost:8399/", and something will in 1 second
    When the capture waits for "http://localhost:8399/" for up to 10 seconds
    Then it went on once that answered

  Scenario: The desk releases a pull request by running its kept script with ARTIE
    Given the desk's release script is:
      """yaml
      heart: 1
      kind: kept
      inputs: {pull: {}, scm: {default: "http://localhost:8098/"}, owner: {default: acme}, repo: {default: web}, book: {default: Matilda}, problem: {default: damaged}}
      start: http://localhost:8160/
      steps:
        - note: "releasing pull request ${pull} of ${owner}/${repo} at ${scm}, smoke testing ${book} with ${problem}"
      """
    When a person asks the desk to release pull request 482, with:
      | scm     | http://localhost:8094/ |
      | owner   | JogoShugh              |
      | repo    | heart-github-test      |
      | book    | The Hobbit             |
      | problem | wrong-book             |
    Then the desk answered 201, at "/releases/1"
    And "/releases/1" reads, once the run is over:
      | member          | value                                                         |
      | /pull           | 482                                                           |
      | /inputs/scm     | http://localhost:8094/                                        |
      | /inputs/owner   | JogoShugh                                                     |
      | /inputs/repo    | heart-github-test                                             |
      | /inputs/book    | The Hobbit                                                    |
      | /inputs/problem | wrong-book                                                    |
      | /state          | ended                                                         |
      | /notes/0        | releasing pull request 482 of JogoShugh/heart-github-test at http://localhost:8094/, smoke testing The Hobbit with wrong-book |
