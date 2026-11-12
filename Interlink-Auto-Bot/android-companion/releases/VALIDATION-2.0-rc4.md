# rc4 pending-alarm restoration regression validation

12 November 2026. Package `ai.interlink.scheduler`, version code 19; API 26 minimum / 35 target. Physical overnight/48-hour acceptance remains pending.

Executed:

- Offline JDK 17 / SDK 35 / Gradle 8.11.1 build, APK/test assembly, Android unit tests and lint passed: 0 errors, 31 warnings. Existing policy, preference-write and other warnings remain unsuppressed.
- 14 JVM tests passed. Node sources unchanged; the 38 mocked Node tests were rerun without real login/claims.
- API 35 routine instrumentation: `OK (29 tests)` — 27 passed, 2 opt-in scenarios skipped. Added regressions prove that app-open/manual repair two seconds after due preserve the pending original generation and dispatch nothing until its real receiver path is invoked; the receiver then retains the original timestamp. Unarmed overdue slots are not preserved/replayed. A subsequent real alarm repairs an independently expired slot without replaying it.
- Actual launcher Recents removal plus screen-off/forced-idle alarm probe passed separately: `OK (1 test)` in 16.477 seconds. The bounded service completed and released both execution and handoff locks without Termux dispatch. This AOSP emulator has no secure PIN or vivo power framework.
- Existing alarm, deadline, callback, permission/setup, source correlation, retention and export regression checks passed. The rc3 dedicated denied-permission/non-forced UI results remain baseline evidence; those separate opt-in variants were not rerun for this maintenance change.
- APK version/signature verified and update installation passed on the emulator. Source/whitespace review passed. No user credentials, real API requests or raw user diagnostic file were included.

The original two-second pending-alarm case was reproduced using persisted slot/generation fixtures and an intercepted Termux boundary; this is not a new physical iQOO claim test. Future alarms after a genuine OEM force-stop remain unproven. The successful physical rc3 probe validates the alarm/FGS path only, not the full scheduled Node/two-window workflow.

Evidence: `/tmp/interlink-rc4-build.log`, `/tmp/interlink-rc4-tests.log`, `/tmp/interlink-rc4-doze.log`, `/tmp/interlink-rc4-node.log` and Gradle reports. [Field review](../../docs/RC3-FIELD-REVIEW-2026-11-12.md) · [Acceptance checklist](../../docs/RC3-ACCEPTANCE.md).

APK SHA-256: `a3557bb5d0584b90df345193afb05560440bc9cce872b46ccd6c4aab5ec24829`.
