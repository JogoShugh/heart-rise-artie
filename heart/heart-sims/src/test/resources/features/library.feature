Feature: Riverside Public Library: each loan offers what its state allows, to a person and to a machine
  The library of "The Web That Wasn't", as a HEART service, declared once for
  heart-core and served in three ways from that one declaration: HTML for a
  person in a browser, HAL-FORMS, and HAL Schema Forms. Ana Torres signs in
  with her library card number and PIN. A loan can be renewed, three weeks at
  a time, up to three times, unless a hold is waiting for the book; it can be
  returned while it is out. What a loan can't do now is absent, with the reason.

  Background:
    Given Riverside Public Library lends Ana Torres these books
      | loan | title           | author           | due        | renewals | hold waiting |
      | 3310 | The Hobbit      | J. R. R. Tolkien | 2026-11-12 | 0        | yes          |
      | 3311 | Matilda         | Roald Dahl       | 2026-11-12 | 0        | no           |
      | 3312 | Charlotte's Web | E. B. White      | 2026-11-12 | 3        | no           |

  Scenario Outline: Loan <loan> offers <acts> as <dialect>
    When Ana reads loan <loan> as <dialect>
    Then the loan offers <acts>

    Examples:
      | loan | dialect          | acts          |
      | 3311 | HAL Schema Forms | renew, return |
      | 3311 | HAL-FORMS        | renew, return |
      | 3311 | HTML             | renew, return |
      | 3310 | HAL Schema Forms | return        |
      | 3310 | HAL-FORMS        | return        |
      | 3310 | HTML             | return        |
      | 3312 | HAL Schema Forms | return        |
      | 3312 | HAL-FORMS        | return        |
      | 3312 | HTML             | return        |

  Scenario Outline: Loan <loan> can't be renewed, and says why, as <dialect>
    When Ana reads loan <loan> as <dialect>
    Then renewing is absent, because "<why>"

    Examples:
      | loan | dialect          | why                                                    |
      | 3310 | HAL Schema Forms | A hold is waiting for this book, so it can't be renewed. |
      | 3310 | HTML             | A hold is waiting for this book, so it can't be renewed. |
      | 3312 | HAL Schema Forms | It has been renewed 3 times, the most allowed.          |
      | 3312 | HTML             | It has been renewed 3 times, the most allowed.          |

  Scenario: Renewing moves the due date three weeks on, and counts the renewal
    When Ana renews loan 3311
    Then the answer is 200
    And loan 3311 is due "2026-12-03", renewed 1 time

  Scenario Outline: A renewal sent anyway for loan <loan> is refused, and the loan is as it was
    The server holds the same rule it shows: an act it doesn't offer is
    refused before anything changes.
    When Ana renews loan <loan> anyway, with the version she read
    Then the answer is 409
    And loan <loan> is due "2026-11-12", renewed <renewals> times

    Examples:
      | loan | renewals |
      | 3310 | 0        |
      | 3312 | 3        |

  Scenario: Returning a loan ends it: nothing is offered after
    When Ana returns loan 3311
    Then the answer is 200
    And loan 3311 offers nothing as HAL Schema Forms, and renewing is absent, because "It was returned."

  Scenario Outline: Ana's account links each book she has out, by its title, as <dialect>
    Her account is found from the bookmark by her library card number, so a
    person or an agent reaches every loan from there, with no title in hand.
    When Ana looks up her account by her card number, as <dialect>
    Then the account links her loans: The Hobbit, Matilda, Charlotte's Web

    Examples:
      | dialect          |
      | HAL Schema Forms |
      | HAL-FORMS        |
      | HTML             |

  Scenario: A book returned leaves her account
    When Ana returns loan 3311
    And Ana looks up her account by her card number, as HAL Schema Forms
    Then the account links her loans: The Hobbit, Charlotte's Web

  Scenario Outline: Searching the loans by title: "<text>"
    When Ana searches the loans for "<text>"
    Then she finds <found>

    Examples:
      | text   | found                                  |
      | hobbit | The Hobbit                             |
      |        | The Hobbit, Matilda, Charlotte's Web   |

  Scenario Outline: Signing in with card <card> and PIN <pin>
    When someone reads loan 3311 as HTML, signed in with card "<card>" and PIN "<pin>"
    Then the answer is <status>

    Examples:
      | card     | pin  | status |
      | 21000451 | 1234 | 200    |
      | 21000451 | 9999 | 401    |
      | 21000999 | 1234 | 401    |

  Scenario Outline: An agent finds a member by name, and the account links the books they have out: <name>
    The library opens with five members, each with books in different standings. An agent with the
    library's token finds any of them by name.

    Given the library as it opens, with its five members
    When an agent searches the accounts for "<name>"
    Then it finds exactly one account, "<name>"
    And that account links <books>

    Examples:
      | name         | books                                |
      | Ana Torres   | The Hobbit, Matilda, Charlotte's Web |
      | David Miller | The Secret Garden, Holes             |
      | Grace Okafor | Wonder, Hatchet                      |
      | Liam Chen    | Coraline                             |
      | Sofia Rossi  | The Giver, Frindle, Number the Stars |

  Scenario Outline: The library's company groups: a search by company lists its members five to a page: <company>
    The library adds groups for the companies whose staff it serves. A search of the members by company
    lists each, by name, five to a page, each page linking the next. The accounts search is as it was.

    Given the library as it opens, with its five members
    When an agent searches the members by company for "<company>"
    Then the search lists <members> members, 5 to a page, over <pages> pages
    And every member it lists works at "<works at>"

    Examples:
      | company      | members | pages | works at               |
      | Harbor Point | 15      | 3     | Harbor Point Logistics |
      | credit union | 2       | 1     | Riverside Credit Union |

  Scenario Outline: An OpenAPI client that follows the affordance link learns what loan <loan> allows now
    The library opts in to the affordance link (SPEC §6.1): a plain JSON answer keeps its body, with
    no controls in it, and names in a Link header the set of forms its state offers. A caller that
    ignores the header sees no difference.

    When an OpenAPI client reads loan <loan> as plain JSON
    Then the answer's body has no controls
    And its affordance link names a set offering <acts>

    Examples:
      | loan | acts          |
      | 3310 | return        |
      | 3311 | renew, return |
