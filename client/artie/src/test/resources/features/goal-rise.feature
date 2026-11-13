Feature: A goal that crosses parties and waits on their answers: At, asking to be told, Once
  A release's goal says, in a person's words, where to go and what to wait for. `At {party}, …`
  goes to that party's entry and does the rest of the step there. A step that asks to be told
  (`ask to be told …`, `asking to be told …`), named `as NAME`, makes its act with a reply the
  host keeps: the act carries the reply in its envelope, and the reply is kept by that name.
  `Once {NAME} is answered, …` waits on that reply, with no model, then does the rest of the step
  holding it. The run is the planner's once; artie generate keeps each of these as the script's own
  step (`enter`, `ask`, `once-answered`), so every run after is the script, with no model.

  Background:
    Given the offer watch and the desk's replies run in this process, the watch answering through ARTIE
    And the file "watch.goal.md" is
      """
      ---
      start: "{desk}"
      inputs:
        desk:
        watcher:
      ---

      Wait until the desk offers to release, then go to it.

      - At {watcher}, ask to be told when {desk} offers desk:release `as watch`
      - Once {watch} is answered, follow it to the resource it watched `as watched`
      """

  Scenario: A step runs the script a resource links, on another resource it links, with no model
    A build links the smoke script it archived and the deployment it made: `Run the script it links
    as REL, on the deployment it links as REL, with NAME = {value}, … as NAME` runs that script on
    that deployment, as written, and keeps its outcome by the step's name, so later steps read it.
    The script made from the run does the same.
    Given the desk serves a build that links its smoke script and its deployment
    And the file "smoke-run.goal.md" is
      """
      ---
      start: "{build}"
      inputs:
        build:
        book: { default: "Matilda" }
      ---

      Smoke test what the build made.

      - Run the script it links as ci:smoke-test, on the deployment it links as ci:deployment, with book = {book} `as smoke`
      """
    When the goal "smoke-run.goal.md" runs with ARTIE in the desk, captured in "smoke-run.trace.yaml", given
      | name  | value                              |
      | build | http://localhost:8160/test-build   |
    Then the run finished
    And the run kept "smoke" with "passed" true
    When artie generated "smoke-run.heart.yaml" as data from "smoke-run.trace.yaml" and "smoke-run.goal.md"
    Then the script "smoke-run.heart.yaml" has a line with "- run-script: ci:smoke-test"
    And the script "smoke-run.heart.yaml" has a line with "on-link: ci:deployment"
    And the script "smoke-run.heart.yaml" has a line with "book: ${book}"
    And the script "smoke-run.heart.yaml" has no line with "act: library"

  Scenario: If on an outcome kept by name: the branch walked is the planner's, the other kept as written
    A capture walks one branch. `If {name.member}, …` is checked with no model; the branch taken is
    the planner's, kept as the run made it. Its `Otherwise, …` is written so ARTIE can do it as it
    stands, `at {resource}, act REL with FIELD = "value"`: kept as written, so a run that takes it
    needs no model either.
    Given the desk serves a build that links its smoke script and its deployment
    And the file "branch.goal.md" is
      """
      ---
      start: "{build}"
      inputs:
        build:
        desk:
        book: { default: "Matilda" }
      ---

      Smoke test what the build made, then go on by how it went.

      - Run the script it links as ci:smoke-test, on the deployment it links as ci:deployment, with book = {book} `as smoke`
      - If {smoke.passed}, follow the deployment it links
        Otherwise, at {desk}, act desk:release with pull = "9"
      """
    And the planner will move
      | move | words                         | opens |
      | act  | follow the deployment it links | yes   |
      | done |                               |       |
    And ARTIE's reasoner will choose, in order
      | form | follow        | fields |
      |      | ci:deployment |        |
    When the goal "branch.goal.md" runs with ARTIE in the desk, captured in "branch.trace.yaml", given
      | name  | value                            |
      | build | http://localhost:8160/test-build |
      | desk  | http://localhost:8160/           |
    Then the run finished
    When artie generated "branch.heart.yaml" as data from "branch.trace.yaml" and "branch.goal.md"
    Then the script "branch.heart.yaml" has a line with "- given:"
    And the script "branch.heart.yaml" has a line with "at: smoke"
    And the script "branch.heart.yaml" has a line with "follow: ci:deployment"
    And the script "branch.heart.yaml" has a line with "enter: ${desk}"
    And the script "branch.heart.yaml" has a line with "act: desk:release"

  Scenario: A value an outcome kept by name gave is read from that outcome again, as the goal's step named it
    The goal's step names what it sends, `{smoke.of}`: the value the planner sent is read again from
    the outcome kept as smoke, not from another answer that happened to hold the same value.
    Given the desk serves a build that links its smoke script and its deployment
    And the file "cited.goal.md" is
      """
      ---
      start: "{build}"
      inputs:
        build:
        desk:
        book: { default: "Matilda" }
      ---

      Smoke test what the build made, then release as many as it ran.

      - Run the script it links as ci:smoke-test, on the deployment it links as ci:deployment, with book = {book} `as smoke`
      - If {smoke.passed}, at {desk}, release pull request {smoke.of}
      """
    And the planner will move
      | move | words                   | opens |
      | act  | release pull request 1  | yes   |
      | done |                         |       |
    And ARTIE's reasoner will choose, in order
      | form         | follow | fields      |
      | desk:release |        | {"pull": 1} |
    When the goal "cited.goal.md" runs with ARTIE in the desk, captured in "cited.trace.yaml", given
      | name  | value                            |
      | build | http://localhost:8160/test-build |
      | desk  | http://localhost:8160/           |
    Then the run finished
    When artie generated "cited.heart.yaml" as data from "cited.trace.yaml" and "cited.goal.md"
    Then the script "cited.heart.yaml" has a line with "pull: ${smoke.of}"

  Scenario: Once a reply is answered, the step goes on at another party
    `Once {NAME} is answered, at {party}, …` waits on the reply, then does the rest at that party's
    entry, with no link to spell out from the reply to it.
    Given the file "once-at.goal.md" is
      """
      ---
      start: "{desk}"
      inputs:
        desk:
        watcher:
      ---

      Wait until the desk offers to release, then release there.

      - At {watcher}, ask to be told when {desk} offers desk:release `as watch`
      - Once {watch} is answered, at {desk}, release pull request 7
      """
    And the planner will move
      | move | words                                                          | opens |
      | act  | ask to be told when http://localhost:8160/ offers desk:release | yes   |
      | done |                                                                |       |
      | act  | release pull request 7                                         | yes   |
      | done |                                                                |       |
    And ARTIE's reasoner will choose, in order
      | form          | follow | fields                                                           |
      | watcher:watch |        | {"resource": "http://localhost:8160/", "offers": "desk:release"} |
      | desk:release  |        | {"pull": 7}                                                      |
    When the goal "once-at.goal.md" runs with ARTIE in the desk, captured in "once-at.trace.yaml", given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the run finished
    When artie generated "once-at.heart.yaml" as data from "once-at.trace.yaml" and "once-at.goal.md"
    Then the script "once-at.heart.yaml" has a line with "- once-answered:"
    And the script "once-at.heart.yaml" has a line with "- enter: ${desk}"
    And the script "once-at.heart.yaml" has a line with "act: desk:release"

  Scenario: At a resource a step named, the script made from the run enters its address
    `At {NAME}, …` where a step named NAME enters what that step led to: the script reads the
    address it was kept at, `${NAME.@self}`, not its state.
    Given the file "at-named.goal.md" is
      """
      ---
      start: "{desk}"
      inputs:
        desk:
        watcher:
      ---

      Wait until the desk offers to release, then release where the watch was about.

      - At {watcher}, ask to be told when {desk} offers desk:release `as watch`
      - Once {watch} is answered, follow it to the resource it watched `as watched`
      - At {watched}, release pull request 7
      """
    And the planner will move
      | move | words                                                          | opens |
      | act  | ask to be told when http://localhost:8160/ offers desk:release | yes   |
      | done |                                                                |       |
      | act  | follow it to the watch                                         | yes   |
      | act  | follow it to the resource it watched                           | yes   |
      | done |                                                                |       |
      | act  | release pull request 7                                         | yes   |
      | done |                                                                |       |
    And ARTIE's reasoner will choose, in order
      | form          | follow         | fields                                                           |
      | watcher:watch |                | {"resource": "http://localhost:8160/", "offers": "desk:release"} |
      |               | hr:in-reply-to |                                                                  |
      |               | about          |                                                                  |
      | desk:release  |                | {"pull": 7}                                                      |
    When the goal "at-named.goal.md" runs with ARTIE in the desk, captured in "at-named.trace.yaml", given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the run finished
    When artie generated "at-named.heart.yaml" as data from "at-named.trace.yaml" and "at-named.goal.md"
    Then the script "at-named.heart.yaml" has a line with "- enter: ${watched.@self}"

  Scenario: A step that only reads is kept in the script when a later step names what it read
    `as NAME` on a read is no detour when a later step names it: the watch is asked about
    `${front.@self}`, so the script reads front first, though no value of front is sent.
    Given the file "named-read.goal.md" is
      """
      ---
      start: "{desk}"
      inputs:
        desk:
        watcher:
        state:
      ---

      Watch the desk's releases in a state.

      - Search the desk's releases that are {state} `as front`
      - At {watcher}, ask to be told when {front} offers desk:release `as watch`
      """
    And the planner will move
      | move | words                                                          | opens |
      | act  | search the desk's releases that are running                    | yes   |
      | done |                                                                |       |
      | act  | ask to be told when the releases found offer desk:release      | yes   |
      | done |                                                                |       |
    And ARTIE's reasoner will choose, in order
      | form                 | follow | fields                                                                          |
      | desk:search-releases |        | {"name": "running"}                                                             |
      | watcher:watch        |        | {"resource": "http://localhost:8160/releases?name=running", "offers": "desk:release"} |
    When the goal "named-read.goal.md" runs with ARTIE in the desk, captured in "named-read.trace.yaml", given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
      | state   | running                |
    Then the run finished
    When artie generated "named-read.heart.yaml" as data from "named-read.trace.yaml" and "named-read.goal.md"
    Then the script "named-read.heart.yaml" has a line with "- act: desk:search-releases"
    And the script "named-read.heart.yaml" has a line with "- keep-as: front"

  Scenario: The goal runs once with the planner, and the script made from it runs with none
    Given the planner will move
      | move | words                                                          | opens |
      | act  | ask to be told when http://localhost:8160/ offers desk:release | yes   |
      | done |                                                                |       |
      | act  | follow it to the watch                                         | yes   |
      | act  | follow it to the resource it watched                           | yes   |
      | done |                                                                |       |
    And ARTIE's reasoner will choose, in order
      | form          | follow         | fields                                                           |
      | watcher:watch |                | {"resource": "http://localhost:8160/", "offers": "desk:release"} |
      |               | hr:in-reply-to |                                                                  |
      |               | about          |                                                                  |
    When the goal "watch.goal.md" runs with ARTIE in the desk, captured in "watch.trace.yaml", given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the run finished
    And the watch was asked with the desk's reply, signed by the desk
    And the reasoner was shown "watcher:watch" without "_links", the run's to fill
    When artie generated "watch.heart.yaml" as data from "watch.trace.yaml" and "watch.goal.md"
    Then the script "watch.heart.yaml" has a line with "- enter: ${watcher}"
    And the script "watch.heart.yaml" has a line with "- ask: watcher:watch"
    And the script "watch.heart.yaml" has a line with "reply: watch"
    And the script "watch.heart.yaml" has a line with "- once-answered:"
    When "watch.heart.yaml" runs with ARTIE in the desk, given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the replay finished, having followed "about" to "http://localhost:8160/", with no model
