Feature: Content addresses stay the same while HEART moves into a library
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, step A5.3. Every Layer 2 and
  Layer 3 resource a bed can link to, in every state and at every rung, is
  pinned here by its address. Moving the code that builds them must leave
  their bytes, and so their addresses, exactly as they were. A change that
  means to alter them updates this list in the same commit, where it is seen.

  Changed once, at step A5.4, when schemas began to be generated from the
  act's input class: the sow-seed schema had said depthCm was any number
  (the server cut 2.5 to 2) and left out seedType's minLength 1 (which the
  server enforced). That changed the sow-seed schema and the two sets of an
  empty bed, which carry it.

  Changed again on 2026-10-11, when @NotBlank began to give a pattern as
  well as minLength 1: seedType had let "   " through, so a bed could be sown
  with a crop of three spaces. The same three changed: the sow-seed schema
  and the two sets of an empty bed.

  Changed again on 2026-10-11, when every generated schema began to name its
  JSON Schema version with "$schema" (HAL-SCHEMA-FORMS-HR Rule 5). All three
  schemas changed, and so did every affordance set, since each one carries a
  schema or its integrity value. The hr:unavailable documents did not change.

  Scenario: Every content-addressed resource reachable from a bed is pinned
    When a client walks bed 2 through every state at L0, L1 and L2, following every content-addressed link
    Then the addresses it reached are exactly:
      | address                                  |
      | /affordance-sets/sha256-00cf08ff2405ee78 |
      | /affordance-sets/sha256-7e3ca9cea617f22a |
      | /affordance-sets/sha256-b8147ba339e26934 |
      | /affordance-sets/sha256-cb7c51c766c9c5b9 |
      | /affordance-sets/sha256-d12335cf83c54f13 |
      | /affordance-sets/sha256-fb5cf62de6dbc0e2 |
      | /schemas/sha256-6efcb63a723f14bc         |
      | /schemas/sha256-7f232c04c52671dd         |
      | /schemas/sha256-8547ad80c90a3125         |
      | /unavailable/sha256-0b7168a23ddb1595     |
      | /unavailable/sha256-1648f3dc5307f159     |
      | /unavailable/sha256-d1995ab17483fab3     |
    And each one answered 200 with bytes that hash to its address
