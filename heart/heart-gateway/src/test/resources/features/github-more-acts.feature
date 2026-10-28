Feature: Editing a pull request, requesting reviews, and updating its branch
  spec/PLAN-GITHUB-PULLS.md. Three more of GitHub's pull request operations,
  each declared in the connector's resource file, none in code:
  - edit: pulls/update, with only the title and body a client fills;
  - request-review: pulls/request-reviewers, its form asking for reviewers
    or teams, at least one, as GitHub's own schema says;
  - update-branch: pulls/update-branch, its expected_head_sha bound to the
    head the client's view had, as merge's sha is;
  - label: issues/add-labels, at the issue the pull request also is, as
    comment is: a label is a fact anyone reading it sees, and a client may
    mark what it has done with one.
  GitHub's own refusals stay the final word, in GitHub's own words.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
      | bob    |
    And pull request 482 of "acme/rockets" is "open" upstream, titled "Fix the login bug"

  Scenario: An open pull request offers every act GitHub allows on it
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act                   |
      | github:close          |
      | github:merge          |
      | github:edit           |
      | github:request-review |
      | github:update-branch  |
      | github:comment        |
      | github:label          |

  Scenario: Edit's form asks for the title and body, nothing else
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the form "github:edit" has the fields
      | field |
      | title |
      | body  |

  Scenario: Edit is pulls/update with the client's title and body, and no state
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:edit" with {"title":"Fix the login bug for good","body":"And the logout one."} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PATCH "/repos/acme/rockets/pulls/482" with {"title":"Fix the login bug for good","body":"And the logout one."}
    And the pull request's title is now "Fix the login bug for good"

  Scenario: Requesting a review is pulls/request-reviewers, and the reviewer is then asked
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:request-review" with {"reviewers":["bob"]} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to POST "/repos/acme/rockets/pulls/482/requested_reviewers" with {"reviewers":["bob"]}
    And the pull request now asks "bob" for a review

  Scenario: A review request that names nobody is refused by the form, before GitHub is asked
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:request-review" with {} and the ETag it holds
    Then the GitHub gateway answers 422
    And GitHub was never asked to POST "/repos/acme/rockets/pulls/482/requested_reviewers"

  Scenario: A review GitHub won't request is refused in GitHub's own words
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:request-review" with {"reviewers":["mallory"]} and the ETag it holds
    Then the GitHub gateway answers 422
    And the problem names the violation "Reviews may only be requested from collaborators. One or more of the users or teams you specified is not a collaborator of the acme/rockets repository."

  Scenario: Updating the branch sends GitHub the head the client's view had
    Given pull request 482's head is at "a1b2c3"
    And pull request 482's branch is behind its base
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:update-branch" with {} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to PUT "/repos/acme/rockets/pulls/482/update-branch" with {"expected_head_sha":"a1b2c3"}

  Scenario: Labelling a pull request is issues/add-labels, at its issue's number, and the label is then read
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:label" with {"labels":["watch-told"]} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub was asked to POST "/repos/acme/rockets/issues/482/labels" with {"labels":["watch-told"]}
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the issue's labels are now
      | label      |
      | watch-told |

  Scenario: Updating a branch that is up to date is refused in GitHub's own words
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    And the client sends "github:update-branch" with {} and the ETag it holds
    Then the GitHub gateway answers 409
    And the problem's detail is "There are no new commits on the base branch."

  Scenario: A closed pull request can be edited, reopened and commented on, and says why not the rest
    Given pull request 482 is closed upstream
    When "ada" gets "/repos/acme/rockets/pulls/482" through the GitHub gateway
    Then the GitHub gateway offers
      | act            |
      | github:reopen  |
      | github:edit    |
      | github:comment |
      | github:label   |
    And the GitHub gateway says these acts are absent, and why
      | act                   | reason                                                               |
      | github:request-review | A closed pull request can't have reviews requested; reopen it first. |
      | github:update-branch  | A closed pull request's branch isn't updated; reopen it first.       |
