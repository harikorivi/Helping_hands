# Task-removal recovery validation — 2.0-rc2

12 November 2026. Package `ai.interlink.scheduler`; version code 17. Personal sideloaded candidate; physical vivo acceptance pending.

## Executed

- SDK 35 / JDK 17 / Gradle 8.11.1 offline build, APK assembly, Android unit tests, instrumentation assembly and lint passed. Lint: 0 errors, 22 warnings. The additional `BatteryLife` warning flags the direct battery-exemption request and its public-store policy implications; it is not suppressed. Existing intentional wake-lock/durable-write and other warnings remain.
- 14 JVM tests passed; 38 mocked Node tests passed. No credentials were read and no real login or claim was submitted.
- API 35 emulator routine suite: `OK (26 tests)` — 25 passed, one opt-in screen-off UI test skipped. The separate opt-in UI test passed in 13.43 seconds, exercising actual launcher Recents removal and timer execution while the screen remained off.
- A framework-created bound-only service exercised the key lifecycle gap: enabled scheduling plus `onTaskRemoved` without a preceding `onStartCommand`. It requested and completed foreground promotion, acquired its CPU wake lock, and continued after the test unbound. Two immediate callbacks produced one recovery request. No Termux dispatch occurred.
- The same bound-only case with scheduling paused did not restart, acquire a lock or dispatch Termux. A healthy started instance retained its identity without a foreground restart request.
- Native request permission verified in the installed manifest. The actual app button was clicked with Android's request intent intercepted by instrumentation: it requested the native package-scoped dialog and a canceled request did not become an exemption grant. This test does not claim the user accepted the real Settings dialog or that vivo will honor its outcome.
- Existing regression checks passed: original deadlines, active/expired leases, changed boot identity, six local slots, migration, duplicate/stale callbacks, actual Android one-shot callback routing, independent Node results, safe missing-report handling, private Downloads export and configurable debug logging.
- APK signature verified; signer matches the existing release. Version 17 installed over the emulator's prior app. Git whitespace check passed.

## Scope and limits

No physical vivo/iQOO test, overnight campaign, manufacturer-force-stop reproduction, real Termux/API claim sequence or battery measurement was performed on the Mac. Bound-only initialization tests the callback recovery branch; it does not reproduce the proprietary implementation that killed the old process. A background-start rejection is logged, not bypassed with an alarm loop. A true package force-stop remains outside guaranteed ordinary-app recovery.

The initial battery-test run was stopped after its activity monitor accidentally blocked the test's own activity launch. The harness was corrected to match the request action and package URI; the completed suite above is the subsequent passing run.

Evidence: `/tmp/interlink-rc2-build.log`, `/tmp/interlink-rc2-testbuild.log`, `/tmp/interlink-rc2-tests.log`, `/tmp/interlink-rc2-recents.log`, `/tmp/interlink-rc2-node.log` and generated Gradle reports. Temporary logs and the user's raw diagnostic export are not included in this release.

[Evidence review](../../docs/TASK-REMOVAL-REVIEW-2026-11-12.md) · [Installation](../../README-mobile.md).

APK SHA-256: `ade1edbd359b34c628ead888bca396cdbff1211c04ba479f4590218987a6f463`.
