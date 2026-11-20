Feature: The Dev environment: a commit is promoted to it, citing the smoke run that passed
  The last step of the merge watch release. Dev is a HEART resource: the commit it serves, when it was promoted and
  the smoke run that was cited. It offers to promote a commit, conditional on its ETag, so a promotion that raced
  another is refused as stale; and its rule refuses a promotion whose smoke run did not pass, saying why.

  Background:
    Given Dev starts out serving commit "cba8780"

  Scenario: A commit whose smoke run passed is promoted, and Dev serves it, citing the run
    When the commit "9b1e2d4" is promoted to Dev, citing a smoke run where 5 of 5 scenarios passed
    Then Dev answers 200
    And Dev serves commit "9b1e2d4", citing that smoke run

  Scenario: A commit whose smoke run did not pass is refused by Dev's rule, and Dev serves what it did
    When the commit "a3ad713" is promoted to Dev, citing a smoke run where 4 of 5 scenarios passed
    Then Dev answers 422, a refusal of type "rule-refused"
    And Dev serves commit "cba8780"

  Scenario: A promotion on a version of Dev that has moved is refused as stale
    Given the commit "9b1e2d4" was promoted to Dev, citing a smoke run where 5 of 5 scenarios passed
    When the commit "0059dac" is promoted to Dev on the version read before, citing a smoke run where 5 of 5 scenarios passed
    Then Dev answers 412, a refusal of type "stale"
    And Dev serves commit "9b1e2d4", citing that smoke run
