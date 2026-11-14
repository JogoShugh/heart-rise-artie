Feature: The CI wrapper: a real Jenkins job served as a HEART service that answers on RISE replies
  The merge watch release builds the merged commit on a real Jenkins. The wrapper stands in front of it as a HEART
  service: its entry offers to build a commit, asking to be told how it goes, as a request to be told does (RISE).
  The request names the caller's reply and the keys it signs with; the wrapper starts the Jenkins job with the commit
  and the build's own number, and answers that build at its own address. Jenkins posts each start and finish to the
  wrapper (its Notification plugin), with a shared secret; the wrapper turns each into a signed answer on the
  caller's reply, naming the build it answers. A build that succeeded links what it made: the deployment it started,
  the smoke script it archived and its log, by the wrapper's own `ci` relations.

  Background:
    Given a Jenkins job app, behind the wrapper
    And the caller's reply r-91 is awaiting

  Scenario: Asked to build a commit, the wrapper starts the job with it and answers the build at its own address
    When the caller asks the wrapper to build commit "9b1e2d4", with reply r-91, signed
    Then the wrapper answers 201, the build at "/builds/1"
    And Jenkins was asked to build job app with these parameters
      | name          | value   |
      | SHA           | 9b1e2d4 |
      | RELEASE_BUILD | 1       |
    And build 1 is "queued", and links these
      | relation    | href                                  |
      | hr:reply-to | http://localhost:8160/replies/r-91    |
      | hr:keys     | /keys                                 |
      | ci:log      | /builds/1/log                         |

  Scenario: A request to build that its caller did not sign is refused, and starts nothing
    When the caller asks the wrapper to build commit "9b1e2d4", with reply r-91, unsigned
    Then the wrapper answers 401
    And Jenkins was asked to build nothing

  Scenario Outline: Jenkins says the build <phase> <status>: the build is <standing>, and the reply is told, signed
    Given the caller has asked to build commit "9b1e2d4", with reply r-91
    When Jenkins notifies the wrapper that build 1 is <phase> <status>, with the secret
    Then the wrapper answers 200
    And build 1 is "<standing>"
    And reply r-91 is now <reply>, by an answer signed by the wrapper, naming the build it answers

    Examples:
      | phase     | status  | standing  | reply     |
      | STARTED   |         | running   | awaiting  |
      | FINALIZED | SUCCESS | succeeded | completed |
      | FINALIZED | FAILURE | failed    | failed    |

  Scenario: A build that succeeded links the deployment it started, the smoke script it archived and its log
    Given the caller has asked to build commit "9b1e2d4", with reply r-91
    When Jenkins notifies the wrapper that build 1 is FINALIZED SUCCESS, with the secret
    Then build 1 links these
      | relation      | href                                         |
      | ci:deployment | http://localhost:8180/                        |
      | ci:smoke-test | /builds/1/artifacts/smoke.heart.yaml          |
      | ci:log        | /builds/1/log                                 |
    And reply r-91's result links the same three

  Scenario: A notification without the secret is refused, and changes nothing
    Given the caller has asked to build commit "9b1e2d4", with reply r-91
    When Jenkins notifies the wrapper that build 1 is FINALIZED SUCCESS, with no secret
    Then the wrapper answers 403
    And build 1 is "queued"
    And reply r-91 is now awaiting, unanswered

  Scenario: The build's log and the files it archived are read through the wrapper, as Jenkins keeps them
    Given the caller has asked to build commit "9b1e2d4", with reply r-91
    And Jenkins notifies the wrapper that build 1 is FINALIZED SUCCESS, with the secret
    When the caller reads "/builds/1/artifacts/smoke.heart.yaml"
    Then the wrapper answers 200, with what Jenkins archived as "smoke.heart.yaml"
    When the caller reads "/builds/1/log"
    Then the wrapper answers 200, with Jenkins' console text
