# Plan step 2: the run log behind the commit

The commit "The HEART script DSL, its test add-on, and artie run" was made with git's
commit hook skipped, by the author's decision. The TDD-ordering check refused it because
heart-script, heart-script-test and artie-cli had no BDD-run logging until just before the
commit: the author chose to build them first and add the shared logging block before
committing. Their red and green runs are recorded here instead, as Claude kept them while
working, with the mutant checks and the rule breaches it disclosed. ARTIE's own runs
(module=client) were logged by its task throughout, in the gate's own log.

Times are local (EDT), 2026-10-17.

| Time (EDT) | Module | Run | Result |
|---|---|---|---|
| 16:59:03 | heart-script | red, before any src/main | 10 scenarios, 10 failed (undefined steps) |
| 17:01:38 | heart-script | green, after Navigator.kt, Run.kt, Heart.kt | 10 passed |
| 17:02:0x | heart-script | mutant check: block doesn't return | 1 failed (the visit-and-return scenario) — caught |
| 17:02:14 | client (all) | check after restoring | heart-script 10 passed; artie 258 passed (41 live skipped); exit 0 |
| 17:03:10 | artie (logged by its own task) | red, script-github.feature before ArtieNavigator | 262 run, 2 failed (undefined steps) |
| 17:04:58 | artie | green, after ArtieNavigator, broughtIntoBeing | 0 failed |
| 17:05:24 | client (all) | check | heart-script 10 passed; artie 260 passed (41 live skipped); exit 0 |

- 17:07:24 heart-script red: flow.feature, 13 run, 3 failed (until, members undefined)
- 17:08:08 heart-script green: 13 passed
- 17:08:39 heart-script red: trace.feature, 15 run, 2 failed (steps undefined, behaviour absent)
- 17:12:22 heart-script green: 15 passed; detekt clean (TraceStep split into Began/Asked, Run.record)
- 17:12:40 mutant: outcomeOf(NewResource) -> "done"; 15 run, 1 failed (killed); reverted
- 17:13:09 heart-script check 15/15, artie test 260 (41 skipped) + 2 green
- 17:14 heart-script API opened for add-ons (no behaviour change): ScriptStopped open, Run.step and Here.held public read, TraceStep.expect; steps moved to heart-script testFixtures; Features marker class (Gradle skipped the classless test set)
- 17:15:50 heart-script-test red: expect.feature, 11 run, 11 failed (Unresolved reference: expect)
- 17:16:52 green: heart-script 15, heart-script-test 11, artie 262 (41 skipped); detekt clean
- 17:17 mutant: Unchanged ignores address/state; 11 run, 0 failed (SURVIVED, a gap)
- 17:17:31 added scenario "Done is not unchanged..."; with the mutant, 12 run, 1 failed (killed); reverted
- 17:18:08 added scenario "A refusal after the expected step still stops the script"; mutant (later stop swallowed): 13 run, 1 failed (killed); reverted
- 17:18:26 heart-script-test green, 13; detekt clean
- 17:20:10 artie red: script-github "finds ARTIE", 263 run, 1 failed (no HEART client on the classpath provides heart.script.NavigatorProvider)
- 17:20:38 artie green: ArtieNavigatorProvider + META-INF/services; check 263 (41 skipped), detekt clean. Mutant = the red run itself (registration absent).
- BREACH: that scenario was appended to script-github.feature with a shell heredoc, not Edit.
- 17:21-17:22 heart-script: Run.entry, Run.onStep, ScriptStopped.refused; ScriptedService refusals carry a side; ScriptSteps.service public
- 17:23:22 artie-cli red: run.feature, 18 run, 18 failed (a stub ArtieCli.run = TODO, written in src/main before the red so the steps compiled)
- 17:25:12 artie-cli green: 18; detekt clean
- 17:25:27 mutants (BREACH: applied with perl on src/main, the file restored from a copy): unreachable exits 3 (killed), inputs file wins (killed), --quiet prints steps (killed)
- 17:25:46 artie-cli check green again, 18
- 17:33:41 LIVE: installed artie (installDist, Java 25) ran scripts/tests/github-comment.kts through the live gateway on 8392 against JogoShugh/heart-github-test #10: 8 steps, exit 0; GitHub shows comment 6241457535 edited; trace token scan 0
- 18:05 artie-cli build: slf4j-nop at runtime; the launcher checks for Java 22 or newer (exit 2 with a message) and quiets sun.misc.Unsafe warnings on 24+; client/scripts/runs/ ignored. Checked by hand: Java 21 says so, exit 2; Java 25 prints no warnings.
- 18:10:08 artie red: script-github "server ARTIE can't reach", 264 run, 1 failed (ConnectException escaped as a script error)
- 18:11:03 artie green 262 (41 skipped) + 2, artie-cli 18; ArtieNavigator maps IOException to Unreachable (client), IntegrityException to IntegrityMismatch. Mutant = the red run (catch absent). Gateway down by hand: exit 4.
- 18:51:24 heart-script red (a client-refused act lost its values), 15 run 1 failed; 18:51:52 green 15/13/18 (actAsked shared). Mutant = the red run.
- 18:52:37 artie-cli red (--report not yet), 19 run 1 failed; 18:53:45 green 19. Installed: garden-bed --report wrote the run report with the script.
- 18:55:15 artie red (model key; a stub returning null written first), 269 run 4 failed; 18:55:44 green. 18:56:19 mutant file-before-environment: 1 failed (killed); restored.
- 18:57:07 all green: artie, heart-script, heart-script-test, artie-cli
- 20:04:23 artie red (a script host's ARTIE over coaps: no binding for the scheme coaps), 270 run 1 failed; the provider refactored to standard(env, home) first (no behaviour change). 20:04:51 green 270 (41 skipped). Live: garden-bed.kts entry=coaps://localhost:5784/, 8 steps, exit 0.
- 20:56:43 artie-cli red (a follow to a missing link exited 1), 20 run 1 failed; 20:57:13 green 15/13/20 (a client refusal, NotOffered, traced via followAsked).
- 20:57:37 artie-cli red (--inputs-of without the inline count), 20 run 1 failed; 20:58:19 green 20.
- 20:59:37 artie-cli red (--rung not yet), 23 run 2 failed (the CLI's signature passing a rung changed first); 21:00:35 green 15/13/268/23. Live: --rung L1 and L2 over http, L2 over coap, checked through a proxy logging Prefer and Preference-Applied.
- 21:03:11 artie red (script input: a bad value was sent; a server 422 was labelled the client's), 272 run 2 failed; 21:03:55 green 272 (41 skipped) + cli 23. ArtieNavigator checks the form's schema first; InvalidPayload is the server's.
- 21:05:10 artie red (an entry with no binding escaped as a script error, exit 5), 273 run 1 failed; 21:05:55 green, the CLI exits 2.
- 21:15:23 heart-script red (reason: a stub stopping "not built"; the contract types, Asked.mode and Run.reasoning added first; BREACH: those src/main edits made with python), 17 run 2 failed; 21:16:33 green 17 + 13.
- 21:18:50 artie-cli red (--reasoner not yet), 26 run 3 failed; 21:19:46 green 26. 21:20:45 artie red (reason refused, no reasoner), 276 run 3 failed; 21:22:55 green. BREACH: a model-key.feature scenario appended with a heredoc.
- 21:24 live: garden-reason.kts --reasoner claude over coap, exit 0; Claude chose garden:sow-seed {seedType: basil, depthCm: 2}; the key put in that one command's environment, never printed; trace token scan 0.
