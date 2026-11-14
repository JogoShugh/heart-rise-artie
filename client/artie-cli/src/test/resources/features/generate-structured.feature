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
