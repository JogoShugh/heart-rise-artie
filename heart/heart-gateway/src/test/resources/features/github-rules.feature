Feature: What a GitHub pull request allows now, and why not the rest
  spec/PLAN-GITHUB-PULLS.md, phase 6. A pull request's state field says
  only open or closed. Whether it can be merged now depends on more: a
  draft can't be; a merged one can't be reopened; GitHub computes
  mergeability in the background, so it may not know yet; a branch behind
  its base must be updated first; branch protection may block it. And the
  caller may not be allowed to push to the repository at all, which GitHub
  says not on the pull request but on the repository.

  A reader's rules, in order, each a condition on several JSON Pointers,
  say which acts are absent and why. A condition may read a fact: another
  of GitHub's answers the resource file names, such as the repository's.
  GitHub's own refusals stay the final word; the rules only say early, and
  why.

  Background:
    Given the GitHub gateway reads GitHub's description with the connector "github"
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: A draft can't be merged
    Given pull request 482 is a draft
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |
    And the GitHub gateway says these acts are absent, and why
      | act          | reason                                                   |
      | github:merge | A draft can't be merged; mark it ready for review first. |

  Scenario: A merged pull request can't be reopened
    Given pull request 482 was merged
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act            |
      | github:edit    |
      | github:comment |
    And the GitHub gateway says these acts are absent, and why
      | act           | reason                                   |
      | github:reopen | A merged pull request can't be reopened. |

  Scenario: While GitHub is still checking mergeability, merge is absent, and offered once it knows
    Given GitHub hasn't yet worked out whether pull request 482 can be merged
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |
    And the GitHub gateway says these acts are absent, and why
      | act          | reason                                                                    |
      | github:merge | GitHub is still checking whether it can be merged; read it again shortly. |
    Given GitHub has worked out that pull request 482 can be merged cleanly
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:merge          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |

  Scenario Outline: A pull request GitHub says is <mergeable state> can't be merged now
    Given GitHub says pull request 482 is "<mergeable state>"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway says "github:merge" is absent because "<reason>"

    Examples:
      | mergeable state | reason                                                                     |
      | behind          | The branch is behind its base; update it first.                            |
      | dirty           | The branch has conflicts with its base; resolve them first.                |
      | blocked         | Branch protection blocks the merge: a required check or review is missing. |

  Scenario: A branch behind its base can't be merged, though GitHub says it is clean, until it is updated
    GitHub says behind only when the base's branch protection asks for
    up-to-date branches; without that, a branch behind its base is clean,
    and GitHub merges it. The gateway asks GitHub's compare whether the base
    has commits the branch lacks, and holds merge back until the branch is
    updated: a rule of its own that GitHub doesn't have.
    Given pull request 482's branch is behind its base
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |
    And the GitHub gateway says these acts are absent, and why
      | act          | reason                                          |
      | github:merge | The branch is behind its base; update it first. |
    When the client sends "github:update-branch" with {} and the ETag it holds
    And a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:merge          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |

  Scenario: A caller who can't push to the repository is offered nothing, from the repository's own answer
    GitHub's example pull request is locked, so a caller without push access
    may not comment on it either.
    Given the caller can't push to "acme/rockets"
    When a client gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers nothing
    And the GitHub gateway says these acts are absent, and why
      | act                   | reason                                                     |
      | github:close          | You can't push to this repository.                         |
      | github:merge          | You can't push to this repository.                         |
      | github:edit           | You can't push to this repository.                         |
      | github:request-review | You can't push to this repository.                         |
      | github:update-branch  | You can't push to this repository.                         |
      | github:comment        | The conversation is locked; only collaborators can comment. |
