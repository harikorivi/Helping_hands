# Service task-removal and startup diagnosis validation — 1.8-rc2

Built 2026-11-06. Android version code 13; bot version 1.8.0-rc.2, scheduled wrapper protocol 2. APK and Termux source must both be updated.

## Executed

- Node tests: 38 passed. New fixtures exercise missing project, outdated wrapper, missing dependencies, exit 2 without a report, console redaction, valid report forwarding and a check-only path that never starts the scheduled wrapper. No real credentials or claims.
- Android JVM: 9 passed. Routine API 35 emulator instrumentation: 19 passed, 5 opt-in skipped (runner summary `OK (24 tests)`). Includes allowlisted startup reasons and exit-number handling.
- Separate actual Recents test on the final APK: 1 passed. Launched the app, started continuous mode, swiped its real task card from Recents, required `MONITOR_TASK_REMOVED`, turned the screen off, observed the continuous timer fire while non-interactive, and confirmed the service remained active. No claim service was started. Service stopped and screen restored afterward.
- This AVD has no device credential; the final Recents test proves screen-off behavior, not credential-locked Funtouch behavior. Early UI automation attempts failed before proving task removal; those are not counted as passes. A stricter device-locked assertion was corrected because the AVD lacks a credential.
- Build/assemble/lint passed: 0 errors, 43 warnings. APK update installation, v2 signature verification and Git whitespace checks passed.

## Implementation and limits

`stopWithTask=false` is explicit but was already Android's default. The callback records actual removal and retains the started service; `START_STICKY` was already in use. Close screen, keep scheduling offers an app-controlled way to remove the UI task. No claim that these override a manufacturer's force-stop/termination policy.

The Android-provided Termux bootstrap performs offline checks and only forwards safe correlated reports. Protocol mismatch becomes `bot_update_required`. A wrapper exit 2 without a report becomes `wrapper_exit_without_report`; exit 2 with a valid Node report retains the independent claim-window reasons. Raw stdout/stderr and credentials never enter the companion report. The previous phone run's exact exit-2 cause cannot be reconstructed without its private historical log.

NOT RUN: physical Funtouch Recents removal, overnight service survival, live two-window claims and battery impact. These remain physical acceptance requirements. No force-stop recovery guarantee.

Logs: `/tmp/interlink-survival-node.log`, `/tmp/interlink-survival-final-build.log`, `/tmp/interlink-survival-package-build.log`, `/tmp/interlink-survival-routine.log`, `/tmp/interlink-survival-recents-final.log`.

SHA-256: `7d97c736db9511ec7d9ada5749d0c3418ce5f02e68ce2b464e13460503b9d633`.
