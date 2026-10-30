Feature: The model key ARTIE reasons with
  spec/PLAN-ARTIE-SCRIPTS.md, decisions: a model key, for act(intent = …) and the
  live reasoner run, comes from ANTHROPIC_API_KEY in the environment first,
  else from the credentials file (ARTIE_HOME's, or ~/.artie's), in the
  section for https://api.anthropic.com. ARTIE never stores it and never
  says it: what it says of a key names where it came from, not its value.

  Scenario Outline: The environment first, then the credentials file
    Given the environment's ANTHROPIC_API_KEY is "<environment>"
    And the credentials file's key for "https://api.anthropic.com" is "<file>"
    When ARTIE looks for its model key
    Then it finds "<found>", from <where>

    Examples:
      | environment | file     | found    | where                |
      | env-key     | file-key | env-key  | the environment      |
      |             | file-key | file-key | the credentials file |
      | env-key     |          | env-key  | the environment      |
      |             |          |          | nowhere              |

  Scenario Outline: The key is the model's address's: Anthropic's from the environment or the file, another's from the file alone
    spec/PLAN-LOCAL-MODEL.md, phase 1: the Messages API is the seam, so a run
    may name where its model answers. ANTHROPIC_API_KEY is Anthropic's key, so it
    stands only for Anthropic's address; any other address takes the credentials
    file's entry for it, and with none, no key, which a local model doesn't need.
    Given the environment's ANTHROPIC_API_KEY is "<environment>"
    And the credentials file's key for "<origin>" is "<file>"
    When ARTIE looks for its model key for "<origin>"
    Then it finds "<found>", from <where>

    Examples:
      | origin                    | environment | file     | found    | where                |
      | https://api.anthropic.com | env-key     | file-key | env-key  | the environment      |
      | http://localhost:11434    | env-key     | file-key | file-key | the credentials file |
      | http://localhost:11434    | env-key     |          |          | nowhere              |

  Scenario: A model at another address needs no key: allowed the claude reasoner there, ARTIE plans and reasons
    Given the environment's ANTHROPIC_API_KEY is ""
    When a script host's ARTIE, allowed the claude reasoner at "http://localhost:11434", is made
    Then it has a planner

  Scenario: What ARTIE says of a key names its source, never its value
    Given the environment's ANTHROPIC_API_KEY is "sk-secret-value"
    When ARTIE looks for its model key
    Then what it says of the key is "a model key, from the environment"

  Scenario: Allowed the claude reasoner with no model key, ARTIE refuses to reason, and says where a key goes
    Given the environment's ANTHROPIC_API_KEY is ""
    When a script host's ARTIE, allowed the claude reasoner, is asked to reason
    Then the reason step was refused as "no reasoner", by "the client": "no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials"
