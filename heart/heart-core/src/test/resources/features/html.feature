Feature: HTML for people, rendered from the declaration
  spec/PLAN-HTML-DIALECT.md, decided 2026-10-30. heart-core renders a
  resource as HTML from its L0 representation and the explanation of the
  acts it leaves out, so every binding serves the same page: the state laid
  out for a person, a button for each act offered now, which opens a dialog
  that asks for the act's fields, and a greyed button, with the server's
  reason, for each act it won't offer. Labels are titled from member names;
  the names on the wire never change.

  Background:
    Given a lamp resource type declared with the profile "home"
    And lamp 7 is "ON" at version 3

  Scenario: The state is laid out for a person, each member titled from its name
    When lamp 7 is rendered as HTML
    Then the HTML shows the state
      | member     | label      | value |
      | id         | Id         | 7     |
      | status     | Status     | ON    |
      | brightness | Brightness | 50    |

  Scenario: Short values sit side by side; a long value takes a whole row; the acts stand beside the state
    The author, 2026-10-30: one label above another down the page is lame on
    a large screen. Short members are tiles that fill the width; a long text
    or a structure takes a whole row; the acts are a column of their own
    beside the information, which a small screen stacks below it.
    When the representation {"title":"Bump left-pad","number":1873,"state":"open","draft":true,"body":"Bumps left-pad from x to 1.0.43. Opened by a Dependabot stand-in (exhibits/dependabot-sim).","head":{"ref":"deps/left-pad"},"_links":{"self":{"href":"/pulls/1873"}},"_forms":{"x:close":{"title":"Close","method":"PATCH","_links":{"target":{"href":"/pulls/1873/actions/close"}},"schema":{"type":"object"}}}} is rendered as HTML
    Then the members laid out a whole row wide are
      | member |
      | body   |
      | head   |
    And the acts stand in a column beside the state

  Scenario: Each act is a button in the type's order, offered or greyed with the server's reason
    An act listed as unavailable keeps its place and stays focusable, so a
    keyboard reaches it and a person learns why. An act neither offered nor
    explained has no button.
    When lamp 7 is rendered as HTML
    Then the HTML shows these acts, in order
      | act             | label        | offered | reason                  |
      | home:switch-on  | Switch on    | no      | The lamp is already on. |
      | home:switch-off | Switch off   | yes     |                         |
      | home:dim        | Dim the lamp | yes     |                         |

  Scenario: An act's dialog sends to the act's target, by its method, with the validator the HTML was rendered under
    When lamp 7 is rendered as HTML under the validator "seq-3.html.L0.abc"
    Then the "home:dim" dialog sends PUT to "/lamps/7/actions/dim" with If-Match "seq-3.html.L0.abc"
    And the "home:switch-off" dialog sends POST to "/lamps/7/actions/switch-off" with If-Match "seq-3.html.L0.abc"

  Scenario: An act's dialog asks for its fields; one with none still opens, as a confirmation
    When lamp 7 is rendered as HTML
    Then the "home:dim" dialog asks for these fields
      | field      | label      | control | required | constraints                         |
      | brightness | Brightness | number  | yes      | min=1 max=100 step=1 placeholder=50 |
      | label      | Label      | text    | no       | minlength=1 placeholder=reading     |
    And the "home:switch-off" dialog asks for no fields

  Scenario: Each dialog carries its act's schema, so the browser checks the whole of it before sending
    HTML's own constraints say only what one field needs. A schema says
    more: at least one of two fields, one field only with another. The page
    runs the act's own schema in the browser before it sends anything; the
    server still decides.
    When lamp 7 is rendered as HTML
    Then the "home:dim" dialog carries the schema of its form
    And the "home:switch-off" dialog carries the schema of its form

  Scenario: A page's validator covers the scripts it names, so a release that changes them is never answered 304
    Live, 2026-10-30: heart-forms.js changed, the page's state did not, and a
    browser revalidating the page was told 304 and kept running the old script.
    When lamp 7's HTML validator is worked out
    Then what it names includes the published name of each script and stylesheet

  Scenario: A schema's words never end the script that carries them
    A description from a server, or through a gateway from an upstream, may
    hold "</script>": inside the page it must stay words.
    When the representation {"_links":{"self":{"href":"/x"}},"_forms":{"x:act":{"title":"Act","method":"POST","_links":{"target":{"href":"/x/actions/act"}},"schema":{"type":"object","properties":{"f":{"type":"string","description":"</script><b>x</b>"}}}}}} is rendered as HTML
    Then the "x:act" dialog carries a schema whose "f" is described as "</script><b>x</b>"
    And the page has no element "b"

  Scenario Outline: A field's control and constraints come from its schema: <kind>
    When a field "f" with the schema <schema> is rendered as HTML
    Then the field is a <control> with <constraints>

    Examples:
      | kind                | schema                                                                | control      | constraints                         |
      | an integer          | {"type":"integer","minimum":1,"maximum":100}                          | number       | min=1 max=100 step=1                |
      | a number            | {"type":"number"}                                                     | number       | step=any                            |
      | a boolean           | {"type":"boolean"}                                                    | checkbox     | none                                |
      | a choice            | {"type":"string","enum":["warm","cool"],"default":"warm"}             | select       | options=warm,cool selected=warm     |
      | a patterned text    | {"type":"string","pattern":"^[A-Z ]+$","maxLength":20}                | text         | pattern=^[A-Z ]+$ maxlength=20      |
      | an example          | {"type":"string","examples":["reading"]}                              | text         | placeholder=reading                 |
      | a list of texts     | {"type":"array","items":{"type":"string"}}                            | text list    | none                                |
      | a plain text        | {"type":"string"}                                                     | text         | none                                |

  Scenario Outline: A text member named for long text is a text area: <name>
    When a field "<name>" with the schema {"type":"string"} is rendered as HTML
    Then the field is a <control> with none

    Examples:
      | name    | control  |
      | body    | textarea |
      | content | textarea |
      | message | text     |

  Scenario: A field's words are shown as text, never run as markup
    The words come from a server, and through a gateway from an upstream's
    description: a page must never run them.
    When a field "f" with the schema {"type":"string","title":"<b>Name</b>","description":"<script>x()</script> & more"} is rendered as HTML
    Then its label reads "<b>Name</b>" and its hint reads "<script>x()</script> & more"
    And the rendered field has no element "b" or "script"

  Scenario Outline: A browser's form fields become the act's JSON, each typed by the act's schema: <case>
    A browser sends a form's fields as text. Each is read as its property's
    type says; a field left blank is left out, as a person who fills nothing
    in means; a value that isn't its type stays text, so the schema check
    refuses it at that field, as for any client.
    When the form "<form>" is read by the schema <schema>
    Then the form's input is <json>

    Examples:
      | case               | schema                                                                                | form                                    | json                          |
      | an integer         | {"type":"object","properties":{"n":{"type":"integer"}}}                               | n=20                                    | {"n":20}                      |
      | a number           | {"type":"object","properties":{"n":{"type":"number"}}}                                | n=2.5                                   | {"n":2.5}                     |
      | a ticked box       | {"type":"object","properties":{"draft":{"type":"boolean"}}}                           | draft=true                              | {"draft":true}                |
      | a list             | {"type":"object","properties":{"reviewers":{"type":"array","items":{"type":"string"}}}} | reviewers=ada&reviewers=lin&reviewers= | {"reviewers":["ada","lin"]}   |
      | an empty list      | {"type":"object","properties":{"reviewers":{"type":"array","items":{"type":"string"}}}} | reviewers=&reviewers=                  | {}                            |
      | a blank field      | {"type":"object","properties":{"title":{"type":"string"},"base":{"type":"string"}}}   | title=&base=main                        | {"base":"main"}               |
      | text with a space  | {"type":"object","properties":{"title":{"type":"string"}}}                            | title=Fix+the+%3Cform%3E                | {"title":"Fix the <form>"}    |
      | not a number       | {"type":"object","properties":{"n":{"type":"integer"}}}                               | n=many                                  | {"n":"many"}                  |

  Scenario: The bookmark's forms are dialogs: a search goes to an address, an act is sent by its method
    A search or a lookup is safe: it is sent as a plain form, so its answer
    is a page of its own, with an address and a way back. The page fills its
    target's template from the fields, a path's variable and a query's alike.
    When the service index of lamps is rendered as HTML under the validator "index-html-abc"
    Then the "home:search-lamps" dialog goes to the address "/lamps{?name}"
    And the "home:get-lamp" dialog goes to the address "/lamps/{id}"
    And the "home:install-lamp" dialog sends POST to "/actions/install-lamp" with If-Match "index-html-abc"

  Scenario: A page of matches is a grid: each a row, titled and linked, with the members their summaries share
    A match carries no forms (SPEC §6.11), so each row's acts are read from
    the match itself when a person asks for them.
    When the page {"count":2,"_links":{"self":{"href":"/pulls"},"next":{"href":"/pulls?page=2"},"item":[{"href":"/pulls/1873","title":"Bump left-pad"},{"href":"/pulls/1872","title":"Fix login"}]},"_embedded":{"item":[{"number":1873,"title":"Bump left-pad","state":"open","draft":true,"_links":{"self":{"href":"/pulls/1873"}}},{"number":1872,"title":"Fix login","state":"open","draft":false,"_links":{"self":{"href":"/pulls/1872"}}}]}} is rendered as HTML titled "Pull requests"
    Then the grid has these rows
      | href       | Title         | Number | State | Draft |
      | /pulls/1873 | Bump left-pad | 1873   | open  | true  |
      | /pulls/1872 | Fix login     | 1872   | open  | false |
    And each row's acts are read from its match when asked
    And the page links "next" to "/pulls?page=2"

  Scenario Outline: An embedded resource is named for a person, never by its address: <by>
    Live, 2026-10-30: the GitHub gateway's pull request embeds its author, a
    user with a login but no title, and the page listed "/users/JogoShugh".
    When the representation {"_links":{"self":{"href":"/pulls/1873"}},"_embedded":{"author":<item>}} is rendered as HTML
    Then the HTML lists "Author" as "<words>" linked to "/users/ada"

    Examples:
      | by              | item                                                                           | words    |
      | its link title  | {"login":"ada","_links":{"self":{"href":"/users/ada","title":"Ada Lovelace"}}} | Ada Lovelace |
      | its name        | {"login":"ada","name":"Ada","_links":{"self":{"href":"/users/ada"}}}           | Ada      |
      | its login       | {"id":7,"login":"ada","_links":{"self":{"href":"/users/ada"}}}                 | ada      |
      | its first words | {"id":7,"sha":"abc123","_links":{"self":{"href":"/users/ada"}}}                | abc123   |

  Scenario Outline: A resource's heading names it for a person: <by>
    Live, 2026-10-30: a garden bed's page was headed "/beds/1", where the
    bed is labelled "bed-1".
    When the representation <body> is rendered as HTML
    Then the page is headed "<heading>"

    Examples:
      | by             | body                                                                         | heading |
      | its link title | {"label":"bed-1","_links":{"self":{"href":"/beds/1","title":"North bed"}}}   | North bed |
      | its title      | {"title":"Fix login","label":"x","_links":{"self":{"href":"/pulls/1"}}}      | Fix login |
      | its label      | {"id":1,"status":"EMPTY","label":"bed-1","_links":{"self":{"href":"/beds/1"}}} | bed-1   |
      | its address    | {"id":7,"status":"ON","_links":{"self":{"href":"/lamps/7"}}}                 | /lamps/7 |

  Scenario: A grid leaves out a member that only repeats each row's title
    Live, 2026-10-30: the garden's beds listed each label twice, as the
    title and again as a column.
    When the page {"count":1,"_links":{"self":{"href":"/beds"},"item":[{"href":"/beds/1","title":"bed-1"}]},"_embedded":{"item":[{"id":1,"label":"bed-1","status":"EMPTY","_links":{"self":{"href":"/beds/1"}}}]}} is rendered as HTML titled "Beds"
    Then the grid has these rows
      | href    | Title | Id | Status |
      | /beds/1 | bed-1 | 1  | EMPTY  |

  Scenario: A refusal, for a browser, is HTML: its title, its detail, and each violation at its field
    When the problem {"type":"https://w3id.org/heart-rise/problems/invalid-input","title":"The body does not match the form's schema","status":422,"detail":"request body does not satisfy the affordance schema","violations":[{"pointer":"/brightness","message":"must be at most 100"}]} is rendered as HTML
    Then the HTML problem reads "The body does not match the form's schema"
    And the HTML problem names the field "/brightness" as "must be at most 100"
