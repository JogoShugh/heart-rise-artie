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

  Scenario: Every content-addressed resource reachable from a bed is pinned
    When a client walks bed 2 through every state at L0, L1 and L2, following every content-addressed link
    Then the addresses it reached are exactly:
      | address                                  |
      | /affordance-sets/sha256-00bb074be861484e |
      | /affordance-sets/sha256-049531dec816f02f |
      | /affordance-sets/sha256-4113cd1b7383f843 |
      | /affordance-sets/sha256-783c24a647c96663 |
      | /affordance-sets/sha256-7c9b4beac495ed3f |
      | /affordance-sets/sha256-c6875cb10cbcdbf1 |
      | /schemas/sha256-5b67c4473aad58b8         |
      | /schemas/sha256-7ab6ce2f03278bdf         |
      | /schemas/sha256-d78168a708f5ba63         |
      | /unavailable/sha256-0b7168a23ddb1595     |
      | /unavailable/sha256-1648f3dc5307f159     |
      | /unavailable/sha256-d1995ab17483fab3     |
    And each one answered 200 with bytes that hash to its address
