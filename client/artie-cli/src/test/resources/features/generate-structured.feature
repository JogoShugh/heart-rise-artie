Feature: artie generate writes a list or an object sent as one, each part named where it came from
  spec/PLAN-SCRIPT-GENERATOR.md. A form's field may take an array or an
  object: labels, assignees. The capture keeps what was sent as JSON text,
  with the field's type. The script made from it sends a list or a map, not
  that text, each part an input or a variable where the run's value was one,
  else the constant it was. Sent as text, the server refuses it: live, the
  labels "["bug"]" were a string where GitHub expects an array.

  Background:
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs:
          label:
            value: bug
            given: default
            default: bug
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      ---
      mode: intent
      say: label it bug, and assign ada and bo
      line: 2
      within: 1
      params:
        relation: label
        labels: '["bug","triage"]'
        assignees: '["ada","bo"]'
        meta: '{"priority":2,"area":"bug"}'
      values:
        labels:
          value: '["bug","triage"]'
          type: array
          from:
            - parts:
                bug:
                  input: label
        assignees:
          value: '["ada","bo"]'
          type: array
          from:
            - chosen
        meta:
          value: '{"priority":2,"area":"bug"}'
          type: object
          from:
            - parts:
                bug:
                  input: label
      reason:
        template: label it $label, and assign ada and bo
        slots:
          label:
            value: bug
            from:
              - input: label
      outcome: done
      """

  Scenario: An object the goal cites by its members is kept as that object, though its form gave no type
    A HAL-FORMS property says only an HTML input type, so the capture keeps no type for an object sent, and a
    digit in its JSON text may match some earlier value by chance. The goal's own words, "citing {smoke.passing}
    of {smoke.of}", say where its members came from: those win, and the object is the reasoner's, for review.
    Given the file "cited.trace.yaml" is
      """
      ---
      run:
        inputs: {}
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
          changed_files: 1
      ---
      mode: intent
      say: promote it, citing 1 of 1 scenarios passed
      line: 2
      params:
        relation: env:promote
        smoke: '{"passed":1,"of":1}'
      values:
        smoke:
          value: '{"passed":1,"of":1}'
          from:
            - parts:
                '1':
                  step: 1
                  pointer: /changed_files
          cited:
            /passed:
              kept: smoke
              pointer: /passing
            /of:
              kept: smoke
              pointer: /of
      outcome: done
      """
    When artie is run with
      | argument         |
      | generate         |
      | cited.trace.yaml |
      | --out            |
      | cited.heart.yaml |
      | --format         |
      | yaml             |
    Then artie exits 0
    And the file "cited.heart.yaml" has these lines
      | line                                                                                        |
      | smoke: # review: the reasoner chose it, asked "promote it, citing 1 of 1 scenarios passed" |
      | passed: ${smoke.passing}                                                                    |
      | of: ${smoke.of}                                                                             |

  Scenario: An array's or an object's parts are written one by one, an input where one was
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.kts        |
    Then artie exits 0
    And the file "gen.kts" has these lines
      | line                                                         |
      | "labels" to listOf(label, "triage")                          |
      | "assignees" to listOf("ada", "bo") // review: the reasoner chose it, asked "label it bug, and assign ada and bo" |
      | "meta" to mapOf("priority" to 2, "area" to label)            |
