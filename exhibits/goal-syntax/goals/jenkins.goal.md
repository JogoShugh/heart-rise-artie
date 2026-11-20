---
start: https://localhost:8393/
inputs:
  job:     { question: "Which job?" }
  branch:  { default: main, question: "Which branch?" }
  release: { question: "Which release?" }
manners: alongside-others
---

Build {release} from {branch}, and take it to staging once it passes and someone may approve it. Keep watching
{branch} until the release is tagged.

## Working alongside others

Other people work in this Jenkins while you do. What they decide stands.

- Don't undo or write over anything someone else changed since you looked. Look again, and work from what's there now.
- When a step no longer makes sense because of what someone did, leave it, and say why in the run's notes.
- Nothing you write may claim something that isn't so: "deployed" only once the deploy stage passed.

## The work

- On job {job}
  - Build it with branch {branch} and release {release} `as build`
    - Add to its description that it builds {release}
    - Once it finishes, check it passed.
      Otherwise, say which stage failed, and leave the rest.
    - Once it asks for approval to deploy to staging, approve it as {release}.
      Otherwise, say who decided instead.
      Or give up after 30 minutes.
  - Whenever a build of {branch} fails `as broken`
    - Add to its description that it blocks {release}
- Until {release} is tagged

## Done when

- {build} passed and its deploy stage ran; or someone declined it, and the notes say who
- Nothing anyone else decided was undone: no aborted build restarted, no declined approval approved
