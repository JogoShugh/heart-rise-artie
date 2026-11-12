Feature: artie generate --format yaml: a run's capture, made a script held as data
  spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 3. The same script artie generate
  writes as Kotlin, written as YAML in the vocabulary of
  spec/schemas/heart-script.draft.schema.json: block maps and lists only,
  every nested block a then, and every value read again written as a
  reference, never as code. An input is ${name}; a value an earlier answer
  brought into the run is read from the resource that step kept, by its
  member, ${issue.number}; a value the reasoner chose is the constant it
  was, with its review note as a YAML comment. The lock file is the same.
  artie run runs it (step 4): each step made through the same functions a
  Kotlin script calls, each reference read where it names, nothing
  evaluated; so it makes the same selections the run it came from made.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari |       |
      | /c/502     | Seen in #10.          |       |
    And these forms
      | at         | relation   | leads to   | outcome      |
      | /          | open-issue | /issues/10 | new resource |
      | /          | issues-get | /issues/10 | done         |
      | /issues/10 | comment    | /c/502     | new resource |
      | /issues/10 | close      | /issues/10 | done         |
    And the resource at "/issues/10" holds
      """
      {"number": 10, "title": "Login fails on Safari"}
      """
    And the form "issues-get" at "/" asks for these fields, typed
      | field  | type    |
      | number | integer |

  Scenario: A refusal the run expected, or one in a script a step ran, is no reason not to generate
    A release's capture holds the smoke script its step ran, and that script's planned refusal (S4, a
    renewal as of the version read before, refused as stale, expected so). The script made from the
    release is the release's own steps: the smoke script's are its own, and it expected its refusal.
    Given the file "release.trace.yaml" is
      """
      ---
      run:
        inputs: {}
        source: |-
          runScript("ci:smoke-test")
        marks:
          "1": {run-script: "ci:smoke-test", as: smoke, inputs: {}}
      ---
      mode: script
      say: enter {entry}
      params:
        entry: scripted://service/
      opens: true
      outcome: done
      ---
      mode: script
      say: act {relation}
      params:
        relation: renew
      outcome: refused
      expect: stale
      refused: {kind: stale, by: the server, detail: the loan moved on}
      ---
      mode: script
      say: return to {address}
      line: 1
      params:
        address: /
      outcome: done
      """
    When artie is run with
      | argument           |
      | generate           |
      | release.trace.yaml |
      | --out              |
      | release.heart.yaml |
      | --format           |
      | yaml               |
    Then artie exits 0
    And the file "release.heart.yaml" has these lines
      | line                         |
      | - run-script: ci:smoke-test  |

  Scenario: A reasoned run becomes a script as data, a value an earlier answer gave read from the resource kept
    Given the script "close.kts" is
      """kotlin
      val title by input()

      heart("scripted://service/") {
          var issue = 0L
          act(intent = "open an issue titled $title") {
              issue = state.long("number")
          }
          act(intent = "open issue $issue") {
              act(intent = "comment on it: Seen in #$issue.") {}
              act(intent = "close it, since it's done")
          }
      }
      """
    And the reasoner chooses
      | intent                                     | selection | relation   | fields                             |
      | open an issue titled Login fails on Safari | act       | open-issue | {"title": "Login fails on Safari"} |
      | open issue 10                              | act       | issues-get | {"number": "10"}                   |
      | comment on it: Seen in #10.                | act       | comment    | {"body": "Seen in #10."}           |
      | close it, since it's done                  | act       | close      | {"reason": "completed"}            |
    When artie is run with
      | argument                    |
      | run                         |
      | close.kts                   |
      | title=Login fails on Safari |
      | --reasoner                  |
      | claude                      |
      | --trace                     |
      | run.trace.yaml              |
    Then artie exits 0
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --script       |
      | close.kts      |
      | --format       |
      | yaml           |
    Then artie exits 0
    And artie prints
      | line                                            |
      | wrote gen.heart.yaml: 1 value to review         |
      | wrote gen.pins.yaml: 4 forms and 0 links pinned |
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml, the run of close.kts.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # A value marked "review:" holds a value the reasoner chose: check it before keeping the script.
      #   artie run gen.heart.yaml title='Login fails on Safari'
      heart: 1
      kind: kept
      start: scripted://service/

      inputs:
        title:

      steps:
        - act: open-issue
          fields:
            title: ${title}
          intent: open an issue titled ${title}
          then:
            - keep-as: issue
        - act: issues-get
          fields:
            number: ${issue.number}
          intent: open issue ${issue.number}
          then:
            - act: comment
              fields:
                body: "Seen in #${issue.number}."
              intent: "comment on it: Seen in #${issue.number}."
              then: []
            - act: close
              fields:
                reason: completed # review: the reasoner chose it, asked "close it, since it's done"
              intent: close it, since it's done
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json
    When artie is run with
      | argument                    |
      | run                         |
      | gen.heart.yaml              |
      | title=Login fails on Safari |
      | --trace                     |
      | gen.trace.yaml              |
    Then artie exits 0
    And the run traced in "gen.trace.yaml" made the same selections as the run traced in "run.trace.yaml"

  Scenario: Quoted words stay words, and a number an earlier answer gave stays a reference, in words and in checks
    A Kotlin script writes a quote inside its words as \", which is Kotlin,
    not the words: as data they are the words alone. Written in words only
    (--intents), each step still keeps the resource an earlier answer brought
    into the run, so "open issue $issue" reads ${issue.number}, not the 10
    that run held. A check the script wrote, of text that holds such a value,
    reads it the same way, so another run checks its own issue's comment.
    Given the script "close.kts" is
      """kotlin
      heart("scripted://service/") {
          var issue = 0L
          act(intent = "open an issue titled \"Login fails on Safari\"") {
              issue = state.long("number")
          }
          act(intent = "open issue $issue") {
              act(intent = "comment on it: Seen in #$issue.") {
                  check("body" eq "Seen in #$issue.")
              }
              act(intent = "close it, since it's done")
          }
      }
      """
    And the resource at "/c/502" holds
      """
      {"body": "Seen in #10."}
      """
    And the reasoner chooses
      | intent                                       | selection | relation   | fields                             |
      | open an issue titled "Login fails on Safari" | act       | open-issue | {"title": "Login fails on Safari"} |
      | open issue 10                                | act       | issues-get | {"number": "10"}                   |
      | comment on it: Seen in #10.                  | act       | comment    | {"body": "Seen in #10."}           |
      | close it, since it's done                    | act       | close      | {"reason": "completed"}            |
    When artie is run with
      | argument       |
      | run            |
      | close.kts      |
      | --reasoner     |
      | claude         |
      | --trace        |
      | run.trace.yaml |
    Then artie exits 0
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --script       |
      | close.kts      |
      | --intents      |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml, the run of close.kts.
      # The run in words: its shape as it nested, each act said in words for the reasoner to choose on every run.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # 4 steps call the model on every run: artie run needs --reasoner.
      #   artie run gen.heart.yaml --reasoner claude
      heart: 1
      kind: intent
      start: scripted://service/

      steps:
        - intent: open an issue titled "Login fails on Safari"
          then:
            - keep-as: issue
        - intent: open issue ${issue.number}
          then:
            - intent: "comment on it: Seen in #${issue.number}."
              then:
                - check:
                    - field: body
                      is: "Seen in #${issue.number}."
            - intent: close it, since it's done
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json
    When artie is run with
      | argument        |
      | generate        |
      | run.trace.yaml  |
      | --out           |
      | kept.heart.yaml |
      | --script        |
      | close.kts       |
      | --format        |
      | yaml            |
    Then artie exits 0
    And the file "kept.heart.yaml" has these lines
      | line                                                 |
      | intent: open an issue titled "Login fails on Safari" |
      | is: "Seen in #${issue.number}."                      |
    And the file "kept.heart.yaml" mentions "Seen in #10." nowhere

  Scenario: A list's members the run visited one by one are written as a for-each, each acted on by what it offers
    spec/PLAN-COST-FOLLOWS-STATES.md, step 3. A goal run visited the
    employees a search found, one after another: Anna, given notice; Maria,
    dismissed; Laura, offering neither, left. Written one by one, by name,
    the script would serve those three alone. Members of one list, each
    opened by its item link and acted on once, are written as a for-each
    over the list, named after where its members live (/employees/…, an
    employee): when-offered makes, for each member, the first of the
    run's acts that the member offers now, in the order the run first made
    them, and otherwise notes the member it leaves. The step's words stay
    with it, so a member offering an act the run never met falls back to
    them.
    Given the file "run.trace.yaml" is the capture "miller-members.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # The members of a list the run visited one by one are each acted on by what they offer (when-offered).
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # After an act, what it changed is checked: the run stops where that no longer holds.
      #   artie run gen.heart.yaml
      heart: 1
      kind: kept
      start: http://localhost:8099/

      steps:
        - act: staff:search-employees
          fields:
            name: Miller
          intent: Find the employees named Miller
          then:
            - for-each: employee
              then:
                - when-offered:
                    - act: staff:give-notice
                      inferred: give notice to this employee
                      from-intent: "Move each of them one step: give notice to anyone working, dismiss anyone on notice, and leave the dismissed as they are"
                      then:
                        - check:
                            - field: status
                              is: on notice
                    - act: staff:dismiss
                      inferred: dismiss this employee
                      from-intent: "Move each of them one step: give notice to anyone working, dismiss anyone on notice, and leave the dismissed as they are"
                      then:
                        - check:
                            - field: status
                              is: dismissed
                  intent: "Move each of them one step: give notice to anyone working, dismiss anyone on notice, and leave the dismissed as they are"
                  otherwise:
                    - note: none of these acts is offered here
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: A list's members are written as a for-each when the run acted on one of them alone
    A goal run went through each book on a library account: The Hobbit,
    offering no renew, left; Matilda, renewed; Charlotte's Web, offering no
    renew, left. The books it left are members it met, not detours: the
    script goes through every book the account links each time it runs,
    renewing whichever offers it then.
    Given the file "run.trace.yaml" is the capture "library-account.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # The members of a list the run visited one by one are each acted on by what they offer (when-offered).
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      #   artie run gen.heart.yaml card=21000451
      heart: 1
      kind: kept
      start: http://localhost:8140/

      inputs:
        card:
          type: integer

      steps:
        - act: library:get-account
          fields:
            id: ${card}
          intent: Look up Ana's account by her library card number ${card}
          then:
            - for-each: loan
              then:
                - when-offered:
                    - act: library:renew
                      inferred: renew this book (${loan.title})
                      from-intent: "For each book she has out: renew it if renewing is offered; otherwise say why it can't be renewed"
                      then: []
                  intent: "For each book she has out: renew it if renewing is offered; otherwise say why it can't be renewed"
                  otherwise:
                    - note: none of these acts is offered here
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario Outline: A result the run followed by an input's value is followed by that input, as <form>
    A goal run searched the accounts for the member named in its input,
    Ana Torres, and followed the result of that title. The script follows the
    result titled by the input, so it serves whichever member it is given,
    not Ana alone.
    Given the file "run.trace.yaml" is the capture "library-member.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | <out>          |
      | --format       |
      | <form>         |
    Then artie exits 0
    And the file "<out>" has these lines
      | line      |
      | <follows> |

    Examples:
      | form | out            | follows                                                                                 |
      | yaml | gen.heart.yaml | title: ${member}                                                                         |
      | kts  | gen.heart.kts  | follow("item", member, intent = "Find the account of the member named ${member}") {    |

  Scenario Outline: The words a model chose an act by, inside a loop, name the member the loop is at: <form>
    The model said why it renewed a book with the book's title, Matilda. In
    a loop over every book, the words read the title of whichever book the
    loop is at, so no title the run met is written in the script.
    Given the file "run.trace.yaml" is the capture "library-member.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | <out>          |
      | --format       |
      | <form>         |
    Then artie exits 0
    And the file "<out>" mentions '<words>'
    And the file "<out>" mentions "Matilda" nowhere

    Examples:
      | form | out            | words                                         |
      | yaml | gen.heart.yaml | inferred: renew this book (${loan.title})     |
      | kts  | gen.heart.kts  | inferred = "renew this book (${loanTitle})"   |

  Scenario Outline: A list the run went through, page after page, is one loop, and each member's own list is another, as <form>
    A goal run found the members of a company group, five to a page, and
    went through every member on every page, following each page's next
    link, and through each member's books, renewing those that offered it.
    The script names no member, no book and no page: it goes through every
    member the search lists, on however many pages, and every book each has
    out, each time it runs. A member with one book is a list of one.
    Given the file "run.trace.yaml" is the capture "library-company.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | <out>          |
      | --format       |
      | <form>         |
    Then artie exits 0
    And the file "<out>" has these lines, in order
      | line      |
      | <members> |
      | <books>   |
      | <offered> |
    And the file "<out>" mentions "<follow>" nowhere
    And the file "<out>" mentions "whenever" nowhere
    And the file "<out>" mentions "Maria Garcia" nowhere
    And the file "<out>" mentions "The Westing Game" nowhere

    Examples:
      | form | out            | members            | books             | offered         | follow  |
      | yaml | gen.heart.yaml | - for-each: member | - for-each: loan  | - when-offered: | follow: |
      | kts  | gen.heart.kts  | each("member") {   | each("loan") {    | whenOffered(    | follow( |

  Scenario Outline: A value the run read from a loop's member is read from whichever member the loop is at: <form>
    The library renews a company group member's book for the member, with
    their card number, which the run read from the member it opened, two
    loops up. The script reads it from the member each loop is at, by the
    loop's name, and keeps no member of its own.
    Given the file "run.trace.yaml" is the capture "library-company.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | <out>          |
      | --format       |
      | <form>         |
    Then artie exits 0
    And the file "<out>" has these lines, in order
      | line      |
      | <members> |
      | <books>   |
      | <act>     |
    And the file "<out>" mentions '<read>'
    And the file "<out>" mentions '<card>'
    And the file "<out>" mentions "<kept>" nowhere
    And the file "<out>" mentions "21000456" nowhere

    Examples:
      | form | out            | members            | books            | act                                | read                                          | card               | kept     |
      | yaml | gen.heart.yaml | - for-each: member | - for-each: loan | - act: library:renew-for-member    | ${member.id}                                  | card: ${member.id} | keep-as: |
      | kts  | gen.heart.kts  | each("member") {   | each("loan") {   | case("library:renew-for-member") { | val memberId by kept("member") { long("id") } | "card" to memberId | keepAs(  |

  Scenario: The company script, held as data, is a script as data
    Given the file "run.trace.yaml" is the capture "library-company.trace.yaml"
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: An input the run sent as a number is declared with its type
    A script's inputs arrive as text. Where the run sent an input as an
    integer, the Kotlin script says number.toLong(); held as data, the input
    says its type, and artie run reads it as one.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs:
          number:
            value: '10'
            given: given
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      now:
        state:
          title: Repositories
          hr:manifest:
            forms:
              issues-get:
                - sha256-issues
            digest: sha256-every-form
      ---
      mode: script
      say: act {relation}, number {number}
      line: 2
      within: 1
      params:
        relation: issues-get
        number: 10
      values:
        number:
          value: 10
          type: integer
          from:
            - input: number
      chose:
        kind: form
        relation: issues-get
      guard:
        rung: L2
        form: sha256-issues
      outcome: done
      now:
        state:
          number: 10
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      #   artie run gen.heart.yaml number=10
      heart: 1
      kind: kept
      start: http://localhost:8392/

      inputs:
        number:
          type: integer

      steps:
        - act: issues-get
          fields:
            number: ${number}
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: A follow, a read again, a wait and a check, each written as data from the capture
    The capture of generate.feature's "Without the script" scenario. A
    follow that opens nothing stands alone; the block of an act that made a
    resource reads it again first, sync; the wait and the check are the
    capture's, never the Kotlin of a script: until a relation is offered,
    and a member's value checked.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs:
          owner:
            value: acme
            given: given
          stamp:
            value: 1018-2200
            given: default
            default: 1018-2200
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      now:
        state:
          title: Rockets
          hr:manifest:
            forms:
              repos-get:
                - sha256-repos
            digest: sha256-every-form
      ---
      mode: intent
      say: open the repository acme
      line: 2
      within: 1
      params:
        relation: repos-get
        owner: acme
      values:
        owner:
          value: acme
          type: string
          from:
            - input: owner
      reason:
        template: open the repository $owner
        slots:
          owner:
            value: acme
            from:
              - input: owner
      opens: true
      chose:
        kind: form
        relation: repos-get
      guard:
        rung: L2
        form: sha256-repos
      outcome: done
      now:
        state:
          name: rockets
          head_sha: abc123
      ---
      mode: intent
      say: follow its branches
      line: 3
      within: 2
      params:
        relation: branches
      reason:
        template: follow its branches
        slots: {}
      chose:
        kind: link
        relation: branches
        target: /repos/{owner}/branches
      guard:
        rung: L2
      outcome: done
      ---
      mode: intent
      say: start a branch named fix-1018-2200
      line: 4
      within: 2
      params:
        relation: start-branch
        name: fix-1018-2200
      values:
        name:
          value: fix-1018-2200
          type: string
          from:
            - parts:
                1018-2200:
                  input: stamp
      reason:
        template: start a branch named fix-$stamp
        slots:
          stamp:
            value: 1018-2200
            from:
              - input: stamp
      opens: true
      chose:
        kind: form
        relation: start-branch
      guard:
        rung: L2
        set:
          integrity: sha256-set1
        form: sha256-start
      outcome: new resource
      now:
        state:
          ref: fix-1018-2200
          sha: abc123
      ---
      mode: script
      say: until
      line: 5
      within: 4
      params: {}
      waited:
        for: offers("merge")
        reads: 2
      outcome: held
      ---
      mode: intent
      say: merge it
      line: 6
      within: 4
      params:
        relation: merge
        sha: abc123
        merge_method: squash
      values:
        sha:
          value: abc123
          type: string
          from:
            - step: 4
              pointer: /sha
            - step: 2
              pointer: /head_sha
        merge_method:
          value: squash
          type: string
          from:
            - chosen
      reason:
        template: merge it
        slots: {}
      chose:
        kind: form
        relation: merge
      guard:
        rung: L2
        set:
          integrity: sha256-set2
        form: sha256-merge
      outcome: done
      ---
      mode: script
      say: check
      line: 7
      within: 4
      params:
        facts:
          - member: merged
            test: eq true
            held: 'true'
            holds: true
      outcome: held
      ---
      mode: script
      say: return to {address}
      line: 4
      within: 2
      params:
        address: http://localhost:8392/repos/acme/branches
      outcome: done
      ---
      mode: script
      say: return to {address}
      line: 2
      within: 1
      params:
        address: http://localhost:8392/
      outcome: done
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And artie prints
      | line                                           |
      | wrote gen.heart.yaml: 1 value to review        |
      | wrote gen.pins.yaml: 3 forms and 1 link pinned |
    And the file "gen.heart.yaml" reads
      """yaml
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # A value marked "review:" holds a value the reasoner chose: check it before keeping the script.
      #   artie run gen.heart.yaml owner=acme
      heart: 1
      kind: kept
      start: http://localhost:8392/

      inputs:
        owner:
        stamp:
          default: 1018-2200

      steps:
        - act: repos-get
          fields:
            owner: ${owner}
          intent: open the repository ${owner}
          then:
            - keep-as: headSha
            - follow: branches
              intent: follow its branches
            - act: start-branch
              fields:
                name: fix-${stamp}
              intent: start a branch named fix-${stamp}
              then:
                - sync: true
                - until:
                    offered: merge
                - act: merge
                  fields:
                    sha: ${headSha.head_sha}
                    merge_method: squash # review: the reasoner chose it, asked "merge it"
                  intent: merge it
                - check:
                    - field: merged
                      is: true
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: An act whose value the resource it acts on gave is written in a decide that keeps that resource again
    The run worked the role out from the role it read. Someone may change
    it between that read and the act: the act is then refused as stale, and
    a decide reads the resource again, keeps it again and works the value
    out anew from what is there now, rather than stopping.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
        source: |-
          act(intent = "find Frodo")
          act(intent = "open Frodo Baggins")
          act(intent = "add retired to his role")
      ---
      mode: script
      say: enter {entry}
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      address: http://localhost:8392/
      ---
      mode: intent
      say: find Frodo
      line: 1
      params:
        relation: search
        name: Frodo
      values:
        name:
          value: Frodo
          type: string
          from:
            - words
      reason:
        template: find Frodo
        slots: {}
      opens: true
      chose:
        kind: form
        relation: search
      guard:
        rung: L0
        form: sha256-search
      outcome: done
      address: http://localhost:8392/employees?name=Frodo
      ---
      mode: intent
      say: open Frodo Baggins
      line: 2
      within: 1
      params:
        relation: item
        title: Frodo Baggins
      values:
        title:
          value: Frodo Baggins
          from:
            - words
      reason:
        template: open Frodo Baggins
        slots: {}
      opens: true
      chose:
        kind: link
        relation: item
        target: http://localhost:8392/employees/1
      outcome: done
      address: http://localhost:8392/employees/1
      now:
        state:
          id: 1
          name: Frodo Baggins
          role: ring bearer
      ---
      mode: intent
      say: add retired to his role
      line: 3
      within: 2
      params:
        relation: update
        role: ring bearer, retired
      values:
        role:
          value: ring bearer, retired
          type: string
          from:
            - parts:
                ring bearer:
                  step: 3
                  pointer: /role
      reason:
        template: add retired to his role
        slots: {}
      chose:
        kind: form
        relation: update
      guard:
        rung: L2
        form: sha256-update
      outcome: done
      address: http://localhost:8392/employees/1
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      #   artie run gen.heart.yaml
      heart: 1
      kind: kept
      start: http://localhost:8392/

      steps:
        - act: search
          fields:
            name: Frodo
          inferred: find Frodo
          then:
            - follow: item
              title: Frodo Baggins
              inferred: open Frodo Baggins
              then:
                - decide:
                    steps:
                      - keep-as: role
                      - act: update
                        fields:
                          role: ${role.role}, retired
                        inferred: add retired to his role
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: After an act, what it changed is checked, as the run saw it: what it sent, and text it changed to
    A kept script made from a run that did only part of its work, or run
    after a release that changed what an act does, would otherwise finish
    as if it were right. An act works on the resource the run holds; one
    whose form's method changes it (not GET), and that made no new resource,
    answers with that resource's next state. The members that answer changed
    from the state held before are checked: one the act sent, by the same
    value or reference; one it changed to text or a truth, by that value. A
    number, a time or a hash (a commit's, say) it changed to may differ every
    run, so it isn't checked unless the act sent it. No address is compared:
    a search, a GET form, goes elsewhere and is never checked.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
        source: |-
          act(intent = "open Frodo Baggins")
          act(intent = "retire him, adding retired to his role")
      ---
      mode: script
      say: enter {entry}
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      address: http://localhost:8392/
      ---
      mode: intent
      say: open Frodo Baggins
      line: 1
      params:
        relation: item
        title: Frodo Baggins
      values:
        title:
          value: Frodo Baggins
          from:
            - words
      reason:
        template: open Frodo Baggins
        slots: {}
      opens: true
      chose:
        kind: link
        relation: item
        target: http://localhost:8392/employees/1
      outcome: done
      address: http://localhost:8392/employees/1
      now:
        state:
          id: 1
          role: ring bearer
          status: working
          version: 1
          revision: 2c7d4f0a91b3e8d6c5f4a3b2c1d0e9f8a7b6c5d4
          updatedAt: 2026-11-03T10:00:00Z
      ---
      mode: intent
      say: retire him, adding retired to his role
      line: 2
      within: 1
      params:
        relation: retire
        role: ring bearer, retired
      values:
        role:
          value: ring bearer, retired
          type: string
          from:
            - parts:
                ring bearer:
                  step: 2
                  pointer: /role
      reason:
        template: retire him, adding retired to his role
        slots: {}
      chose:
        kind: form
        relation: retire
        method: PUT
      guard:
        rung: L2
        form: sha256-retire
      outcome: done
      address: http://localhost:8392/employees/1
      now:
        state:
          id: 1
          role: ring bearer, retired
          status: retired
          version: 2
          revision: 9f8e7d6c5b4a39281706f5e4d3c2b1a098f7e6d5
          updatedAt: 2026-11-03T10:00:05Z
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # After an act, what it changed is checked: the run stops where that no longer holds.
      #   artie run gen.heart.yaml
      heart: 1
      kind: kept
      start: http://localhost:8392/

      steps:
        - follow: item
          title: Frodo Baggins
          inferred: open Frodo Baggins
          then:
            - decide:
                steps:
                  - keep-as: role
                  - act: retire
                    fields:
                      role: ${role.role}, retired
                    inferred: retire him, adding retired to his role
                    then:
                      - check:
                          - field: role
                            is: ${role.role}, retired
                          - field: status
                            is: retired
      """
    And the file "gen.heart.yaml" is a script as data, by spec/schemas/heart-script.draft.schema.json

  Scenario: What the run checks itself right after an act isn't checked twice
    A goal that says "Verify: he is retired" right after the act has the run
    check it there. The act's own check, of what it changed, leaves out each
    member that check holds, so the script says it once: the role, which
    only the act checks, stays in the act's check; the status is the run's.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
        source: |-
          act(intent = "open Frodo Baggins")
          act(intent = "retire him, adding retired to his role")
          check("status" to "retired")
      ---
      mode: script
      say: enter {entry}
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      address: http://localhost:8392/
      ---
      mode: intent
      say: open Frodo Baggins
      line: 1
      params:
        relation: item
        title: Frodo Baggins
      values:
        title:
          value: Frodo Baggins
          from:
            - words
      reason:
        template: open Frodo Baggins
        slots: {}
      opens: true
      chose:
        kind: link
        relation: item
        target: http://localhost:8392/employees/1
      outcome: done
      address: http://localhost:8392/employees/1
      now:
        state:
          id: 1
          role: ring bearer
          status: working
      ---
      mode: intent
      say: retire him, adding retired to his role
      line: 2
      within: 1
      params:
        relation: retire
        role: ring bearer, retired
      values:
        role:
          value: ring bearer, retired
          type: string
          from:
            - parts:
                ring bearer:
                  step: 2
                  pointer: /role
      reason:
        template: retire him, adding retired to his role
        slots: {}
      chose:
        kind: form
        relation: retire
        method: PUT
      guard:
        rung: L2
        form: sha256-retire
      outcome: done
      address: http://localhost:8392/employees/1
      now:
        state:
          id: 1
          role: ring bearer, retired
          status: retired
      ---
      mode: script
      say: return to {address}
      params:
        address: http://localhost:8392/employees/1
      outcome: done
      address: http://localhost:8392/employees/1
      ---
      mode: script
      say: check
      line: 3
      within: 1
      params:
        facts:
          - member: status
            test: eq retired
            held: retired
            holds: true
      outcome: held
      address: http://localhost:8392/employees/1
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.heart.yaml" reads
      """
      # Generated by artie generate from run.trace.yaml.
      # Every selection as that run made it, nested as it nested, with no model.
      # What it was offered is pinned in gen.pins.yaml, held to before every act.
      # After an act, what it changed is checked: the run stops where that no longer holds.
      #   artie run gen.heart.yaml
      heart: 1
      kind: kept
      start: http://localhost:8392/

      steps:
        - follow: item
          title: Frodo Baggins
          inferred: open Frodo Baggins
          then:
            - decide:
                steps:
                  - keep-as: role
                  - act: retire
                    fields:
                      role: ${role.role}, retired
                    inferred: retire him, adding retired to his role
                    then:
                      - check:
                          - field: role
                            is: ${role.role}, retired
            - check:
                - field: status
                  is: retired
      """

  Scenario Outline: A form taken at the entry doesn't say the rung the run read at
    The entry is a bookmark: the service's root, which answers at L0 whatever
    rung is asked, or an address the service handed over, which answers at
    the rung asked. A form taken at the entry answered at L0 says nothing of
    the rung, so a run whose only act was there pins none, and its script
    reads at whatever rung it is run at.
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: <entry>
      opens: true
      outcome: done
      address: <entry>
      ---
      mode: script
      say: act {relation}
      line: 2
      within: 1
      params:
        relation: close
      chose:
        kind: form
        relation: close
      guard:
        rung: <answered at>
        form: sha256-close
      outcome: done
      address: http://localhost:8392/issues/10
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.heart.yaml |
      | --format       |
      | yaml           |
    Then artie exits 0
    And the file "gen.pins.yaml" has <count> lines that read "rung: <answered at>"

    Examples:
      | entry                           | answered at | count |
      | http://localhost:8392/          | L0          | 0     |
      | http://localhost:8392/issues/10 | L2          | 1     |
