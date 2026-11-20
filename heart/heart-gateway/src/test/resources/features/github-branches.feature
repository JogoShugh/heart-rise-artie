Feature: Branches, commits and new pull requests, from acts a branch offers
  spec/ideas/github-branches-and-new-pulls.md, decided 2026-10-18. A branch is a
  resource of its own, at GitHub's /repos/{owner}/{repo}/branches/{branch},
  linked from its repository. It offers three acts, each a projection of one of
  GitHub's operations: branch from here (git/create-ref), commit a file
  (repos/create-or-update-file-contents) and open a pull request
  (pulls/create). Three mechanisms make them read as acts, not as GitHub's
  plumbing: an input the body is a template of (the client names the branch;
  the gateway sends refs/heads/<name>), a field the gateway encodes (the
  client writes the file's text; the gateway sends it base64), and an address
  template for a new resource whose answer isn't that resource (a new branch's
  Location). The branch's own name and head are bound from its state.

  Background:
    Given the GitHub gateway reads GitHub's description with the shipped connector
    And the GitHub gateway calls GitHub as each caller
    And GitHub knows these callers
      | caller |
      | ada    |
    And "acme/rockets" has these branches upstream
      | name | sha                                      | protected |
      | main | 1111111111111111111111111111111111111111 | true      |
      | fix  | 2222222222222222222222222222222222222222 | false     |

  Scenario: A branch is read at GitHub's own path, linked from its repository, and offers its acts
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/branches/fix"
    And the answer links these, by type
      | relation  | href                                      | type      | templated |
      | alternate | https://github.com/acme/rockets/tree/fix  | text/html |           |
    And the GitHub gateway offers
      | act                      |
      | github:branch-from-here  |
      | github:commit-file       |
      | github:open-pull-request |
    When "ada" gets "/repos/acme/rockets" through the GitHub gateway
    Then the answer links "github:repos-list-branches" to "/repos/acme/rockets/branches"

  Scenario: A branch's head commit is embedded, and its parents are links, one per parent
    GitHub lists a commit's parents as objects, each a sha and two addresses.
    The addresses become links; the sha stays.
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the answer embeds "github:commit" with its self at "https://api.github.com/repos/acme/rockets/commits/2222222222222222222222222222222222222222" and these links
      | relation            | href                                                                                       | type                        |
      | alternate           | https://github.com/acme/rockets/commit/2222222222222222222222222222222222222222            | text/html                   |
      | github:parents      | https://api.github.com/repos/acme/rockets/commits/9999999999999999999999999999999999999999 | application/vnd.github+json |
      | github:parents-html | https://github.com/acme/rockets/commit/9999999999999999999999999999999999999999            | text/html                   |
    And the embedded "github:commit" keeps only these members of each of its "parents"
      | member |
      | sha    |

  Scenario: The bookmark finds a repository: by its owner and name, or among a user's
    So a task can begin at the bookmark and reach a repository's branches, as
    issues and pull requests are found.
    When "ada" gets "/" through the GitHub gateway
    Then the index form "github:repos-get" targets "/repos/{owner}/{repo}"
    And the index form "github:repos-list-for-user" targets "/users/{username}/repos{?type,sort,direction}"
    When "ada" gets "/users/acme/repos" through the GitHub gateway
    Then the answer lists
      | href                | title        |
      | /repos/acme/rockets | acme/rockets |

  Scenario: A repository's branches are listed, each linked by its name
    GitHub lists branches without their own addresses: the list's path names
    the repository, and each item names its branch.
    When "ada" gets "/repos/acme/rockets/branches" through the GitHub gateway
    Then the GitHub gateway answers 200
    And the answer lists
      | href                              | title |
      | /repos/acme/rockets/branches/main | main  |
      | /repos/acme/rockets/branches/fix  | fix   |

  Scenario Outline: A branch whose name holds a slash is linked and read at its name encoded, <way>
    Live, 2026-10-30: a client followed the link to "deps/left-pad-1.0.1",
    "/branches/deps%2Fleft-pad-1.0.1", and the gateway answered 400 before
    any of its own code ran: Jetty refuses an encoded slash in a path by
    default. GitHub answers both forms. A branch name is one path segment, so
    its slash is encoded in the link, and the gateway takes it so.
    Given "acme/rockets" has these branches upstream
      | name          | sha                                      | protected |
      | deps/left-pad | 3333333333333333333333333333333333333333 | false     |
    When "ada" gets "/repos/acme/rockets/branches" through the GitHub gateway
    Then the answer lists
      | href                                       | title         |
      | /repos/acme/rockets/branches/main          | main          |
      | /repos/acme/rockets/branches/fix           | fix           |
      | /repos/acme/rockets/branches/deps%2Fleft-pad | deps/left-pad |
    When "ada" gets "/repos/acme/rockets/branches/deps%2Fleft-pad" through <way>
    Then the GitHub gateway answers 200
    And the answer links "self" to "/repos/acme/rockets/branches/deps%2Fleft-pad"

    Examples:
      | way                                |
      | the GitHub gateway                 |
      | the GitHub gateway's own listener  |

  Scenario: The forms ask for what a person would say, not GitHub's plumbing
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the forms ask for these fields
      | act                      | field   |
      | github:branch-from-here  | name    |
      | github:commit-file       | path    |
      | github:commit-file       | message |
      | github:commit-file       | content |
      | github:open-pull-request | title   |
      | github:open-pull-request | body    |
      | github:open-pull-request | base    |
      | github:open-pull-request | draft   |

  Scenario: The connector's own words for a field replace GitHub's
    From the HTML mockup, 2026-10-30: GitHub describes commit-file's path
    only as "path parameter", and pulls/create's base in three sentences. A
    resource file may give a field its own title and description, which a
    person reads in the form, and which win over GitHub's and the name's.
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the forms title their fields
      | act                      | field | title       | description                                                             |
      | github:commit-file       | path  | File path   | Where the file goes, from the repository's root, such as docs/NOTES.md. |
      | github:open-pull-request | base  | Base branch | The branch to merge into, such as main.                                 |
      | github:open-pull-request | title | Title       | The title of the new pull request. Required unless `issue` is specified. |

  Scenario: Branching from here sends GitHub a ref, at this branch's head, and answers 201 naming the new branch
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:branch-from-here" with {"name":"fix-2"} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/branches/fix-2"
    And GitHub was asked to POST "/repos/acme/rockets/git/refs" with {"ref":"refs/heads/fix-2","sha":"2222222222222222222222222222222222222222"}

  Scenario: Committing a file sends its text base64-encoded, on this branch
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:commit-file" with {"path":"NOTES.md","message":"Refuse blank names","content":"Bed names may not be blank.\n"} and the ETag it holds
    Then the GitHub gateway answers 200
    And GitHub holds the file "NOTES.md" on "fix" of "acme/rockets", saying "Bed names may not be blank.\n", committed as "Refuse blank names"

  Scenario: The commit form describes the file's text as plain text, since the gateway encodes it
    Live, a reasoner read GitHub's own words for content, "The new file
    content, using Base64 encoding.", encoded the text itself, and the gateway
    encoded it again (2026-10-18): the file held base64. A field the gateway
    encodes is described as what the client sends.
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    Then the "github:commit-file" form's field "content" is described as "Plain text, as is: the gateway encodes it in base64 before sending it on."

  Scenario: Opening a pull request from this branch answers 201 naming the new pull request
    When "ada" gets "/repos/acme/rockets/branches/fix" through the GitHub gateway
    And the client sends "github:open-pull-request" with {"title":"Refuse blank names","base":"main"} and the ETag it holds
    Then the GitHub gateway answers 201
    And the answer's Location is "/repos/acme/rockets/pulls/1"
    And GitHub was asked to POST "/repos/acme/rockets/pulls" with {"title":"Refuse blank names","base":"main","head":"fix"}

  Scenario: A protected branch takes no commits, and says why
    When "ada" gets "/repos/acme/rockets/branches/main" through the GitHub gateway
    Then the GitHub gateway says these acts are absent, and why
      | act                | reason                                         |
      | github:commit-file | The branch is protected; commit to another one. |
