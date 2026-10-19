Feature: artie run's config: how strictly a run holds to its lock file, set by a person beside the script
  spec/ideas/replay-guards-lenient-strict.md, "Script, lock file and config". Beside
  NAME.kts and its lock file NAME.pins.yaml, a person may keep NAME.config.yaml: how
  strict to be. The tool never writes it. --config names another one, for a trial
  run that leaves the default alone. With none, a run behaves as it did before
  there was a config. A flag wins over the config. Its settings: guard (lenient
  or strict) and repin (auto, propose or never; --no-repin wins), which
  script-replay.feature shows at work.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
    And these forms
      | at | relation   | leads to   | outcome |
      | /  | issues-get | /issues/10 | done    |
    And the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    And the file "issue.pins.yaml" is
      """
      rung: L2
      forms:
        issues-get:
          integrity: sha256-abc
          sets: []
      links: {}
      """

  Scenario Outline: The guard comes from the flag, then the config, then the default, lenient
    Given the file "<file>" is
      """
      <setting>
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
      | <option>  |
      | <value>   |
    Then artie exits 0
    And the client was made holding the lock file pinning "issues-get" to "sha256-abc", "<guard>"

    Examples:
      | file              | setting       | option   | value             | guard   |
      | issue.config.yaml | guard: strict |          |                   | strict  |
      | issue.config.yaml | guard: strict | --guard  | lenient           | lenient |
      | other.config.yaml | guard: strict | --config | other.config.yaml | strict  |
      | other.config.yaml | guard: strict |          |                   | lenient |

  Scenario Outline: A config that can't be held to stops the run before its first request
    Given the file "<file>" is
      """
      <setting>
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
      | <option>  |
      | <value>   |
    Then artie exits 2
    And artie's last line is: stopped before the first request: <why>

    Examples:
      | file              | setting       | option   | value        | why                                             |
      | issue.config.yaml | guard: loose  |          |              | issue.config.yaml: guard is lenient or strict   |
      | issue.config.yaml | budget: 5     |          |              | issue.config.yaml: no setting budget            |
      | issue.config.yaml | repin: often  |          |              | issue.config.yaml: repin is auto or propose or never |
      | issue.config.yaml | guard: strict | --config | missing.yaml | --config, but no config at missing.yaml         |
