Feature: The gateway never steps back to a state it has seen replaced
  GitHub answers some reads from a copy that hasn't caught up with the last
  write yet: just after a commit, one read of a branch shows its new head,
  the next its old one, the next its new one again. Compared just before an
  act, the old and the new look like someone's change, and the act is
  refused as stale for no reason.

  So the gateway keeps, for each caller and resource, the ETags it has seen
  replaced: by a later read, or by the caller's own write. A read answered
  with one of those came from a copy that is behind. The gateway reads
  again, a few times, a moment apart, before it takes the answer; a
  resource that really did change back is taken after those few tries. A
  client is never given a state older than one it was given before.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: The read after the caller's own write is answered from a copy behind it
    Given GitHub answers the read after the next write to pull request 482 from a copy behind it
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:edit" with {"title":"Fix the login"} and the ETag it holds
    Then the GitHub gateway answers 200
    And the pull request's title is now "Fix the login"

  Scenario: A read of a state seen replaced by a later one is read again before an act
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And after the gateway's next read of pull request 482, its title changes upstream to "Fix login"
    And a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And GitHub answers the next read of pull request 482 from a copy from before that change
    And the client sends "github:close" with {} and the ETag it holds
    Then the GitHub gateway answers 200
