Feature: The gateway calls GitHub as each caller, and revalidates what it read
  spec/PLAN-GITHUB-PULLS.md, phase 7. What a caller may see and do on
  GitHub is GitHub's to say, by the caller's own token. So the gateway
  holds no GitHub credential of its own: it forwards each caller's, and
  answers as GitHub does, 401 for a token GitHub rejects. GitHub limits
  how often each token may call, but a conditional request answered 304
  costs nothing, so the gateway keeps what each caller last read, with its
  ETag, and asks again with If-None-Match. One caller's copy never answers
  another: their permissions differ.

  Background:
    Given the GitHub gateway reads GitHub's description with the connector "github"
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows the callers "ada" and "bob"
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: GitHub is asked with the caller's own token
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And GitHub was asked for "/repos/acme/rockets/pulls/482" with "Authorization" "Bearer ada-token"

  Scenario: Each caller is offered what GitHub lets them do
    Given "bob" can't push to "acme/rockets"
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers "github:close, github:merge"
    When "bob" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers nothing

  Scenario: A token GitHub rejects is answered 401
    When "mallory" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 401

  Scenario: A request with no credential is refused before GitHub is asked
    When a client with no credential gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 401
    And GitHub was never asked for "/repos/acme/rockets/pulls/482"

  Scenario: A second read revalidates with GitHub, which answers 304
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway answers 200
    And GitHub answered the last request for "/repos/acme/rockets/pulls/482" with 304
    And the GitHub gateway offers "github:close, github:merge"

  Scenario: One caller's copy never answers another
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And "bob" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then GitHub's last request for "/repos/acme/rockets/pulls/482" carried no "If-None-Match"
