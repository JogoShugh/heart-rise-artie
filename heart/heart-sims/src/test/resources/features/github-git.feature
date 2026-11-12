Feature: The small GitHub keeps its pull requests in a real git repository, and merges them there
  For the merge watch release, what Jenkins builds must be what was merged. So the small GitHub, behind the gateway,
  can keep a repository's pull requests as branches of a real git repository (pull request 482 is branch pr-482): a
  pull request's head is its branch's commit, and merging it makes a real commit on main, whose sha the merge answers
  with and the pull request names as its merge_commit_sha.

  Background:
    Given a git repository whose main has a README, and whose branch "pr-482" adds the line "Renewals: three weeks."
    And the small GitHub keeps acme/web's pull requests in that repository

  Scenario: A pull request's head is its branch's commit
    When the gateway reads pull request 482 of acme/web
    Then its head sha is the commit at the tip of "pr-482"

  Scenario: Merging by squashing makes a real commit on main, named as the merge's commit
    When pull request 482 of acme/web is merged by squashing, through the gateway
    Then the merge is done
    And main's latest commit is a new one, whose README has the line "Renewals: three weeks."
    And pull request 482 names main's latest commit as its merge_commit_sha
