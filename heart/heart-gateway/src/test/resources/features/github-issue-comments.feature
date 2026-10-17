Feature: An issue's comments, linked from it, and commenting
  spec/PLAN-GITHUB-ISSUES.md, step I6. GitHub keeps an issue's comments
  under it, at /repos/{owner}/{repo}/issues/{issue_number}/comments, and
  each comment at /repos/{owner}/{repo}/issues/comments/{comment_id}. The
  gateway links the comments from the issue, so a client follows a link
  and never builds the address. A comment is a resource of its own: its
  author, or anyone with push access, may edit it. Commenting is an act the
  issue offers; GitHub's answer is the new comment, named in Location. A
  locked conversation takes comments only from collaborators with push access.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And "acme/rockets" has these issues upstream
      | number | title                 | state | state_reason | locked | author |
      | 10     | Login fails on Safari | open  |              | false  | ada    |
    And issue 10 of "acme/rockets" has these comments upstream
      | id  | author | body                   |
      | 501 | ada    | It fails on Safari 17. |
      | 502 | bob    | Same on Safari 16.     |

  Scenario: An issue links its comments, and the link lists them
    When "ada" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    Then the answer links "github:issues-list-comments" to "/repos/acme/rockets/issues/10/comments"
    When "ada" gets "/repos/acme/rockets/issues/10/comments" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists
      | href                                    | title                  |
      | /repos/acme/rockets/issues/comments/501 | It fails on Safari 17. |
      | /repos/acme/rockets/issues/comments/502 | Same on Safari 16.     |

  Scenario: A comment is read at GitHub's own path, and its author may edit it
    When "ada" gets "/repos/acme/rockets/issues/comments/501" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/issues/comments/501"
    And the GitHub gateway offers
      | act         |
      | github:edit |

  Scenario: Who may edit a comment, and why not
    Then comment 501 of "acme/rockets" offers each caller
      | caller | access | edit | reason                                                             |
      | ada    | read   | yes  |                                                                    |
      | bob    | read   | no   | Only the comment's author or someone with push access can edit it. |
      | bob    | triage | no   | Only the comment's author or someone with push access can edit it. |
      | bob    | write  | yes  |                                                                    |

  Scenario: Editing a comment changes its body upstream
    When "ada" gets "/repos/acme/rockets/issues/comments/501" through the GitHub gateway
    And the client sends "github:edit" with {"body":"It fails on Safari 17.1."} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PATCH "/repos/acme/rockets/issues/comments/501" with {"body":"It fails on Safari 17.1."}

  Scenario: Commenting answers 201, naming the new comment in Location
    When "bob" gets "/repos/acme/rockets/issues/10" through the GitHub gateway
    And the client sends "github:comment" with {"body":"Fixed in 1.2."} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/issues/comments/503"
    And GitHub was asked to POST "/repos/acme/rockets/issues/10/comments" with {"body":"Fixed in 1.2."}

  Scenario: Who may comment on a locked conversation, and why not
    Given issue 10 of "acme/rockets" is locked upstream
    Then issue 10 of "acme/rockets" offers each caller
      | caller | access | comment |
      | ada    | read   | no      |
      | bob    | triage | no      |
      | bob    | write  | yes     |
    And issue 10 of "acme/rockets" says why, to each caller
      | caller | access | act     | reason                                                      |
      | ada    | read   | comment | The conversation is locked; only collaborators can comment. |
