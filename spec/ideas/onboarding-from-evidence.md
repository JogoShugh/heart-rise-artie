# Idea: onboarding from evidence, for developers raised on data APIs

**Status:** idea only. Raised by the author on 2026-10-26, after the onboarding map
(https://claude.ai/artifact/C3MpLm9soTddCVbSYvbfzb) felt like too much. The research below is summarized from memory:
check each source before quoting it anywhere.

## The worry

People raised on data APIs, Swagger documents and big bags of tools read HEART as "JSON with extra links". The body of
work is large, and even its author can't keep up with it. A two-week reading plan asks too much of first contact.

## What the evidence says

- **Cognitive load theory (Sweller).** Working memory is small; novices drown in a whole system. They learn faster from
  worked examples than from problems or specs, and then from faded examples: finish this one, then write your own.
- **Threshold concepts (Meyer and Land).** Some ideas are troublesome and transformative: confusing before they click,
  obvious after. Learners get stuck between and fake it by copying. HEART's threshold concept is "the answer tells you
  what you can do now; absence is the rule". Everything else is detail.
- **Conceptual change (Posner and colleagues).** People drop a working mental model only when dissatisfied with it, and
  when the new one is intelligible, plausible and fruitful. Swagger developers aren't dissatisfied yet, and their prior
  knowledge interferes.
- **Productive failure (Kapur).** Let people attempt the problem their own way first, struggle a little, then show the
  solution. They learn more deeply than people taught first.
- **Concreteness fading (Fyfe and colleagues).** Concrete, then a sketch, then abstract: curl first, the spec last,
  history optional.
- **Retrieval and spacing (Roediger and Karpicke; Cepeda).** Short sessions spread out, each starting by recalling the
  last, beat long reading.
- **Diffusion of innovations (Rogers).** Adoption depends on relative advantage, compatibility, complexity,
  trialability and observability. HEART is strong on advantage and observability, weak on complexity and compatibility;
  trialability is the lever.
- **Crossing the chasm (Moore).** Win early adopters, people who already feel the pain, before skeptics.
- **Newcomer barriers in open source (Steinmacher and colleagues).** Orientation, finding a first task, missing mentors,
  and documentation that is too broad. The fixes: a curated first issue, a named mentor, a short path.

## What it means for HEART

1. **Start with the pain, not with Fielding.** Write a client the usual way to merge a dependency bump; hit the draft
   GitHub calls mergeable. Then show the HEART answer for the same pull request: no merge form, and a reason.
2. **Teach one threshold concept, and name the misconceptions aloud:** "REST means JSON over HTTP", "links are
   documentation", "the client should know the endpoints", "HATEOAS is academic".
3. **Worked example, then faded:** the merge watch script; change one line; write a three-line goal.
4. **Trialability in ten minutes:** the newcomer's own repository through the gateway, from their laptop.
5. **Cut the scope:** first contact is three sessions (the pain, the answer, change one line). The rest of the map is
   for those who come back wanting more, which is the sign they crossed the threshold.
6. **Recruit the frustrated, not the skeptical:** people burned by API drift, hallucinated calls or brittle
   integrations.
7. **One buddy per newcomer,** pairing once with Claude in the room.
8. **Measure understanding, not reading:** show a state and ask which forms will appear and why.

The same applies to the author: freeze new ideas and review one worked example at a time, starting with the merge
watch. The five-ways experiment (spec/ideas/five-ways-to-run-one-goal.md) doubles as the productive-failure lesson:
arms 1 and 2 are what newcomers would build themselves.

## Open

- A tested ten-minute setup for "your own repository through the gateway".
- A short list of good first issues.
- The predict-the-forms quiz: a handful of states, with the answers.
